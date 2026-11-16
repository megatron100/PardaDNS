package com.example.pardadns.debughooks

import android.app.Activity
import com.example.pardadns.billing.Ownership
import com.example.pardadns.billing.PlayBilling
import com.example.pardadns.billing.PurchaseEvent
import com.example.pardadns.billing.PurchaseState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Scripted stand-in for Google Play, installed by the `BILLING` debug hook (debug builds only).
 * [nextResult] decides what the next "Upgrade to Pro" tap does.
 */
class FakePlayBilling : PlayBilling {

    enum class Result { SUCCESS, CANCEL, PENDING }

    override val name = "fake"

    @Volatile var nextResult = Result.SUCCESS
    @Volatile var ownership = Ownership.NONE

    private val events = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    override val purchaseEvents: Flow<PurchaseEvent> = events

    override suspend fun productPrice(productId: String) = PRICE

    override suspend fun queryPurchase(productId: String) =
        PurchaseState(ownership, purchaseToken = if (ownership == Ownership.NONE) null else TOKEN)

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        if (ownership == Ownership.OWNED_UNACKED) ownership = Ownership.OWNED
        return true
    }

    override suspend fun launchPurchase(activity: Activity, productId: String): String? {
        when (nextResult) {
            // Like Play: a new purchase arrives unacknowledged.
            Result.SUCCESS -> { ownership = Ownership.OWNED_UNACKED; events.tryEmit(PurchaseEvent.Updated) }
            Result.PENDING -> { ownership = Ownership.PENDING; events.tryEmit(PurchaseEvent.Updated) }
            Result.CANCEL -> events.tryEmit(PurchaseEvent.Cancelled)
        }
        return null
    }

    companion object {
        const val PRICE = "₹499.00 (test)"
        private const val TOKEN = "fake-token"
    }
}
