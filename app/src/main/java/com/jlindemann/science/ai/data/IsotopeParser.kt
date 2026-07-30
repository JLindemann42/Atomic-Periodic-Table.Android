package com.jlindemann.science.ai.data

/**
 * One isotope, parsed out of the `iso_N` / `iso_mass_N` / `decay_type_N` / `iso_half_N` /
 * `iso_Z_N` / `iso_N_N` / `iso_A_N` block.
 *
 * @property halfLifeSeconds normalised half-life, null when stable or unparseable
 * @property halfLifeDisplay the authored half-life string, shown verbatim
 * @property stable true when the source says stable or observationally stable
 */
data class Isotope(
    val name: String,
    val massNumber: Int?,
    val decayType: String?,
    val halfLifeSeconds: Double?,
    val halfLifeDisplay: String,
    val stable: Boolean,
    val protons: Int?,
    val neutrons: Int?,
    val nucleons: Int?
)

/**
 * Reads the flat 42-slot isotope block out of an element row.
 *
 * The half-life strings are inconsistent across 47 distinct unit spellings, including a
 * corrupted `"minu-Tes"`, a bare `"m"` that means minutes rather than metres, `"million years"`,
 * and several scientific-notation variants using U+00D7 and U+2212. All of them are handled here
 * so downstream code sees one normalised number of seconds.
 */
object IsotopeParser {

    /** The data tops out at 42 isotope slots for a single element. */
    private const val MAX_SLOTS = 42

    private val STABLE_MARKERS = listOf("stable", "observationally stable")

    /** Unit spellings to seconds. Longest keys are matched first so "ms" never shadows "m". */
    private val UNIT_SECONDS: List<Pair<String, Double>> = listOf(
        "nanoseconds" to 1e-9, "nanosecond" to 1e-9,
        "microseconds" to 1e-6, "microsecond" to 1e-6,
        "milliseconds" to 1e-3, "millisecond" to 1e-3,
        "million years" to 3.15576e7 * 1e6, "million y" to 3.15576e7 * 1e6,
        "billion years" to 3.15576e7 * 1e9,
        "seconds" to 1.0, "second" to 1.0,
        "minutes" to 60.0, "minute" to 60.0, "minutes" to 60.0,
        "hours" to 3600.0, "hour" to 3600.0,
        "days" to 86400.0, "day" to 86400.0,
        "years" to 3.15576e7, "year" to 3.15576e7,
        "ps" to 1e-12, "ns" to 1e-9, "μs" to 1e-6, "µs" to 1e-6, "us" to 1e-6,
        "ms" to 1e-3, "min" to 60.0, "hr" to 3600.0,
        "s" to 1.0, "h" to 3600.0, "d" to 86400.0, "y" to 3.15576e7, "a" to 3.15576e7,
        // A bare "m" is minutes in this data (e.g. "8.718 (m)", "4.12 m"), never metres.
        "m" to 60.0
    ).sortedByDescending { it.first.length }

    /** `5.0×10^−21 (s)`, `570*10^-24 (s)`, `7.17(24)×10^5 (Years)`. */
    private val SCIENTIFIC = Regex("""([\d.]+)\s*[×xX*]\s*10\s*\^?\s*([+\-−–]?\d+)""")
    private val LEADING_NUMBER = Regex("""^\s*([\d.]+)""")
    private val GLUED_UNCERTAINTY = Regex("""(\d)\((\d+)\)""")

    /**
     * Parse every isotope present on an element row.
     *
     * All slots are scanned rather than stopping at the first gap: the blocks are *mostly*
     * contiguous, but bromine's runs 1-5, skips 6, then continues to 34. Breaking early there
     * dropped 29 of its isotopes, including the stable ones.
     */
    fun parse(row: ElementRow): List<Isotope> {
        val isotopes = ArrayList<Isotope>()
        for (slot in 1..MAX_SLOTS) {
            val name = row["iso_$slot"]?.toString()?.trim()
            if (name.isNullOrEmpty() || ValueParser.isNullToken(name)) continue
            val halfRaw = row["iso_half_$slot"]?.toString()?.trim().orEmpty()
            isotopes.add(
                Isotope(
                    name = name,
                    massNumber = intOf(row["iso_mass_$slot"]),
                    decayType = row["decay_type_$slot"]?.toString()?.trim()
                        ?.takeUnless { ValueParser.isNullToken(it) },
                    halfLifeSeconds = halfLifeSeconds(halfRaw),
                    halfLifeDisplay = halfRaw,
                    stable = isStable(halfRaw),
                    protons = intOf(row["iso_Z_$slot"]),
                    neutrons = intOf(row["iso_N_$slot"]),
                    nucleons = intOf(row["iso_A_$slot"])
                )
            )
        }
        return isotopes
    }

    /** True when the source marks the isotope stable, including "Observationally Stable". */
    fun isStable(halfLife: String): Boolean {
        val lower = halfLife.trim().lowercase()
        return STABLE_MARKERS.any { lower.startsWith(it) }
    }

    /**
     * Normalise a half-life string to seconds.
     *
     * @return the half-life in seconds, or null when stable, absent, or genuinely unparseable
     */
    fun halfLifeSeconds(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty() || ValueParser.isNullToken(s) || isStable(s)) return null

        // "18.3 minu-Tes" — a corrupted "minutes" that appears 19 times. Only this exact
        // corruption is repaired: stripping hyphens generally would destroy negative exponents
        // such as the "-24" in "570*10^-24 (s)".
        s = s.replace("minu-Tes", "minutes", ignoreCase = true)
        s = GLUED_UNCERTAINTY.replace(s) { it.groupValues[1] }
        s = s.replace('−', '-').replace('–', '-')
        s = s.trimStart('~', '≈', '<', '>', ' ')

        val magnitude: Double
        val remainder: String
        val sci = SCIENTIFIC.find(s)
        if (sci != null) {
            val mantissa = sci.groupValues[1].toDoubleOrNull() ?: return null
            val exponent = sci.groupValues[2].replace("+", "").toIntOrNull() ?: return null
            magnitude = mantissa * Math.pow(10.0, exponent.toDouble())
            remainder = s.substring(sci.range.last + 1)
        } else {
            val m = LEADING_NUMBER.find(s) ?: return null
            magnitude = m.groupValues[1].toDoubleOrNull() ?: return null
            remainder = s.substring(m.range.last + 1)
        }

        val factor = unitFactor(remainder) ?: return null
        return magnitude * factor
    }

    /** Match the unit token in the tail of a half-life string; longest spelling wins. */
    private fun unitFactor(remainder: String): Double? {
        val cleaned = remainder.lowercase()
            .replace("(", " ").replace(")", " ").replace("^", " ")
            .replace(Regex("""[\d.,]"""), " ")
            .trim()
        if (cleaned.isEmpty()) return null
        for ((token, seconds) in UNIT_SECONDS) {
            if (cleaned == token || cleaned.startsWith("$token ") || cleaned.startsWith(token) &&
                cleaned.length == token.length
            ) return seconds
        }
        // Fall back to the first whitespace-delimited word, for tails like "years old".
        val first = cleaned.substringBefore(' ')
        return UNIT_SECONDS.firstOrNull { it.first == first }?.second
    }

    private fun intOf(value: Any?): Int? =
        value?.toString()?.trim()?.takeUnless { ValueParser.isNullToken(it) }
            ?.filter { it.isDigit() }?.toIntOrNull()
}
