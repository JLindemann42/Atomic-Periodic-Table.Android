package com.jlindemann.science.ai.data

/** One side's term before balancing: a formula and the atom counts it contributes. */
data class Species(val formula: String, val counts: Map<String, Int>)

/** A balanced term, ready to render. */
data class Term(val coefficient: Int, val formula: String)

/** Per-element proof that both sides agree, or the evidence that they cannot. */
data class ElementTally(val symbol: String, val left: Int, val right: Int)

/**
 * Balances a chemical equation with exact rational arithmetic.
 *
 * The app already ships a balancer in `ChemicalReactionsActivity`, and it does not work: it
 * augments the system with a zero right-hand side and then reads the solution out of that same
 * zero column, so every input comes back all zeros or NaN. This is a fresh implementation rather
 * than a repair of that one, and it is deliberately kept in the data layer where it can be tested
 * without an Activity.
 *
 * Every step runs in [Fraction]. Row reduction over a stoichiometry matrix produces thirds and
 * sevenths constantly, and in floating point a leftover 1e-12 is indistinguishable from a real
 * zero pivot — which is the difference between an equation that balances and one that does not.
 * There is no epsilon here because any epsilon would be a guess.
 *
 * The failure modes are as important as the success. An equation with several independent balances
 * is reported as such rather than answered with whichever one the elimination happened to produce.
 */
object EquationBalancer {

    /** Why an equation could not be balanced. Each is a sentence the user is owed. */
    enum class Reason {
        /** An element appears on only one side, so no choice of coefficients can fix it. */
        ONE_SIDED_ELEMENT,

        /** The system admits only the trivial all-zero solution. */
        NO_SOLUTION,

        /**
         * Several independent balances exist.
         *
         * `CO + O2 + H2 -> CO2 + H2O` is the everyday example: two reactions are written as one,
         * and picking a basis vector out of the null space would present one arbitrary answer as
         * the answer.
         */
        UNDERDETERMINED,

        /** A coefficient ran past [MAX_COEFFICIENT], or the rationals overflowed. */
        TOO_LARGE,

        /** A species did not parse as a chemical formula. */
        PARSE_FAILED
    }

    sealed class Result {
        data class Balanced(
            val reactants: List<Term>,
            val products: List<Term>,
            val tally: List<ElementTally>
        ) : Result()

        data class Failed(val reason: Reason, val detail: String? = null) : Result()
    }

    /**
     * What reading the text produced.
     *
     * Three different situations used to be one `null`: no arrow yet, an arrow over unreadable
     * formulas, and an equation too long to solve. Callers cannot tell them apart from a null, so
     * they all guessed the same way — the tool showed "type an equation" to somebody who had, and
     * the assistant said "I could not read that" about an equation it had read perfectly well and
     * merely found too long.
     */
    sealed class ParseOutcome {
        data class Sides(val reactants: List<Species>, val products: List<Species>) : ParseOutcome()

        /** No reaction arrow. The normal state while somebody is still typing. */
        object NoArrow : ParseOutcome()

        /** An arrow, but a side would not read as chemical formulas. */
        object NotAnEquation : ParseOutcome()

        /** Read fine, but past [MAX_SPECIES]. */
        object TooManySpecies : ParseOutcome()
    }

    /** Beyond this the input is not a question anybody typed on purpose. */
    const val MAX_SPECIES = 12

    /**
     * Where a side stops being parsed at all.
     *
     * [MAX_SPECIES] is a verdict — an equation of thirteen species is reported as too large. This
     * is a guard: prose that happens to be full of `+` signs must never reach [reduce] and set it
     * multiplying rationals across a matrix that wide.
     */
    private const val PARSE_CEILING = 40

    /** A real equation does not need coefficients this large; past it, something has gone wrong. */
    const val MAX_COEFFICIENT = 1000

    /**
     * Reaction arrows.
     *
     * A bare `=` counts, since plenty of people write one, but never `==`, `<=`, `>=` or `!=`:
     * those appear in comparisons, and reading one as an equation would claim a query that is not
     * one. This file used to claim that in a comment while testing `raw.contains("=")`, which is
     * true of all four — so `"H2 == O2"` was declined by the planner, which had the lookarounds,
     * and balanced by the tool, which called straight in here. The pattern lives here now and the
     * planner uses it, so the two cannot disagree again.
     *
     * Matching rather than scanning a list of literals also fixes the split point: [Regex.find]
     * returns the *earliest* arrow, where `SEPARATORS.firstOrNull { raw.contains(it) }` returned
     * whichever was listed first and cut `"A = B -> C"` at the `->`. The `-{1,2}>` branch is
     * greedy, so `-->` is still never read as `-` followed by `->`.
     */
    val ARROW = Regex("""(?:<->|-{1,2}>|=>|→|⇌|⇄|⟶|(?<![<>=!])=(?![=>]))""")

