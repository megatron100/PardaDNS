package com.example.pardadns.billing

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.example.pardadns.data.repository.ProRepository
import com.example.pardadns.data.repository.RemoteConfigRepository
import com.example.pardadns.service.ProtectionScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Pro purchase: loads the price, runs the purchase flow, acknowledges purchases and
 * re-queries Play on app start / resume / "Restore purchases". The result goes to
 * [ProRepository.setPlayOwnership]; everything else reads [ProRepository.isPro].
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proRepository: ProRepository,
    private val remoteConfig: RemoteConfigRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshLock = Mutex()
    private var eventsJob: Job? = null
    private var started = false

    private var backend: PlayBilling = GooglePlayBilling(context)
    val backendName: String get() = backend.name

    private val _price = MutableStateFlow<String?>(null)
    /** Play's localized price for the Pro product, once loaded. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _pending = MutableStateFlow(proRepository.ownership.value == Ownership.PENDING)
    /** A purchase is waiting for payment (e.g. cash / bank transfer) — not Pro yet. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-shot messages for the paywall (cancelled, failed, restored…). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Connects and checks purchases. Called once from Application.onCreate. */
    fun start() {
        if (started) return
        started = true
        observe(backend)
        refresh()
        // A new product ID from Remote Config → reload its price and ownership.
        scope.launch { remoteConfig.proProductId.drop(1).collect { _price.value = null; refresh() } }
        // Schedule alarms only run for Pro — re-arm or cancel them when that changes.
        scope.launch { proRepository.isPro.drop(1).collect { ProtectionScheduler.reschedule(context) } }
    }

    /** Re-queries Play. [userInitiated] = "Restore purchases": reports the outcome. */
    fun refresh(userInitiated: Boolean = false) {
        scope.launch { refreshNow(userInitiated) }
    }

    fun buy(activity: Activity) {
        scope.launch {
            backend.launchPurchase(activity, remoteConfig.proProductId.value)?.let { _messages.tryEmit(it) }
        }
    }

    /** Debug builds only: replace Play with a fake (see the `BILLING` debug hook). */
    fun useBackend(fake: PlayBilling) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (backend === fake) return
        backend.release()
        backend = fake
        _price.value = null
        observe(fake)
    }

    private fun observe(b: PlayBilling) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            b.purchaseEvents.collect { event ->
                when (event) {
                    PurchaseEvent.Updated -> refreshNow(userInitiated = false, afterPurchase = true)
                    PurchaseEvent.Cancelled -> Unit
                    is PurchaseEvent.Failed -> _messages.tryEmit(event.message)
                }
            }
        }
    }

    private suspend fun refreshNow(userInitiated: Boolean, afterPurchase: Boolean = false) = refreshLock.withLock {
        val b = backend
        val productId = remoteConfig.proProductId.value
        if (_price.value == null) _price.value = b.productPrice(productId)

        val state = b.queryPurchase(productId)
        if (state == null) {
            // Offline / Play unavailable: keep the cached entitlement.
            if (userInitiated) _messages.tryEmit("Couldn't reach Google Play. Try again later.")
            return@withLock
        }

        var ownership = state.ownership
        if (ownership == Ownership.OWNED_UNACKED && state.purchaseToken != null) {
            // Play refunds purchases that aren't acknowledged within 3 days.
            if (b.acknowledge(state.purchaseToken)) ownership = Ownership.OWNED
            else Log.w(TAG, "Acknowledge failed — will retry on next refresh")
        }

        val wasOwned = proRepository.ownership.value == Ownership.OWNED
        proRepository.setPlayOwnership(ownership)
        _pending.value = ownership == Ownership.PENDING
        Log.i(TAG, "refresh(${b.name}) $productId → $ownership")

        when {
            ownership == Ownership.PENDING && (afterPurchase || userInitiated) ->
                _messages.tryEmit("Payment pending. Pro unlocks once Google Play confirms it.")
            ownership == Ownership.OWNED && !wasOwned && afterPurchase ->
                _messages.tryEmit("Welcome to Pro!")
            userInitiated ->
                _messages.tryEmit(if (ownership == Ownership.OWNED) "Purchase restored" else "No purchase found")
        }
    }

    private companion object {
        const val TAG = "PardaDNS/Billing"
    }
}
