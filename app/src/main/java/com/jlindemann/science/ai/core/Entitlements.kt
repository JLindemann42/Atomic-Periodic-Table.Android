package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.Tier

/**
 * What the current user is allowed to be told.
 *
 * The agent has to honour the same paywall the rest of the app does. Without this it is a way around
 * it: the Mechanical section of the element screen shows Young's modulus as a lock, while asking
 * "what is the Young's modulus of gold" answered in full — and "which element has the highest
 * Young's modulus" leaked a ranking over the whole gated column.
 *
 * Defaults to full access so every existing construction site — and every unit test — keeps working
 * unchanged; the app supplies the real entitlements.
 */
data class Entitlements(
    val isPro: Boolean = true,
    val isProPlus: Boolean = true
) {

    /** Whether the user owns what [tier] requires. PRO+ implies PRO. */
    fun allows(tier: Tier): Boolean = when (tier) {
        Tier.FREE -> true
        Tier.PRO -> isPro || isProPlus
        Tier.PRO_PLUS -> isProPlus
    }

    /** Whether a field's value may be revealed. */
    fun allowsField(fieldId: String): Boolean = allows(tierOf(fieldId))

    /**
     * The tier a field sits behind.
     *
     * Banked and multi-unit fields are addressed as `ionization_energy#3` and `melting_point@K`, so
     * the suffix is stripped before the lookup — otherwise a gated field would come back FREE simply
     * because it was asked for in a particular unit.
     */
    fun tierOf(fieldId: String): Tier {
        val base = fieldId.substringBefore('#').substringBefore('@')
        return FieldRegistry.byId[base]?.tier ?: Tier.FREE
    }

    /** The strictest tier among these fields, or null when they are all free. */
    fun blockingTier(fieldIds: Collection<String>): Tier? = fieldIds
        .map { tierOf(it) }
        .filterNot { allows(it) }
        .maxByOrNull { it.ordinal }

    companion object {
        /** Full access. The default, so tests and existing callers are unaffected. */
        val FULL = Entitlements()
    }
}