    /** Splits a side on `+`, but only a `+` that separates: `Na+` is a charge. */
    private val PLUS = Regex("""\s\+\s|\s\+|\+\s""")

    /** A leading stoichiometric coefficient the user supplied, which is re-derived rather than kept. */
    private val LEADING_COEFFICIENT = Regex("""^(\d+)\s*(?=[A-Za-z(])""")

    /**
     * Split raw text into reactants and products, saying which way it failed if it did.
     *
     * [ParseOutcome.NoArrow] is the one a caller must not treat as an error: it is what half a
     * typed equation looks like, and what a sentence that was never an equation looks like.
     */
    fun parse(raw: String, isKnownSymbol: (String) -> Boolean): ParseOutcome {
        val arrow = ARROW.find(raw) ?: return ParseOutcome.NoArrow
        val left = sideOf(raw.substring(0, arrow.range.first), isKnownSymbol)
            ?: return ParseOutcome.NotAnEquation
        val right = sideOf(raw.substring(arrow.range.last + 1), isKnownSymbol)
            ?: return ParseOutcome.NotAnEquation
        if (left.isEmpty() || right.isEmpty()) return ParseOutcome.NotAnEquation
        // A verdict, not a parse error: the equation was read, there is just too much of it.
        if (left.size + right.size > MAX_SPECIES) return ParseOutcome.TooManySpecies
        return ParseOutcome.Sides(left, right)
    }

    /**
     * Split raw text into reactants and products.
     *
     * @return the two sides, or null when the text is not an equation this can solve — which is how
     *   the planner declines a sentence containing a stray arrow rather than claiming it. Use
     *   [parse] where the reason matters; it is the same work with the reason kept.
     */
    fun parseEquation(
        raw: String,
        isKnownSymbol: (String) -> Boolean
    ): Pair<List<Species>, List<Species>>? =
        (parse(raw, isKnownSymbol) as? ParseOutcome.Sides)?.let { it.reactants to it.products }

    /**
     * Parse one side of an equation.
     *
     * Everything before the first parseable species is dropped, so "balance this reaction: Fe + O2"
     * loses its preamble rather than failing on it. Once a species has parsed, though, a token that
     * does not parse fails the whole side — a half-read equation is worse than none.
     */
    private fun sideOf(raw: String, isKnownSymbol: (String) -> Boolean): List<Species>? {
        val species = ArrayList<Species>()
        for (token in raw.split(PLUS)) {
            val cleaned = LEADING_COEFFICIENT.replace(
                ChemistryMath.stripStateAnnotation(token).trim(), ""
            ).trim()
            if (cleaned.isEmpty()) continue

            // An equation written in a sentence carries words on both ends: "balance this
            // reaction: Fe" in front and "Fe2O3 संतुलित करें" behind. Trying only the whole token
            // drops the species along with the prose, and the equation then fails as one-sided on
            // an element that was written down — a confident, wrong diagnosis of a good question.
            // So when the token as a whole does not parse, each word in it is tried in turn.
            val candidates = listOf(cleaned) + cleaned.split(' ', ':').map { it.trim() }
            val parsed = candidates.firstNotNullOfOrNull { candidate ->
                if (candidate.isEmpty()) null
                else ChemistryMath.elementCounts(candidate) { isKnownSymbol(it) }
                    ?.let { Species(candidate, it) }
            }
            if (parsed == null) {
                // Prose in front of the equation is expected; prose inside it is not.
                if (species.isEmpty()) continue
                return null
            }
            species.add(parsed)
            // Stop reading rather than hand [reduce] a matrix this wide. Anything over MAX_SPECIES
            // is refused upstream anyway, so nothing solvable is lost by not counting past here.
            if (species.size > PARSE_CEILING) return species
        }
        return species
    }

