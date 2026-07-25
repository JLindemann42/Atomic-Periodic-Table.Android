package com.jlindemann.science.ai.data

/** One element's share of a compound's mass. */
data class CompositionPart(val symbol: String, val count: Int, val massContribution: Double, val percent: Double)

/** A parsed chemical formula with its molar mass and per-element breakdown. */
data class FormulaResult(
    val formula: String,
    val molarMass: Double,
    val parts: List<CompositionPart>
)

/**
 * Chemistry the element data implies but does not store: formula masses, neutron counts and
 * mole conversions.
 *
 * These were questions the agent could not answer at all — "the molar mass of H2SO4" retrieved
 * the dictionary definition of molar mass rather than computing a number, and "2 moles of carbon
 * into atoms" was answered as an element comparison.
 */
object ChemistryMath {

    /** Avogadro's number, particles per mole. */
    const val AVOGADRO = 6.02214076e23

    /** `H2SO4`, `Fe2(SO4)3`, `CuSO4·5H2O` — element symbol, optional count, optional groups. */
    private val TOKEN = Regex("""([A-Z][a-z]?)(\d*)|\(|\)(\d*)|·(\d*)""")

    /** Subscript digits users paste from formatted text, e.g. H₂SO₄. */
    private const val SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉"

    /**
     * Parse a formula and compute its molar mass from the element table.
     *
     * @param atomicMassOf supplies the atomic mass for a symbol, or null when unknown
     * @return the result, or null when the formula is unparseable or names an unknown element
     */
    fun parseFormula(raw: String, atomicMassOf: (String) -> Double?): FormulaResult? {
        val formula = normalise(raw)
        strictParse(formula, atomicMassOf)?.let { return it }

        // Users type formulas in lower case far more often than not — "h2so4" rather than
        // "H2SO4" — so recover the capitalisation before giving up. Only for candidates that
        // contain a digit, though: element symbols tile ordinary words alarmingly well, and
        // without this guard "percentage" parses as P-Er-Ce-N-Ta-Ge and weighs 605.93 g/mol.
        if (formula.none { it.isDigit() }) return null
        return recapitalise(formula, atomicMassOf)?.let { strictParse(it, atomicMassOf) }
    }

    /**
     * Restore standard capitalisation of a case-insensitive formula.
     *
     * Walks left to right taking the longest symbol that the caller recognises, so "h2so4"
     * resolves H, S, O rather than stalling on "Hs" or "So".
     */
    private fun recapitalise(formula: String, atomicMassOf: (String) -> Double?): String? {
        if (formula.isEmpty()) return null
        val out = StringBuilder(formula.length)
        var i = 0
        while (i < formula.length) {
            val c = formula[i]
            when {
                c.isDigit() || c == '(' || c == ')' -> { out.append(c); i++ }
                c.isLetter() -> {
                    val two = if (i + 1 < formula.length && formula[i + 1].isLetter()) {
                        formula.substring(i, i + 2).lowercase()
                            .replaceFirstChar { it.uppercase() }
                    } else null
                    val one = c.uppercase()
                    when {
                        two != null && atomicMassOf(two) != null -> { out.append(two); i += 2 }
                        atomicMassOf(one) != null -> { out.append(one); i++ }
                        else -> return null
                    }
                }
                else -> return null
            }
        }
        return out.toString()
    }

    private fun strictParse(formula: String, atomicMassOf: (String) -> Double?): FormulaResult? {
        if (formula.isEmpty() || !formula.first().isUpperCase()) return null

        val counts = LinkedHashMap<String, Int>()
        // Multipliers for the enclosing groups, innermost last.
        val stack = ArrayDeque<LinkedHashMap<String, Int>>()
        var current = counts
        var index = 0
        var sawElement = false

        while (index < formula.length) {
            val c = formula[index]
            when {
                c == '(' -> {
                    stack.addLast(current)
                    current = LinkedHashMap()
                    index++
                }
                c == ')' -> {
                    if (stack.isEmpty()) return null
                    index++
                    val multiplier = readNumber(formula, index).also { index += it.second }.first ?: 1
                    val parent = stack.removeLast()
                    for ((symbol, n) in current) parent.merge(symbol, n * multiplier, Int::plus)
                    current = parent
                }
                c.isUpperCase() -> {
                    var symbol = c.toString()
                    index++
                    if (index < formula.length && formula[index].isLowerCase()) {
                        symbol += formula[index]; index++
                    }
                    val count = readNumber(formula, index).also { index += it.second }.first ?: 1
                    current.merge(symbol, count, Int::plus)
                    sawElement = true
                }
                else -> return null
            }
        }
        if (!sawElement || stack.isNotEmpty()) return null

        var total = 0.0
        val masses = LinkedHashMap<String, Double>()
        for ((symbol, count) in counts) {
            val mass = atomicMassOf(symbol) ?: return null
            val contribution = mass * count
            masses[symbol] = contribution
            total += contribution
        }
        if (total <= 0.0) return null

        return FormulaResult(
            formula = formula,
            molarMass = total,
            parts = counts.map { (symbol, count) ->
                val contribution = masses.getValue(symbol)
                CompositionPart(symbol, count, contribution, contribution / total * 100.0)
            }
        )
    }

    /** Neutrons in a specific nuclide: mass number minus proton count. */
    fun neutronsIn(massNumber: Int, atomicNumber: Int): Int? =
        (massNumber - atomicNumber).takeIf { it >= 0 }

    /** Particles in a given number of moles. */
    fun molesToParticles(moles: Double): Double = moles * AVOGADRO

    /** Moles represented by a given number of particles. */
    fun particlesToMoles(particles: Double): Double = particles / AVOGADRO

    /** Moles in a mass of a substance of known molar mass. */
    fun massToMoles(grams: Double, molarMass: Double): Double? =
        if (molarMass > 0) grams / molarMass else null

    /**
     * Weighted mean atomic mass from isotope masses and their fractional abundances.
     * Abundances may be given as percentages or fractions; both are normalised.
     */
    fun averageAtomicMass(massesAndAbundances: List<Pair<Double, Double>>): Double? {
        if (massesAndAbundances.isEmpty()) return null
        val totalWeight = massesAndAbundances.sumOf { it.second }
        if (totalWeight <= 0) return null
        return massesAndAbundances.sumOf { it.first * it.second } / totalWeight
    }

    /** Uppercase-normalised formula with subscripts, spaces and dot separators folded out. */
    private fun normalise(raw: String): String = buildString {
        for (c in raw.trim()) {
            when {
                c in SUBSCRIPTS -> append(SUBSCRIPTS.indexOf(c))
                c == '·' || c == '*' -> append('·')
                c.isLetterOrDigit() || c == '(' || c == ')' -> append(c)
            }
        }
    }.replace("·", "")

    /** Reads digits at [from]; returns the value (null when absent) and how many chars consumed. */
    private fun readNumber(text: String, from: Int): Pair<Int?, Int> {
        var i = from
        while (i < text.length && text[i].isDigit()) i++
        if (i == from) return null to 0
        return text.substring(from, i).toIntOrNull() to (i - from)
    }
}
