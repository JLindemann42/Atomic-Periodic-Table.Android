package com.jlindemann.science.ai.data

/**
 * A parsed numeric value from the element data, with its unit and its original rendering.
 *
 * The element JSON stores everything as unit-suffixed strings (`"2743 (K)"`, `"160-350 (MPa)"`,
 * `"8.0 × 10^4"`). [ValueParser] turns those into a Quantity so the agent can compare, rank and
 * aggregate. [display] always keeps the authored string, so a plain lookup can echo the source
 * verbatim rather than round-tripping through a formatter.
 *
 * @property value the value, or the low end when [high] is set
 * @property high the high end of a range, null when the value is a scalar
 * @property unit normalised unit, e.g. `K`, `g/cm³`, `MPa`; null when the value is dimensionless
 * @property display the original authored string, rendered as-is for verbatim answers
 * @property approximate true when the source marked the value with `~`, `≈`, `<` or `>`
 * @property note a qualifier the source attached in brackets or as a prefix,
 *   e.g. `room temperature`, `Graphite`, `Br2`
 */
data class Quantity(
    val value: Double,
    val high: Double? = null,
    val unit: String? = null,
    val display: String,
    val approximate: Boolean = false,
    val note: String? = null
) {
    /** True when this represents a range rather than a single value. */
    val isRange: Boolean get() = high != null

    /**
     * The value to sort and aggregate on: the midpoint for ranges, the value itself otherwise.
     * Ranking by midpoint is a deliberate choice; callers should surface [isRange] to the user.
     */
    val mid: Double get() = high?.let { (value + it) / 2 } ?: value
}

/**
 * The typed result of reading one field off one element.
 *
 * [Missing] is the crucial case: the element JSON uses `"---"` (sometimes with a unit still
 * attached, as in `"--- (pm)"`), `"N/A"` and empty strings as its absent-value sentinels, and
 * roughly a third of the fields are populated for fewer than half the elements. Modelling absence
 * explicitly is what lets the agent say it has no data instead of printing a sentinel or silently
 * falling through to an unrelated answer.
 */
sealed class FieldValue {

    /** The element has no value for this field. */
    object Missing : FieldValue()

    /** A parsed numeric value. */
    data class Num(val quantity: Quantity) : FieldValue()

    /** Free text that carries no number, e.g. `"Deep Antiquity"`, `"Au^+, Au^3+"`. */
    data class Text(val raw: String) : FieldValue()

    /**
     * A value from a closed vocabulary.
     *
     * @property canonical language-invariant key used for filtering and comparison
     * @property localized the value as authored in the active language, used for display
     */
    data class Enum(val canonical: String, val localized: String) : FieldValue()

    /** A composite value, e.g. `lattice_constants` or `debye_temperature`. */
    data class Struct(val parts: Map<String, Quantity>) : FieldValue()

    /** The source recorded a presence too small to quantify (`"trace"`). */
    object Trace : FieldValue()

    val isMissing: Boolean get() = this === Missing

    /** The [Quantity] if this is a [Num], else null. Convenience for ranking and filtering. */
    fun asQuantity(): Quantity? = (this as? Num)?.quantity
}
