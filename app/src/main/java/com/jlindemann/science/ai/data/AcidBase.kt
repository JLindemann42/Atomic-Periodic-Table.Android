package com.jlindemann.science.ai.data

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The pH of a strong acid or base in water (roadmap 2.8). Strong ones dissociate completely, so
 * the concentration fixes [H⁺] or [OH⁻]; a weak acid needs its Ka, which the app does not have, so
 * it is not answered here.
 */
object AcidBase {

    /** The ion product of water at 25 °C. */
    const val KW = 1.0e-14

    /** Strong acids, by formula, with the protons each gives up. Sulfuric acid counts both. */
    private val STRONG_ACIDS = mapOf(
        "HCl" to 1, "HBr" to 1, "HI" to 1, "HNO3" to 1, "HClO4" to 1, "HClO3" to 1, "H2SO4" to 2
    )

    /** Strong bases, by formula, with the hydroxides each releases. */
    private val STRONG_BASES = mapOf(
        "LiOH" to 1, "NaOH" to 1, "KOH" to 1, "RbOH" to 1, "CsOH" to 1,
        "Ca(OH)2" to 2, "Sr(OH)2" to 2, "Ba(OH)2" to 2
    )

    /**
     * @property factor protons per acid, or hydroxides per base
     * @property hydrogen [H⁺] in mol/L, counting water's own ions, so a very dilute acid stays below 7
     */
    data class Result(
        val formula: String,
        val molarity: Double,
        val factor: Int,
        val base: Boolean,
        val hydrogen: Double,
        val hydroxide: Double
    ) {
        val pH: Double get() = -log10(hydrogen)
        val pOH: Double get() = -log10(hydroxide)
    }

    /** The strong acid or base [formula] names, as the table writes it, or null. */
    fun strong(formula: String, composition: (String) -> Map<String, Int>?): Pair<String, Boolean>? {
        val target = composition(formula) ?: return null
        STRONG_ACIDS.keys.firstOrNull { composition(it) == target }?.let { return it to false }
        STRONG_BASES.keys.firstOrNull { composition(it) == target }?.let { return it to true }
        return null
    }

    /**
     * The solution's pH. The total of both sources of the ion is solved exactly,
     * x = (c + √(c² + 4·Kw)) / 2, which is c itself for any ordinary concentration and tends to 10⁻⁷
     * as c does: 10⁻⁸ M HCl is pH 6.98, not 8.
     */
    fun solve(formula: String, base: Boolean, molarity: Double): Result? {
        if (molarity <= 0.0 || molarity.isNaN() || molarity.isInfinite()) return null
        val factor = (if (base) STRONG_BASES[formula] else STRONG_ACIDS[formula]) ?: return null
        val supplied = molarity * factor
        val major = (supplied + sqrt(supplied * supplied + 4 * KW)) / 2
        val minor = KW / major
        return if (base) Result(formula, molarity, factor, true, hydrogen = minor, hydroxide = major)
        else Result(formula, molarity, factor, false, hydrogen = major, hydroxide = minor)
    }
}
