package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.data.Dimension
import com.jlindemann.science.ai.data.UnitConverter

/**
 * One `<number><unit>` pair found in a query, with the span it occupied.
 *
 * @property value the number as written
 * @property unit canonical unit, as [UnitConverter.knownUnit] resolved it
 * @property dimension what that unit measures
 * @property range where in the raw query it was found, so a caller can tell two quantities apart
 *   by position — "dilute 50 mL of 2 M to 0.5 M" needs to know which molarity came first
 */
data class ScannedQuantity(
    val value: Double,
    val unit: String,
    val dimension: Dimension,
    val range: IntRange
) {
    /** This value expressed in [target], or null when the units do not convert. */
    fun inUnit(target: String): Double? = UnitConverter.convert(value, unit, target)
}

/**
 * Finds the quantities a user wrote in a query.
 *
 * Shared by the conversion, solution and decay intents so the three cannot disagree about what
 * "250 mL" means. That sharing is the point: when only the planner knew how to read a mole
 * quantity and the executor had its own idea, a query planned correctly and then executed to
 * nothing, which the user experiences as the assistant shrugging at its own answer.
 *
 * Scans the **raw** query, never the normalised one. `TextMatching.normalizeForLookup` lowercases,
 * and `M` and `m` — molar and metre — differ only by case.
 */
object QuantityScanner {

    /**
     * A number followed by a unit.
     *
     * Derived from `OperatorExtractor`'s equivalent, widened for the compound unit spellings the
     * new dimensions need: `mol/L`, `cm³`, `µg/L`. The unit group stays optional so a bare number
     * is consumed rather than leaving its digits to be re-matched as part of the next token.
     */
    private val NUMBER_WITH_UNIT = Regex(
        // Unicode letters, not `a-z`: Spanish writes "años", and an ASCII-only class matched the
        // leading "a" of it — which the converter reads as the angstrom, so "11460 años" scanned
        // as a length and the decay question lost its duration.
        // Digits after the letters, so `g/cm3` and `kg/m3` survive as written; the denominator
        // needs them for the same reason.
        """(-?\d+(?:[.,]\d+)?(?:\s*[×x*]\s*10\s*\^?\s*-?\d+)?)""" +
                """\s*(°?[\p{L}µμÅ°³²Ω]+[0-9³²]*(?:\s*/\s*[\p{L}³²·()]+[0-9³²]*)?)?"""
    )

    /** A number followed by a unit written in Devanagari, Arabic or Han script. */
    private val NUMBER_THEN_SCRIPT = Regex("""(-?\d+(?:[.,]\d+)?)\s*([^\s\d]{1,8})""")

    /** Every quantity in the query, in the order written. */
    fun scan(rawQuery: String): List<ScannedQuantity> {
        val found = LinkedHashMap<IntRange, ScannedQuantity>()

        for (match in NUMBER_WITH_UNIT.findAll(rawQuery)) {
            val unitToken = match.groupValues[2].takeIf { it.isNotBlank() } ?: continue
            val value = numberOf(match.groupValues[1]) ?: continue
            val unit = UnitConverter.knownUnit(unitToken) ?: continue
            val dimension = UnitConverter.dimensionOf(unit) ?: continue
            found[match.range] = ScannedQuantity(value, unit, dimension, match.range)
        }

        // Scripts that write no space before the unit and no Latin letters in it. The Latin pass
        // above matches only `[a-zA-Z…]`, so "11460 साल" presents it with nothing to fold.
        for (match in NUMBER_THEN_SCRIPT.findAll(rawQuery)) {
            if (found.keys.any { it.first == match.range.first }) continue
            val value = numberOf(match.groupValues[1]) ?: continue
            val tail = match.groupValues[2]
            val unit = Lexicon.SCRIPT_UNIT_WORDS.entries
                .sortedByDescending { it.key.length }
                .firstOrNull { tail.startsWith(it.key) }
                ?.value
                ?: continue
            val dimension = UnitConverter.dimensionOf(unit) ?: continue
            found[match.range] = ScannedQuantity(value, unit, dimension, match.range)
        }

        return found.values.sortedBy { it.range.first }
    }

    /** Every quantity of a given dimension, in the order written. */
    fun of(rawQuery: String, dimension: Dimension): List<ScannedQuantity> =
        scan(rawQuery).filter { it.dimension == dimension }

    /** The first quantity of a given dimension, or null when there is none. */
    fun firstOf(rawQuery: String, dimension: Dimension): ScannedQuantity? =
        of(rawQuery, dimension).firstOrNull()

    /**
     * A unit requested as the output, introduced by a preposition: "to °C", "in pm", "साल में".
     *
     * Distinct from the quantities above, which each carry a number. A target has none — that is
     * what makes it the target — so it cannot be told apart from a stray word by shape alone, and
     * the preposition is the only evidence available. Checked on both sides because Hindi, Urdu
     * and Filipino put it after.
     */
    fun targetUnit(rawQuery: String): String? {
        val tokens = TOKEN.findAll(rawQuery).map { it.value }.toList()

        /**
         * A unit sitting right after a number is the value being converted, not the answer being
         * asked for. Without this, "2 eV in kJ/mol" resolves eV — which has "in" on its right and
         * so satisfies the postpositional rule below perfectly — and the conversion runs backwards.
         */
        fun isSourceQuantity(index: Int) = tokens.getOrNull(index - 1)?.let(NUMBER::matches) == true

        fun unitAt(index: Int): String? {
            val token = tokens[index]
            // Only a bare number is not a unit. Rejecting anything containing a digit would throw
            // away `kg/m3`, `cm3` and `g/cm3` — the spellings people actually type.
            if (NUMBER.matches(token)) return null
            // UNIT_WORDS last: it is the list the property-lookup path already accepts as a
            // conversion target, and it carries the transliterated names — "सेल्सियस", "开尔文" —
            // that neither the converter's aliases nor the script table spell.
            return UnitConverter.knownUnit(token)
                ?: Lexicon.SCRIPT_UNIT_WORDS[token]
                ?: Lexicon.UNIT_WORDS[token.lowercase()]
        }

        // A preposition in front is the ordinary shape and wins outright. The trailing form is
        // checked only afterwards, because Hindi, Urdu and Filipino write it the other way round
        // and would otherwise never resolve a target at all.
        for (index in tokens.indices) {
            if (isSourceQuantity(index)) continue
            val unit = unitAt(index) ?: continue
            if (tokens.getOrNull(index - 1)?.lowercase() in Lexicon.UNIT_PREPOSITIONS) return unit
        }
        for (index in tokens.indices) {
            if (isSourceQuantity(index)) continue
            val unit = unitAt(index) ?: continue
            if (tokens.getOrNull(index + 1)?.lowercase() in Lexicon.UNIT_PREPOSITIONS) return unit
        }
        return null
    }

    private val TOKEN = Regex("""[^\s,;()]+""")
    private val NUMBER = Regex("""-?\d+(?:[.,]\d+)?""")

    /** Reads `12`, `12.5`, `12,5` and `3.5 × 10^-21`. */
    private fun numberOf(raw: String): Double? {
        val text = raw.replace(",", ".").replace(" ", "")
        val exponent = Regex("""^(-?[\d.]+)[×x*]10\^?(-?\d+)$""").matchEntire(text)
        if (exponent != null) {
            val mantissa = exponent.groupValues[1].toDoubleOrNull() ?: return null
            val power = exponent.groupValues[2].toIntOrNull() ?: return null
            return mantissa * Math.pow(10.0, power.toDouble())
        }
        return text.toDoubleOrNull()
    }
}
