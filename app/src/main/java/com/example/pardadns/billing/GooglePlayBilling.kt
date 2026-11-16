package com.example.pardadns.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** [PlayBilling] backed by the Play Billing Library (one-time INAPP product). */
class GooglePlayBilling(context: Context) : PlayBilling {

    override val name = "play"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    override val purchaseEvents: Flow<PurchaseEvent> = events

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, _ -> events.tryEmit(result.toEvent()) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var connecting: CompletableDeferred<Boolean>? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    private val details = mutableMapOf<String, ProductDetails>()

    /** Connects if needed; true once the client is ready. */
    private suspend fun ready(): Boolean {
        if (client.isReady) return true
        val pending = connecting ?: CompletableDeferred<Boolean>().also { d ->
            connecting = d
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingResponseCode.OK
                    if (ok) retryDelayMs = INITIAL_RETRY_MS
                    else Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    connecting = null
                    d.complete(ok)
                }

                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "Billing service disconnected — reconnecting in ${retryDelayMs}ms")
                    connecting = null
                    d.complete(false)
                    scheduleReconnect()
                }
            })
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) { pending.await() } ?: false
    }

    private fun scheduleReconnect() {
        val wait = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
        scope.launch {
            delay(wait)
            ready()
        }
    }

    private suspend fun loadDetails(productId: String): ProductDetails? {
        details[productId]?.let { return it }
        if (!ready()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "Product details failed: ${result.billingResult.debugMessage}")
            return null
        }
        return result.productDetailsList?.firstOrNull { it.productId == productId }
            ?.also { details[productId] = it }
    }

    override suspend fun productPrice(productId: String): String? =
        loadDetails(productId)?.oneTimePurchaseOfferDetails?.formattedPrice

    override suspend fun queryPurchase(productId: String): PurchaseState? {
        if (!ready()) return null
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "Purchase query failed: ${result.billingResult.debugMessage}")
            return null
        }
        val purchases = result.purchasesList.filter { productId in it.products }
        // Prefer a completed purchase over a pending one.
        val purchased = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        return when {
            purchased != null -> PurchaseState(
                if (purchased.isAcknowledged) Ownership.OWNED else Ownership.OWNED_UNACKED,
                purchased.purchaseToken,
            )
            purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                PurchaseState(Ownership.PENDING, purchases.first().purchaseToken)
            else -> PurchaseState(Ownership.NONE)
        }
    }

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        if (!ready()) return false
        val result = client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build(),
        )
        return result.responseCode == BillingResponseCode.OK
    }

    override suspend fun launchPurchase(activity: Activity, productId: String): String? {
        val product = loadDetails(productId) ?: return "Google Play is unavailable. Try again later."
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .apply { product.oneTimePurchaseOfferDetails?.offerToken?.let { setOfferToken(it) } }
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
        )
        return when (result.responseCode) {
            BillingResponseCode.OK -> null
            BillingResponseCode.ITEM_ALREADY_OWNED -> { events.tryEmit(PurchaseEvent.Updated); null }
            else -> "Couldn't start the purchase (${result.debugMessage.ifBlank { "code ${result.responseCode}" }})"
        }
    }

    override fun release() = client.endConnection()

    private fun BillingResult.toEvent(): PurchaseEvent = when (responseCode) {
        BillingResponseCode.OK, BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseEvent.Updated
        BillingResponseCode.USER_CANCELED -> PurchaseEvent.Cancelled
        else -> PurchaseEvent.Failed(debugMessage.ifBlank { "Purchase failed (code $responseCode)" })
    }

    private companion object {
        const val TAG = "PardaDNS/Billing"
        const val INITIAL_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 60_000L
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
