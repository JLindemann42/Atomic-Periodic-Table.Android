package com.jlindemann.science.ai.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Empirical and molecular formulas (roadmap 2.8): from the mass of each element, or a percentage,
 * which is the mass in 100 g; or from a molecular formula by dividing out its common factor.
 */
object Empirical {

    /** How far a mole ratio may sit from a whole number and still be read as one. */
    const val TOLERANCE = 0.1

    /** The largest multiplier tried to clear a ratio such as 1.5 or 1.33. */
    const val MAX_MULTIPLIER = 8

    /** How far the given molar mass may sit from a whole multiple of the empirical one, relatively. */
    const val MOLAR_MASS_TOLERANCE = 0.02

    /** One element's path from mass to subscript. */
    data class Row(val symbol: String, val mass: Double, val atomicMass: Double, val moles: Double, val ratio: Double, val count: Int)

    /** The molecular formula, [factor] times the empirical one, for the [molarMass] the question gave. */
    data class Molecular(val factor: Int, val counts: Map<String, Int>, val molarMass: Double) {
        val formula: String get() = format(counts)
    }

    /**
     * @property rows the working, in Hill order; empty when the formula was reduced rather than built
     * @property multiplier what the mole ratios were multiplied by to make them whole
     * @property from the molecular formula that was reduced, when one was given
     * @property divisor the common factor that was divided out of [from]
     */
    data class Result(
        val counts: Map<String, Int>,
        val empiricalMass: Double,
        val rows: List<Row>,
        val multiplier: Int,
        val from: String?,
        val divisor: Int,
        val molecular: Molecular?
    ) {
        val formula: String get() = format(counts)
    }

    /** The empirical formula from each element's mass or percentage, or null when no ratio comes out whole. */
    fun fromMasses(masses: Map<String, Double>, atomicMassOf: (String) -> Double?, molarMass: Double? = null): Result? {
        if (masses.size < 2 || masses.values.any { it <= 0 || it.isNaN() || it.isInfinite() }) return null
        val atomic = masses.keys.associateWith { atomicMassOf(it) ?: return null }
        val moles = masses.mapValues { (symbol, mass) -> mass / atomic.getValue(symbol) }
        val smallest = moles.values.minOrNull() ?: return null
        val ratios = moles.mapValues { it.value / smallest }
        val multiplier = (1..MAX_MULTIPLIER).firstOrNull { k ->
            ratios.values.all { r -> abs(r * k - (r * k).roundToInt()) <= TOLERANCE }
        } ?: return null
        val order = hill(masses.keys)
        val counts = order.associateWith { (ratios.getValue(it) * multiplier).roundToInt() }
        val rows = order.map {
            Row(it, masses.getValue(it), atomic.getValue(it), moles.getValue(it), ratios.getValue(it), counts.getValue(it))
        }
        val mass = massOf(counts, atomic::get) ?: return null
        return Result(counts, mass, rows, multiplier, from = null, divisor = 1, molecular = molarMass?.let { molecular(counts, mass, it) })
    }

    /** A molecular formula's empirical formula: its subscripts over their greatest common divisor. */
    fun fromFormula(formula: String, counts: Map<String, Int>, atomicMassOf: (String) -> Double?): Result? {
        if (counts.isEmpty() || counts.values.any { it <= 0 }) return null
        val divisor = counts.values.reduce(::gcd)
        val reduced = counts.mapValues { it.value / divisor }
        val mass = massOf(reduced, atomicMassOf) ?: return null
        return Result(reduced, mass, emptyList(), 1, from = formula, divisor = divisor, molecular = null)
    }

    /** The molecular formula with [molarMass], or null when it is not close to a whole multiple. */
    fun molecular(counts: Map<String, Int>, empiricalMass: Double, molarMass: Double): Molecular? {
        if (molarMass <= 0 || empiricalMass <= 0) return null
        val factor = (molarMass / empiricalMass).roundToInt()
        if (factor < 1 || abs(molarMass / (empiricalMass * factor) - 1) > MOLAR_MASS_TOLERANCE) return null
        return Molecular(factor, counts.mapValues { it.value * factor }, molarMass)
    }

    /** "CH2O": each symbol with its count, a count of one left out. */
    fun format(counts: Map<String, Int>): String =
        counts.entries.joinToString("") { (symbol, n) -> if (n == 1) symbol else "$symbol$n" }

    /** Carbon first, then hydrogen, then the rest alphabetically; with no carbon, all alphabetically. */
    fun hill(symbols: Collection<String>): List<String> =
        if ("C" in symbols) listOf("C") + listOfNotNull("H".takeIf { it in symbols }) + symbols.filter { it != "C" && it != "H" }.sorted()
        else symbols.sorted()

    private fun massOf(counts: Map<String, Int>, atomicMassOf: (String) -> Double?): Double? =
        counts.entries.sumOf { (symbol, n) -> (atomicMassOf(symbol) ?: return null) * n }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
