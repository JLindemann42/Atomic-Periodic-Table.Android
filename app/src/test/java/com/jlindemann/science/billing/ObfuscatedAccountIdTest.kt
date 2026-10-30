package com.jlindemann.science.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The binding between a Play purchase and a Firebase account.
 *
 * Worth pinning down: the value has to mean the same thing for the same account forever. If a
 * later change altered the hash, every Ultimate bought before it would stop matching the uid that
 * claims it, and the server could no longer tell a real purchase from a forged flag.
 */
class ObfuscatedAccountIdTest {

    private val uid = "k3Jq8vZ1aXbYc2Nd4Pe5Rf6Sg7Th"

    @Test
    fun `is stable for the same uid`() {
        assertEquals(
            BillingManager.obfuscatedAccountId(uid),
            BillingManager.obfuscatedAccountId(uid)
        )
    }

    @Test
    fun `differs between accounts`() {
        assertNotEquals(
            BillingManager.obfuscatedAccountId(uid),
            BillingManager.obfuscatedAccountId(uid.dropLast(1) + "Z")
        )
    }

    @Test
    fun `fits the field Play gives it`() {
        // Play caps obfuscatedAccountId at 64 characters; SHA-256 hex is exactly 64.
        val id = BillingManager.obfuscatedAccountId(uid)
        assertEquals(64, id.length)
        assertTrue("not lowercase hex: $id", id.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `does not leak the uid`() {
        // The point of hashing is that Play never receives the account identifier itself.
        assertTrue(!BillingManager.obfuscatedAccountId(uid).contains(uid))
    }

    @Test
    fun `is a real SHA-256`() {
        // Pinned against a known digest so a future refactor cannot quietly change the algorithm.
        assertEquals(
            "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae",
            BillingManager.obfuscatedAccountId("foo")
        )
    }
}
