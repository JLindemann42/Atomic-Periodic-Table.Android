package com.jlindemann.science.ai.cards

import kotlin.math.abs
import kotlin.math.log10

/** How a chart axis maps values to pixels. */
enum class ChartScale { LINEAR, LOG10 }

/**
 * Human labels for decade gridlines.
 *
 * The chart views hold no branch that depends on what a number *means* — that rule is what makes it
 * acceptable that their drawing is untested. Choosing whether 10⁵ seconds should read as "1 day" is
 * exactly such a decision, so it lives here, where a unit test can pin it.
 */
object DecadeLabels {

    private val TIME_UNITS = listOf(
        1e-15 to "fs", 1e-12 to "ps", 1e-9 to "ns", 1e-6 to "µs", 1e-3 to "ms",
        1.0 to "s", 60.0 to "min", 3600.0 to "h", 86_400.0 to "d",
        31_557_600.0 to "y", 31_557_600e3 to "ky", 31_557_600e6 to "My", 31_557_600e9 to "Gy"
    )

    /**
     * A half-life gridline label for a power of ten in seconds.
     *
     * Half-lives span roughly 10⁻²² s to 10²⁷ s — fifty decades — so a raw "1.0E+17 s" tick is
     * useless. This picks the largest unit that leaves a value of at least one.
     */
    fun timeLabel(log10Seconds: Int): String {
        val seconds = Math.pow(10.0, log10Seconds.toDouble())
        val (factor, unit) = TIME_UNITS.lastOrNull { seconds >= it.first } ?: TIME_UNITS.first()
        val scaled = seconds / factor
        val rendered = when {
            scaled >= 100 -> "%.0f".format(scaled)
            scaled >= 1 -> "%.0f".format(scaled)
            else -> "%.2g".format(scaled)
        }
        return "$rendered $unit"
    }

    /**
     * A scientific label for a power of ten, e.g. `10⁻⁶`.
     *
     * Used where the quantity has no natural unit ladder — abundance spans ten orders of magnitude
     * in mg/kg and there is no "kilo-mg/kg" worth naming.
     */
    fun sciLabel(log10: Int): String = if (log10 == 0) "1" else "10${superscript(log10)}"

    private fun superscript(value: Int): String {
        val digits = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        val sign = if (value < 0) "⁻" else ""
        return sign + abs(value).toString().map { digits[it - '0'] }.joinToString("")
    }

    /**
     * Whether a set of values needs a log axis.
     *
     * A decade of spread is the threshold: below it a linear axis reads more directly, above it the
     * small values collapse onto the baseline. Returning the decision rather than making it inside a
     * view is what keeps the choice testable.
     */
    fun suggestedScale(values: List<Double>): ChartScale {
        val positive = values.filter { it > 0 }
        if (positive.size < 2) return ChartScale.LINEAR
        val spread = log10(positive.max()) - log10(positive.min())
        return if (spread >= 1.0) ChartScale.LOG10 else ChartScale.LINEAR
    }
}
