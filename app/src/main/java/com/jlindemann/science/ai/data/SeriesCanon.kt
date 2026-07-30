package com.jlindemann.science.ai.data

import com.jlindemann.science.R

/** The element series (chemical family), as a language-invariant key. */
enum class SeriesId {
    ALKALI_METAL,
    ALKALINE_EARTH_METAL,
    TRANSITION_METAL,
    POST_TRANSITION_METAL,
    METALLOID,
    REACTIVE_NONMETAL,
    OTHER_NONMETAL,
    NOBLE_GAS,
    HALOGEN,
    LANTHANOID,
    ACTINIDE,
    UNKNOWN;

    /** True for the series that are metals, used by "which metals…" style filters. */
    val isMetal: Boolean
        get() = this == ALKALI_METAL || this == ALKALINE_EARTH_METAL || this == TRANSITION_METAL ||
                this == POST_TRANSITION_METAL || this == LANTHANOID || this == ACTINIDE

    /** True for the series that are nonmetals. */
    val isNonmetal: Boolean
        get() = this == REACTIVE_NONMETAL || this == OTHER_NONMETAL || this == NOBLE_GAS || this == HALOGEN

    val labelRes: Int
        get() = when (this) {
            ALKALI_METAL -> R.string.ai_series_alkali_metal_label
            ALKALINE_EARTH_METAL -> R.string.ai_series_alkaline_earth_label
            TRANSITION_METAL -> R.string.ai_series_transition_metal_label
            POST_TRANSITION_METAL -> R.string.ai_series_transition_metal_label
            METALLOID -> R.string.ai_series_metalloid_label
            REACTIVE_NONMETAL, OTHER_NONMETAL -> R.string.ai_series_nonmetal_label
            NOBLE_GAS -> R.string.ai_series_noble_gas_label
            HALOGEN -> R.string.ai_series_halogen_label
            LANTHANOID -> R.string.ai_series_lanthanide_label
            ACTINIDE -> R.string.ai_series_actinide_label
            UNKNOWN -> R.string.ai_series_nonmetal_label
        }
}

/** The s/p/d/f block an element sits in. */
enum class Block { S, P, D, F, UNKNOWN }

/**
 * Canonicalises the element data's free-text classification strings.
 *
 * `element_group` in `elements_en.json` uses 15 distinct spellings for 11 concepts — both
 * `Post-Transition Metals` and `Post-transition Metals`, both `Other Nonmetal` and
 * `Other Nonmetals`, plus `Alkaline earth metal` and `Lanthanoids`. The legacy agent matched
 * these with `group.contains("Lanthanide")`, which never matches `"Lanthanoids"`, so series
 * filtering was partly broken. Canonicalising once here fixes that for every caller.
 *
 * Classification is language-invariant, so this always runs against the English table.
 */
object SeriesCanon {

    /** Canonical series for an `element_group` string. Never returns null; unmatched maps to UNKNOWN. */
    fun series(rawGroup: String?): SeriesId {
        val key = normalise(rawGroup ?: return SeriesId.UNKNOWN)
        return when {
            key.isEmpty() -> SeriesId.UNKNOWN
            key.contains("alkalineearth") || key.contains("alkaliearth") -> SeriesId.ALKALINE_EARTH_METAL
            key.contains("alkali") -> SeriesId.ALKALI_METAL
            key.contains("posttransition") -> SeriesId.POST_TRANSITION_METAL
            key.contains("transition") -> SeriesId.TRANSITION_METAL
            key.contains("metalloid") || key.contains("semimetal") -> SeriesId.METALLOID
            key.contains("noblegas") || key.contains("inertgas") -> SeriesId.NOBLE_GAS
            key.contains("halogen") -> SeriesId.HALOGEN
            // Lanthanoid / Lanthanide / Lanthanoids all collapse here.
            key.startsWith("lanthan") -> SeriesId.LANTHANOID
            key.startsWith("actin") -> SeriesId.ACTINIDE
            key.contains("reactivenonmetal") -> SeriesId.REACTIVE_NONMETAL
            key.contains("othernonmetal") -> SeriesId.OTHER_NONMETAL
            key.contains("nonmetal") -> SeriesId.OTHER_NONMETAL
            else -> SeriesId.UNKNOWN
        }
    }

    /** Canonical block for an `element_block` string such as `"d - block"`. */
    fun block(rawBlock: String?): Block {
        val key = normalise(rawBlock ?: return Block.UNKNOWN)
        return when {
            key.startsWith("s") -> Block.S
            key.startsWith("p") -> Block.P
            key.startsWith("d") -> Block.D
            key.startsWith("f") -> Block.F
            else -> Block.UNKNOWN
        }
    }

    /** Canonical phase key for `element_phase`, matched against the English table. */
    fun phase(rawPhase: String?): String? {
        if (rawPhase == null) return null
        return when (normalise(rawPhase)) {
            "solid" -> "solid"
            "liquid" -> "liquid"
            "gas" -> "gas"
            else -> null
        }
    }

    /** `radioactive` is authored as "Yes"/"No" in every language, so it is safe to read directly. */
    fun radioactive(raw: String?): Boolean {
        if (raw == null) return false
        return normalise(raw).startsWith("y")
    }

    /**
     * Lowercase, drop diacritics, strip everything that is not a letter or digit.
     * NFD splits accents into combining marks, which are not letters or digits and so fall away.
     */
    private fun normalise(raw: String): String {
        val decomposed = java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
        return buildString {
            for (c in decomposed) if (c.isLetterOrDigit()) append(c)
        }
    }
}
