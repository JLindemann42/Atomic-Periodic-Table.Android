package com.jlindemann.science.ai.data

/** The four quantities in `C₁V₁ = C₂V₂`, all known once the missing one has been solved for. */
data class Dilution(val c1: Double, val v1: Double, val c2: Double, val v2: Double)

/**
 * Solution chemistry: `n = c·V`, mass through a molar mass, and dilution.
 *
 * Everything here is in mol, litres, mol/L and grams. Converting mL, mmol and mM is the scanner's
 * job, done once at the edge — carrying mixed units this far in is how a factor of a thousand
 * ends up in an answer that otherwise looks right.
 */
object SolutionMath {

    /**
     * Complete `n = c·V` from any two of the three.
     *
     * @return (moles, molarity in mol/L, volume in litres), or null when fewer than two are known
     *   or a known value is not positive — a zero volume or a negative concentration is a misread
     *   query, not a solution to report
     */
    fun complete(moles: Double?, molarity: Double?, litres: Double?): Triple<Double, Double, Double>? {
        val known = listOfNotNull(moles, molarity, litres)
        if (known.size < 2) return null
        if (known.any { it <= 0.0 || !it.isFinite() }) return null

        return when {
            moles != null && molarity != null -> Triple(moles, molarity, moles / molarity)
            moles != null && litres != null -> Triple(moles, moles / litres, litres)
            molarity != null && litres != null -> Triple(molarity * litres, molarity, litres)
            else -> null
        }
    }

    /** Grams of a substance of known molar mass. Null when the molar mass is not positive. */
    fun massOf(moles: Double, molarMassGramsPerMol: Double): Double? =
        if (molarMassGramsPerMol > 0.0 && moles.isFinite()) moles * molarMassGramsPerMol else null

    /** Moles in a mass of a substance of known molar mass. */
    fun molesOf(grams: Double, molarMassGramsPerMol: Double): Double? =
        ChemistryMath.massToMoles(grams, molarMassGramsPerMol)

    /**
     * Solve `C₁V₁ = C₂V₂` for whichever one of the four is missing.
     *
     * @return every value filled in, or null unless exactly three are known and positive. Three is
     *   the whole condition: with four the user has asked nothing, and with two the equation has a
     *   family of answers rather than one.
     */
    fun dilution(c1: Double?, v1: Double?, c2: Double?, v2: Double?): Dilution? {
        val known = listOf(c1, v1, c2, v2)
        if (known.count { it != null } != 3) return null
        if (known.filterNotNull().any { it <= 0.0 || !it.isFinite() }) return null

        return when {
            c1 == null -> Dilution(c2!! * v2!! / v1!!, v1, c2, v2)
            v1 == null -> Dilution(c1, c2!! * v2!! / c1, c2, v2)
            c2 == null -> Dilution(c1, v1, c1 * v1 / v2!!, v2)
            else -> Dilution(c1, v1, c2, c1 * v1 / c2)
        }
    }

    /**
     * Solvent to add to get from `v1` to `v2`.
     *
     * Negative when the target is the more concentrated of the two, which is a real answer: it says
     * the sample has to be evaporated or made up differently, not diluted.
     */
    fun solventToAdd(v1: Double, v2: Double): Double = v2 - v1
}
