package com.jlindemann.science.ai.data

import java.text.Normalizer

/**
 * Turns the element JSON's unit-suffixed strings into typed [FieldValue]s.
 *
 * Every rule here was derived from a survey of the shipped `elements_en.json`, so the ordering is
 * load-bearing: scientific notation must be tried before the plain-number rule, the range rule
 * must run after the negative-number case is settled, and the null sentinel must be recognised
 * even when a unit is still glued to it (`"--- (pm)"` occurs 32 times).
 */
object ValueParser {

    /**
     * Sentinels the data uses for "no value". Compared after trimming and lowercasing.
     *
     * Deliberately excludes "na": it is sodium's symbol, and treating it as a sentinel silently
     * erased sodium from every symbol lookup. "n/a" alone covers the real absent marker.
     */
    private val NULL_TOKENS = setOf("", "---", "--", "n/a", "none", "null", "?", "???", "unknown")

    /** Leading markers meaning the number is not exact. */
    private val APPROX_PREFIXES = charArrayOf('~', '≈', '<', '>', '≲', '≳')

    /** `[room temperature]`, `[Graphite]`, `[27°C]` — a qualifier, not part of the number. */
    private val BRACKET_NOTE = Regex("""\[([^\[\]]*)]""")

    /** `Br2: 3.1028 (g/cm^3)`, `White: 317 (K)`, `Beta: 13.25 (g/cm^3)` — allotrope/molecular form. */
    private val FORM_PREFIX = Regex("""^([A-Za-z][A-Za-z0-9]{0,7})\s*:\s*""")

    /** `0.479 (Cl2) (kJ/mol)` — the diatomic form annotated mid-string. */
    private val MOLECULE_INFIX = Regex("""\((?:Cl|F|H|I|N|O|Br)\d\)\s*""")

    /** `7.17(24)×10^5` — a glued uncertainty on the last digits. */
    private val GLUED_UNCERTAINTY = Regex("""(\d)\((\d+)\)""")

    /** `8.0 × 10^4`, `5.0×10^−21`, `570*10^-24`, `700×10−24`. Note U+2212 and U+00D7. */
    private val SCIENTIFIC = Regex(
        """^([+-]?\d+(?:[.,]\d+)?)\s*[×xX*]\s*10\s*\^?\s*([+\-−–]?\d+)"""
    )

    /** A bare power such as `10^-8`, with no mantissa. */
    private val BARE_POWER = Regex("""^10\s*\^\s*([+\-−–]?\d+)""")

    /** `160-350 (MPa)`, `0.2-0.4`. Only applied to fields that genuinely carry ranges. */
    private val RANGE = Regex("""^(\d+(?:\.\d+)?)\s*[-–—]\s*(\d+(?:\.\d+)?)""")

    /** A signed decimal at the start of the string, possibly with thousands separators. */
    private val LEADING_NUMBER = Regex("""^([+\-−]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[+\-−]?\d*\.?\d+)""")

    /**
     * Parse one raw JSON value against its field's expectations.
     *
     * @param raw the value as it came out of the JSON: String, Int, Map, or null
     * @param spec the field's metadata; drives enum handling and whether ranges are meaningful
     */
    fun parse(raw: Any?, spec: FieldSpec): FieldValue {
        // Rule 1 — absent. Handles bare sentinels and unit-suffixed ones like "--- (pm)".
        if (raw == null) return FieldValue.Missing
        if (raw is Int) return FieldValue.Num(
            Quantity(raw.toDouble(), unit = spec.canonicalUnit, display = raw.toString())
        )

        // Rule 2 — nested structures (lattice_constants, debye_temperature).
        if (raw is Map<*, *>) {
            val parts = LinkedHashMap<String, Quantity>()
            for ((k, v) in raw) {
                val key = k?.toString() ?: continue
                val q = (parse(v, spec.asScalar()) as? FieldValue.Num)?.quantity ?: continue
                parts[key] = q
            }
            return if (parts.isEmpty()) FieldValue.Missing else FieldValue.Struct(parts)
        }

        val original = raw.toString()
        var s = original.trim()
        if (isNullToken(s)) return FieldValue.Missing

        // Multi-valued cells separate allotropes with a newline; take the first, keep the rest as a note.
        var extraNote: String? = null
        if (s.contains('\n')) {
            val lines = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size > 1) extraNote = lines.drop(1).joinToString("; ")
            s = lines.firstOrNull() ?: return FieldValue.Missing
        }

        // Rule 3 — bracketed qualifier, e.g. "5000 (m/s) [room temperature]".
        var note: String? = null
        BRACKET_NOTE.find(s)?.let {
            note = it.groupValues[1].trim().ifEmpty { null }
            s = s.replace(BRACKET_NOTE, " ").trim()
        }

        // Rule 4 — allotrope / molecular-form prefix, e.g. "Br2: 332.0 (K)".
        FORM_PREFIX.find(s)?.let { m ->
            val candidate = m.groupValues[1]
            // Only treat it as a form marker when a number follows; otherwise it is prose.
            val rest = s.substring(m.value.length).trimStart()
            if (rest.isNotEmpty() && (rest[0].isDigit() || rest[0] in APPROX_PREFIXES || rest[0] == '-')) {
                note = listOfNotNull(candidate, note).joinToString(", ")
                s = rest
            }
        }
        s = s.replace(MOLECULE_INFIX, "").trim()
        if (isNullToken(s)) return FieldValue.Missing

