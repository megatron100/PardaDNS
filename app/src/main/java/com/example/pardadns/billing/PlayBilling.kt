package com.example.pardadns.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/** State of the Pro product on the user's Play account. */
enum class Ownership { NONE, PENDING, OWNED_UNACKED, OWNED }

/** Result of a successful purchase query. [purchaseToken] is set whenever a purchase exists. */
data class PurchaseState(val ownership: Ownership, val purchaseToken: String? = null)

/** Outcome of a purchase flow, reported asynchronously by Play. */
sealed interface PurchaseEvent {
    /** Purchases changed (bought, pending, or already owned) — re-query to see the result. */
    data object Updated : PurchaseEvent
    data object Cancelled : PurchaseEvent
    data class Failed(val message: String) : PurchaseEvent
}

/**
 * The small slice of Google Play Billing the app uses. [GooglePlayBilling] is the real one;
 * debug builds can swap in a fake via the `BILLING` debug hook.
 */
interface PlayBilling {
    /** Short label for debug output ("play" / "fake"). */
    val name: String

    val purchaseEvents: Flow<PurchaseEvent>

    /** Localized price (e.g. "₹499.00"), or null if the product can't be loaded. */
    suspend fun productPrice(productId: String): String?

    /** Current purchase of [productId], or null if Play couldn't be queried (keep the cache). */
    suspend fun queryPurchase(productId: String): PurchaseState?

    suspend fun acknowledge(purchaseToken: String): Boolean

    /** Opens Play's purchase sheet. Returns an error message if it couldn't be shown. */
    suspend fun launchPurchase(activity: Activity, productId: String): String?

    fun release() {}
}