    /**
     * Find the smallest positive integer coefficients that balance the equation.
     *
     * The matrix has one row per element and one column per species, reactants positive and
     * products negative, so a balanced equation is exactly a positive vector in the null space.
     */
    fun balance(reactants: List<Species>, products: List<Species>): Result {
        if (reactants.isEmpty() || products.isEmpty()) {
            return Result.Failed(Reason.PARSE_FAILED)
        }
        if (reactants.size + products.size > MAX_SPECIES) {
            return Result.Failed(Reason.TOO_LARGE)
        }

        val species = reactants + products
        val symbols = species.flatMap { it.counts.keys }.distinct()

        // An element written on one side only can never be evened out, whatever the coefficients.
        // Saying so names the actual mistake, where "no solution" leaves the user hunting for it.
        for (symbol in symbols) {
            val onLeft = reactants.any { it.counts.containsKey(symbol) }
            val onRight = products.any { it.counts.containsKey(symbol) }
            if (!onLeft || !onRight) return Result.Failed(Reason.ONE_SIDED_ELEMENT, symbol)
        }

        val matrix = symbols.map { symbol ->
            species.mapIndexed { column, s ->
                val count = (s.counts[symbol] ?: 0).toLong()
                val signed = if (column < reactants.size) count else -count
                Fraction.of(signed) ?: return Result.Failed(Reason.TOO_LARGE)
            }.toMutableList()
        }.toMutableList()

        val pivots = reduce(matrix) ?: return Result.Failed(Reason.TOO_LARGE)
        val freeColumns = species.indices.filterNot { column -> pivots.containsValue(column) }

        // Nullity 0 means only the zero vector solves it; more than 1 means several independent
        // balances exist and choosing between them would be a guess dressed up as an answer.
        when {
            freeColumns.isEmpty() -> return Result.Failed(Reason.NO_SOLUTION)
            freeColumns.size > 1 -> return Result.Failed(Reason.UNDERDETERMINED)
        }

        val freeColumn = freeColumns.single()
        val solution = MutableList(species.size) { Fraction.ZERO }
        solution[freeColumn] = Fraction.ONE
        for ((row, column) in pivots) {
            // With the matrix in reduced row echelon form, the pivot row reads
            // x_pivot + coefficient * x_free = 0.
            val coefficient = matrix[row][freeColumn]
            solution[column] = -coefficient
        }

        val integers = toSmallestIntegers(solution) ?: return Result.Failed(Reason.TOO_LARGE)
        if (integers.any { it <= 0L }) return Result.Failed(Reason.NO_SOLUTION)
        if (integers.any { it > MAX_COEFFICIENT }) return Result.Failed(Reason.TOO_LARGE)

        val coefficients = integers.map { it.toInt() }
        val tally = symbols.map { symbol ->
            var left = 0
            var right = 0
            species.forEachIndexed { index, s ->
                val atoms = (s.counts[symbol] ?: 0) * coefficients[index]
                if (index < reactants.size) left += atoms else right += atoms
            }
            ElementTally(symbol, left, right)
        }
        // The elimination is believed only after its answer is checked back against the input.
        // If they disagree the bug is here, and emitting a confident wrong equation would hide it.
        if (tally.any { it.left != it.right }) return Result.Failed(Reason.NO_SOLUTION)

        return Result.Balanced(
            reactants = reactants.mapIndexed { i, s -> Term(coefficients[i], s.formula) },
            products = products.mapIndexed { i, s ->
                Term(coefficients[reactants.size + i], s.formula)
            },
            tally = tally
        )
    }

    /**
     * Reduce [matrix] to reduced row echelon form in place.
     *
     * @return row index to pivot column, or null when the rationals overflowed
     */
    private fun reduce(matrix: MutableList<MutableList<Fraction>>): Map<Int, Int>? {
        val pivots = LinkedHashMap<Int, Int>()
        val columns = matrix.firstOrNull()?.size ?: return pivots
        var row = 0

        for (column in 0 until columns) {
            val pivotRow = (row until matrix.size).firstOrNull { !matrix[it][column].isZero }
                ?: continue
            val swap = matrix[row]; matrix[row] = matrix[pivotRow]; matrix[pivotRow] = swap

            val pivot = matrix[row][column]
            for (c in 0 until columns) {
                matrix[row][c] = (matrix[row][c] / pivot) ?: return null
            }
            for (r in matrix.indices) {
                if (r == row) continue
                val factor = matrix[r][column]
                if (factor.isZero) continue
                for (c in 0 until columns) {
                    val product = (matrix[row][c] * factor) ?: return null
                    matrix[r][c] = (matrix[r][c] - product) ?: return null
                }
            }
            pivots[row] = column
            row++
            if (row == matrix.size) break
        }
        return pivots
    }

    /** Scale a rational solution to the smallest positive integers, or null on overflow. */
    private fun toSmallestIntegers(solution: List<Fraction>): List<Long>? {
        var multiplier = 1L
        for (f in solution) {
            multiplier = Fraction.lcm(multiplier, f.den) ?: return null
        }
        val scaled = solution.map { f ->
            runCatching { Math.multiplyExact(f.num, multiplier / f.den) }.getOrNull() ?: return null
        }
        val divisor = scaled.fold(0L) { acc, v -> Fraction.gcd(acc, v) }
        if (divisor == 0L) return null
        return scaled.map { it / divisor }
    }
}
