package com.example.pardadns.data.repository

import com.example.pardadns.billing.Ownership

/** Pure Pro rules, kept free of Android so they can be unit-tested. */
object ProEntitlement {

    /**
     * First match wins:
     * 1. debug override (debuggable builds only)
     * 2. `pro_enabled_for_all` promo → everyone is Pro
     * 3. an acknowledged Play purchase (pending / unacknowledged don't count)
     */
    fun isPro(
        ownership: Ownership,
        enabledForAll: Boolean,
        debugOverride: Boolean?,
        debuggable: Boolean,
    ): Boolean = when {
        debuggable && debugOverride != null -> debugOverride
        enabledForAll -> true
        else -> ownership == Ownership.OWNED
    }
}
