package com.jlindemann.science.ai.data

/**
 * Gram-to-gram stoichiometry and the limiting reagent (roadmap 2.8), over a balanced equation.
 *
 * Every given amount becomes moles; divided by its coefficient it says how far the reaction could
 * run on that amount alone. The smallest of those over the given reactants is how far it does run,
 * and that reactant is the limiting one. Every other amount follows from that extent.
 */
object Stoichiometry {

    /** One species of the balanced equation. */
    data class Line(val formula: String, val coefficient: Int, val molarMass: Double, val reactant: Boolean)

    /** An amount the question gave, for the species at [index]; [grams] when it was given as a mass. */
    data class Given(val index: Int, val moles: Double, val grams: Double?)

    /** An amount of the species at [index]. */
    data class Amount(val index: Int, val moles: Double, val grams: Double)

    /**
     * @property extent how many times the balanced equation runs, in moles
     * @property limiting the reactant that runs out first, when two or more reactants were given
     * @property amounts every species' amount at that extent, in equation order
     * @property leftovers what remains of each other given reactant
     */
    data class Result(
        val lines: List<Line>,
        val givens: List<Given>,
        val extent: Double,
        val limiting: Int?,
        val amounts: List<Amount>,
        val leftovers: List<Amount>
    ) {
        /** "2H2 + O2 → 2H2O". */
        val equation: String
            get() {
                fun side(reactant: Boolean) = lines.filter { it.reactant == reactant }
                    .joinToString(" + ") { (if (it.coefficient == 1) "" else "${it.coefficient}") + it.formula }
                return side(true) + " → " + side(false)
            }
    }

    /** Below this a leftover is rounding, not excess. */
    private const val NEGLIGIBLE = 1e-9

    fun solve(lines: List<Line>, givens: List<Given>): Result? {
        if (lines.isEmpty() || givens.isEmpty()) return null
        if (givens.any { it.index !in lines.indices || it.moles <= 0 || it.moles.isNaN() || it.moles.isInfinite() }) return null
        if (givens.map { it.index }.toSet().size != givens.size) return null
        val reactants = givens.filter { lines[it.index].reactant }
        // A product given alone fixes the extent; reactants given alongside one decide it instead.
        val deciding = reactants.ifEmpty { givens }
        val runs = deciding.associateWith { it.moles / lines[it.index].coefficient }
        val (first, extent) = runs.entries.minByOrNull { it.value }?.toPair() ?: return null
        val limiting = if (reactants.size >= 2) first.index else null
        val amounts = lines.mapIndexed { i, line ->
            val moles = extent * line.coefficient
            Amount(i, moles, moles * line.molarMass)
        }
        val leftovers = reactants.filter { it.index != first.index }.mapNotNull { given ->
            val left = given.moles - extent * lines[given.index].coefficient
            if (left <= NEGLIGIBLE) null else Amount(given.index, left, left * lines[given.index].molarMass)
        }
        return Result(lines, givens, extent, limiting, amounts, leftovers)
    }
}
