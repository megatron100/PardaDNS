package com.example.pardadns.data.repository

import com.example.pardadns.billing.Ownership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProEntitlementTest {

    @Test
    fun `remote config defaults`() {
        assertEquals(
            mapOf("pro_enabled_for_all" to false, "pro_product_id" to "pro_lifetime"),
            RemoteConfigRepository.DEFAULTS,
        )
    }

    private fun isPro(
        ownership: Ownership = Ownership.NONE,
        enabledForAll: Boolean = false,
        debugOverride: Boolean? = null,
        debuggable: Boolean = false,
    ) = ProEntitlement.isPro(ownership, enabledForAll, debugOverride, debuggable)

    @Test
    fun `only an acknowledged purchase grants Pro`() {
        assertFalse(isPro(Ownership.NONE))
        assertFalse(isPro(Ownership.PENDING))
        assertFalse(isPro(Ownership.OWNED_UNACKED))
        assertTrue(isPro(Ownership.OWNED))
    }

    @Test
    fun `pro_enabled_for_all makes everyone Pro`() {
        assertTrue(isPro(Ownership.NONE, enabledForAll = true))
        assertTrue(isPro(Ownership.PENDING, enabledForAll = true))
    }

    @Test
    fun `debug override wins in debuggable builds`() {
        assertTrue(isPro(Ownership.NONE, debugOverride = true, debuggable = true))
        assertFalse(isPro(Ownership.OWNED, debugOverride = false, debuggable = true))
        assertFalse(isPro(Ownership.NONE, enabledForAll = true, debugOverride = false, debuggable = true))
    }

    @Test
    fun `debug override is ignored in release builds`() {
        assertFalse(isPro(Ownership.NONE, debugOverride = true, debuggable = false))
        assertTrue(isPro(Ownership.OWNED, debugOverride = false, debuggable = false))
    }
}
