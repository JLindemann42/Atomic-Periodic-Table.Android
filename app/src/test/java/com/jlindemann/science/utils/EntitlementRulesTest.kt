package com.jlindemann.science.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntitlementRulesTest {

    // ---- Wire format ---------------------------------------------------------------------------

    @Test
    fun `wire strings round trip`() {
        for (tier in Tier.values()) {
            assertEquals(tier, EntitlementRules.fromWire(tier.wire))
        }
    }

    @Test
    fun `wire strings are the ones Windows reads`() {
        assertEquals("free", Tier.FREE.wire)
        assertEquals("pro", Tier.PRO.wire)
        assertEquals("pro+", Tier.PRO_PLUS.wire)
    }

    @Test
    fun `unrecognised wire values are free`() {
        assertEquals(Tier.FREE, EntitlementRules.fromWire(null))
        assertEquals(Tier.FREE, EntitlementRules.fromWire(""))
        assertEquals(Tier.FREE, EntitlementRules.fromWire("PRO"))
        assertEquals(Tier.FREE, EntitlementRules.fromWire("ultimate"))
    }

    // ---- Licences ------------------------------------------------------------------------------

    @Test
    fun `no purchases is free`() {
        assertEquals(
            Tier.FREE,
            EntitlementRules.fromLicences(hasPro = false, hasProPlus = false, hasProUpgrade = false)
        )
    }

    @Test
    fun `pro plus implies pro`() {
        // Buying PRO+ outright means owning neither the PRO product nor the upgrade. A tier that
        // did not fold that in would leave the user outside every PRO gate in the app.
        val tier = EntitlementRules.fromLicences(
            hasPro = false, hasProPlus = true, hasProUpgrade = false
        )
        assertEquals(Tier.PRO_PLUS, tier)
    }

    @Test
    fun `the upgrade product alone is pro plus`() {
        assertEquals(
            Tier.PRO_PLUS,
            EntitlementRules.fromLicences(hasPro = true, hasProPlus = false, hasProUpgrade = true)
        )
    }

    @Test
    fun `ultimate alone is pro plus`() {
        assertEquals(
            Tier.PRO_PLUS,
            EntitlementRules.fromLicences(
                hasPro = false, hasProPlus = false, hasProUpgrade = false, hasUltimate = true
            )
        )
    }

    // ---- Reconcile: the regression test that matters ---------------------------------------------

    @Test
    fun `silence from the store never revokes`() {
        // The bug this exists to prevent: the PRO page ran before the purchase query came back,
        // read empty flags, and wrote the user's PRO away. Null means "no answer", not "you own
        // nothing".
        assertEquals(Tier.PRO, EntitlementRules.reconcile(Tier.PRO, answer = null))
        assertEquals(Tier.PRO_PLUS, EntitlementRules.reconcile(Tier.PRO_PLUS, answer = null))
    }

    @Test
    fun `a definite answer wins over what is stored`() {
        assertEquals(Tier.PRO_PLUS, EntitlementRules.reconcile(Tier.FREE, Tier.PRO_PLUS))
        // Including downwards - a real answer of "free" is allowed to revoke.
        assertEquals(Tier.FREE, EntitlementRules.reconcile(Tier.PRO_PLUS, Tier.FREE))
    }

    // ---- Ultimate ------------------------------------------------------------------------------

    @Test
    fun `ultimate only ever grants`() {
        assertEquals(Tier.PRO_PLUS, EntitlementRules.withUltimate(Tier.FREE, ultimate = true))
        assertEquals(Tier.PRO_PLUS, EntitlementRules.withUltimate(Tier.PRO, ultimate = true))

        // A false flag means "no Ultimate seen", which must leave the platform tier alone.
        assertEquals(Tier.PRO_PLUS, EntitlementRules.withUltimate(Tier.PRO_PLUS, ultimate = false))
        assertEquals(Tier.PRO, EntitlementRules.withUltimate(Tier.PRO, ultimate = false))
        assertEquals(Tier.FREE, EntitlementRules.withUltimate(Tier.FREE, ultimate = false))
    }

    // ---- Badges --------------------------------------------------------------------------------

    @Test
    fun `badges name the platform`() {
        assertEquals("Windows PRO+", EntitlementRules.badgeFor("windows", Tier.PRO_PLUS))
        assertEquals("Android PRO", EntitlementRules.badgeFor("android", Tier.PRO))
    }

    @Test
    fun `free has no badge`() {
        assertNull(EntitlementRules.badgeFor("windows", Tier.FREE))
        assertNull(EntitlementRules.badgeFor("android", Tier.FREE))
    }

    @Test
    fun `tier names are bare product names`() {
        // The platform is named by the row the name sits in, so it is not repeated here.
        assertEquals("PRO+", EntitlementRules.tierName(Tier.PRO_PLUS))
        assertEquals("PRO", EntitlementRules.tierName(Tier.PRO))
        assertNull(EntitlementRules.tierName(Tier.FREE))
    }

    @Test
    fun `ultimate outranks the platform tier in the summary`() {
        // An Ultimate owner's Android tier is PRO+ as a consequence of the Ultimate. Showing
        // "Android PRO+" would name a purchase they never made and hide the one they did.
        assertEquals(
            EntitlementRules.ULTIMATE_PRODUCT_NAME,
            EntitlementRules.summary(ultimate = true, platformTier = Tier.PRO_PLUS)
        )
        assertEquals(
            EntitlementRules.ULTIMATE_PRODUCT_NAME,
            EntitlementRules.summary(ultimate = true, platformTier = Tier.FREE)
        )
    }

    @Test
    fun `without ultimate the summary is this platform's badge`() {
        assertEquals("Android PRO", EntitlementRules.summary(false, Tier.PRO))
        assertNull(EntitlementRules.summary(false, Tier.FREE))
    }

    @Test
    fun `the ultimate product name matches what Windows writes`() {
        // Both apps show this literal, and Windows reads the same ultimate_version resource.
        assertEquals("PRO+ Ultimate", EntitlementRules.ULTIMATE_PRODUCT_NAME)
    }
}