        // Rule 5 — closed vocabularies never go through numeric parsing.
        if (spec.kind == FieldKind.ENUM) {
            val canonical = spec.canonicalise(original.trim())
            return if (canonical == null) FieldValue.Missing
            else FieldValue.Enum(canonical, original.trim())
        }

        // Rule 6 — an unquantified presence.
        if (s.equals("trace", ignoreCase = true)) return FieldValue.Trace

        if (spec.kind == FieldKind.TEXT || spec.kind == FieldKind.ID || spec.kind == FieldKind.LINK) {
            return FieldValue.Text(original.trim())
        }

        // Rule 7 — strip a glued uncertainty: "7.17(24)×10^5" -> "7.17×10^5".
        s = GLUED_UNCERTAINTY.replace(s) { it.groupValues[1] }

        // Rule 8 — approximation markers.
        var approximate = false
        while (s.isNotEmpty() && s[0] in APPROX_PREFIXES) {
            approximate = true
            s = s.substring(1).trimStart()
        }

        note = listOfNotNull(note, extraNote).joinToString("; ").ifEmpty { null }

        // Rule 9 — scientific notation, before the plain-number rule so the exponent is not lost.
        SCIENTIFIC.find(s)?.let { m ->
            val mantissa = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            val exponent = normaliseSign(m.groupValues[2]).toIntOrNull()
            if (mantissa != null && exponent != null) {
                val unit = unitFrom(s.substring(m.value.length), spec)
                return FieldValue.Num(
                    Quantity(
                        mantissa * pow10(exponent), null, unit.unit, original.trim(), approximate,
                        note, unit.authored
                    )
                )
            }
        }
        BARE_POWER.find(s)?.let { m ->
            val exponent = normaliseSign(m.groupValues[1]).toIntOrNull()
            if (exponent != null) {
                val unit = unitFrom(s.substring(m.value.length), spec)
                return FieldValue.Num(
                    Quantity(pow10(exponent), null, unit.unit, original.trim(), approximate, note, unit.authored)
                )
            }
        }

        // Rule 10 — ranges, only where the field genuinely carries them. Elsewhere a hyphen is a
        // minus sign (melting points go negative) and treating it as a range would be silently wrong.
        if (spec.allowsRange) {
            RANGE.find(s)?.let { m ->
                val low = m.groupValues[1].toDoubleOrNull()
                val high = m.groupValues[2].toDoubleOrNull()
                if (low != null && high != null) {
                    val unit = unitFrom(s.substring(m.value.length), spec)
                    return FieldValue.Num(
                        Quantity(low, high, unit.unit, original.trim(), approximate, note, unit.authored)
                    )
                }
            }
        }

        // Rule 11 — a leading decimal, with thousands separators and percent handled.
        LEADING_NUMBER.find(s)?.let { m ->
            val numeric = normaliseSign(m.groupValues[1]).replace(",", "").toDoubleOrNull()
            if (numeric != null) {
                val remainder = s.substring(m.value.length)
                // A percent sign is glued to the number rather than spaced off it, so it never
                // reaches normaliseUnit; it is authored either way.
                val unit =
                    if (remainder.trimStart().startsWith("%")) UnitSource("%", authored = true)
                    else unitFrom(remainder, spec)
                return FieldValue.Num(
                    Quantity(numeric, null, unit.unit, original.trim(), approximate, note, unit.authored)
                )
            }
        }

        // Rule 12 — no number in there at all.
        return FieldValue.Text(original.trim())
    }

    /** True when the string is one of the data's absent-value sentinels, unit suffix and all. */
    fun isNullToken(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.lowercase() in NULL_TOKENS) return true
        // "--- (pm)", "--- J/(g·K)", "---  (K)": a sentinel that kept its unit.
        if (trimmed.startsWith("---")) {
            val rest = trimmed.removePrefix("---").trim()
            return rest.isEmpty() || rest.startsWith("(") || rest.first().isLetter()
        }
        return false
    }

    /**
     * Normalise a trailing unit: drop wrapping parentheses, collapse whitespace, and render
     * caret powers as superscripts so `(g/cm^3)` and `g/cm³` compare equal.
     */
    fun normaliseUnit(raw: String): String? {
        var u = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).trim()
        if (u.isEmpty()) return null
        if (u.startsWith("(") && u.endsWith(")") && u.count { it == '(' } == u.count { it == ')' }) {
            u = u.substring(1, u.length - 1).trim()
        }
        u = u.replace(Regex("""\s+"""), " ")
            .replace("^3", "³").replace("^2", "²").replace("^-1", "⁻¹")
        return u.ifEmpty { null }
    }

    /**
     * A unit and where it came from.
     *
     * The distinction matters downstream: a unit read off the value is already inside
     * [Quantity.display], while one taken from the registry is not and has to be added by whoever
     * renders the value. See [FieldSpec.displayWithUnit].
     */
    private data class UnitSource(val unit: String?, val authored: Boolean)

    private fun unitFrom(remainder: String, spec: FieldSpec): UnitSource =
        normaliseUnit(remainder)
            ?.let { UnitSource(it, authored = true) }
            ?: UnitSource(spec.canonicalUnit, authored = false)

    /** Map the Unicode minus and dashes onto ASCII so [String.toDoubleOrNull] accepts them. */
    private fun normaliseSign(raw: String): String =
        raw.replace('−', '-').replace('–', '-').replace('—', '-').replace("+", "")

    private fun pow10(exponent: Int): Double {
        var result = 1.0
        repeat(kotlin.math.abs(exponent)) { result *= 10.0 }
        return if (exponent < 0) 1.0 / result else result
    }
}
