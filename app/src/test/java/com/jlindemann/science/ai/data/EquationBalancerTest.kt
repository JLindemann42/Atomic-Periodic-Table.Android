package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

class EquationBalancerTest {

    /** Enough of the table for the equations under test; the balancer needs no masses. */
    private val known = setOf(
        "H", "C", "N", "O", "Na", "Mg", "Al", "S", "Cl", "K", "Ca", "Fe", "Cu", "Zn", "Mn", "P", "U"
    )

    private fun balance(equation: String): EquationBalancer.Result {
        val sides = EquationBalancer.parseEquation(equation) { it in known }
            ?: return EquationBalancer.Result.Failed(EquationBalancer.Reason.PARSE_FAILED)
        return EquationBalancer.balance(sides.first, sides.second)
    }

    private fun coefficients(equation: String): List<Int> {
        val result = balance(equation)
        assertTrue("expected a balance for $equation, got $result",
            result is EquationBalancer.Result.Balanced)
        result as EquationBalancer.Result.Balanced
        return (result.reactants + result.products).map { it.coefficient }
    }

    private fun reasonFor(equation: String): EquationBalancer.Reason? =
        (balance(equation) as? EquationBalancer.Result.Failed)?.reason

    @Test
    fun balancesTheTextbookEquations() {
        assertEquals(listOf(4, 3, 2), coefficients("Fe + O2 -> Fe2O3"))
        assertEquals(listOf(2, 1, 2), coefficients("H2 + O2 -> H2O"))
        assertEquals(listOf(3, 2, 1), coefficients("Fe + O2 -> Fe3O4"))
        assertEquals(listOf(1, 5, 3, 4), coefficients("C3H8 + O2 -> CO2 + H2O"))
    }

    /** Parentheses have to survive the walk, or polyatomic ions balance to nonsense. */
    @Test
    fun balancesThroughParenthesisedGroups() {
        assertEquals(listOf(3, 2, 1, 6), coefficients("Ca(OH)2 + H3PO4 -> Ca3(PO4)2 + H2O"))
    }

    /**
     * The redox case that motivates exact rationals. In floating point the elimination leaves
     * residues around 1e-16 on rows that must read as exactly zero.
     */
    @Test
    fun balancesASixSpeciesRedoxEquation() {
        assertEquals(
            listOf(2, 16, 2, 2, 8, 5),
            coefficients("KMnO4 + HCl -> KCl + MnCl2 + H2O + Cl2")
        )
    }

    @Test
    fun everySeparatorSpellingIsUnderstood() {
        for (arrow in listOf("->", "-->", "→", "=>", "=", "⇌", "<->")) {
            assertEquals("arrow $arrow", listOf(2, 1, 2), coefficients("H2 + O2 $arrow H2O"))
        }
    }

    @Test
    fun stateAnnotationsAreIgnored() {
        assertEquals(listOf(2, 1, 2), coefficients("H2(g) + O2(g) -> H2O(l)"))
    }

    /** Coefficients the user supplied are re-derived, not trusted. */
    @Test
    fun userSuppliedCoefficientsAreRecomputed() {
        assertEquals(listOf(2, 1, 2), coefficients("2H2 + O2 -> 2H2O"))
        assertEquals(listOf(2, 1, 2), coefficients("5H2 + 9O2 -> 3H2O"))
    }

    /** A charge written `Na+` must not be read as the separator between two species. */
    @Test
    fun anIonChargeIsNotASeparator() {
        val sides = EquationBalancer.parseEquation("Na+ + Cl -> NaCl") { it in known }
        assertNotNull(sides)
        assertEquals(2, sides!!.first.size)
    }

    @Test
    fun prosePrecedingTheEquationIsDropped() {
        assertEquals(listOf(4, 3, 2), coefficients("balance this reaction: Fe + O2 -> Fe2O3"))
    }

    /** An element on one side only names the actual mistake, where "no solution" would not. */
    @Test
    fun aOneSidedElementIsReportedAsSuch() {
        assertEquals(EquationBalancer.Reason.ONE_SIDED_ELEMENT, reasonFor("H2 + O2 -> H2"))
        assertEquals(EquationBalancer.Reason.ONE_SIDED_ELEMENT, reasonFor("Na + Cl2 -> NaClFe"))
    }

    /**
     * Two reactions written as one. Picking a basis vector out of the null space would present one
     * arbitrary balance as the answer, which is the failure this whole design exists to avoid.
     */
    @Test
    fun severalIndependentBalancesAreDeclinedRatherThanGuessed() {
        assertEquals(
            EquationBalancer.Reason.UNDERDETERMINED,
            reasonFor("CO + O2 + H2 -> CO2 + H2O")
        )
    }

    @Test
    fun nonFormulasAreRejected() {
        assertEquals(EquationBalancer.Reason.PARSE_FAILED, reasonFor("Xx -> Yy"))
        assertNull(EquationBalancer.parseEquation("gold is denser than lead") { it in known })
    }

    /**
     * A comparison operator is not a reaction arrow.
     *
     * `"H2 == H2"` is the one that used to get through: the separator test was `contains("=")`,
     * which is true of `==`, `<=`, `>=` and `!=` alike, and the leftover `=` was then normalised
     * away — so the tool balanced a comparison as an equation.
     */
    @Test
    fun comparisonOperatorsAreNotEquations() {
        for (text in listOf("density >= 5", "mass == 12", "H2 == H2", "a <= b", "a != b")) {
            assertNull(text, EquationBalancer.parseEquation(text) { it in known })
        }
    }

    /**
     * The split point is the first arrow written, not whichever spelling is listed first. Scanning
     * a list of literals cut `"H2 = O2 -> H2O"` at the `->`, a place the user did not write.
     */
    @Test
    fun theEarliestArrowIsTheSplitPoint() {
        val text = "H2 = O2 -> H2O"
        assertEquals(text.indexOf('='), EquationBalancer.ARROW.find(text)!!.range.first)
    }

    /** A two-character arrow is never read as one character followed by another. */
    @Test
    fun theLongestArrowSpellingWins() {
        assertEquals("-->", EquationBalancer.ARROW.find("H2 --> H2")!!.value)
        assertEquals("<->", EquationBalancer.ARROW.find("H2 <-> H2")!!.value)
    }

    /**
     * The three ways of not being a solvable equation used to be one `null`, so every caller told
     * the user the same thing about all of them.
     */
    @Test
    fun theWaysOfNotBeingAnEquationAreToldApart() {
        fun parse(text: String) = EquationBalancer.parse(text) { it in known }

        // Half a typed equation, and a sentence that was never one. Neither is a mistake.
        assertEquals(EquationBalancer.ParseOutcome.NoArrow, parse("Fe + O2"))
        assertEquals(EquationBalancer.ParseOutcome.NoArrow, parse("gold is denser than lead"))

        // An arrow was written; what sits beside it is not chemistry.
        assertEquals(EquationBalancer.ParseOutcome.NotAnEquation, parse("Xx -> Yy"))

        assertEquals(EquationBalancer.ParseOutcome.TooManySpecies, parse(tooManySpecies()))
        assertTrue(parse("Fe + O2 -> Fe2O3") is EquationBalancer.ParseOutcome.Sides)
    }

    @Test
    fun tooManySpeciesIsDeclined() {
        assertNotNull(reasonFor(tooManySpecies()))
        assertNull(EquationBalancer.parseEquation(tooManySpecies()) { it in known })
    }

    private fun tooManySpecies(): String =
        (1..8).joinToString(" + ") { "H2" } + " -> " + (1..8).joinToString(" + ") { "H2" }

    /** The solver knows which element is unbalanced; the message is only useful if it says which. */
    @Test
    fun theOneSidedElementIsNamed() {
        val failed = balance("H2 + O2 -> H2") as EquationBalancer.Result.Failed
        assertEquals(EquationBalancer.Reason.ONE_SIDED_ELEMENT, failed.reason)
        assertEquals("O", failed.detail)
    }

    /** The tally is recomputed from the final coefficients, so it must always agree. */
    @Test
    fun theTallyProvesTheAnswer() {
        val result = balance("C3H8 + O2 -> CO2 + H2O") as EquationBalancer.Result.Balanced
        assertEquals(setOf("C", "H", "O"), result.tally.map { it.symbol }.toSet())
        for (t in result.tally) assertEquals(t.symbol, t.left, t.right)
        assertEquals(3, result.tally.single { it.symbol == "C" }.left)
        assertEquals(8, result.tally.single { it.symbol == "H" }.left)
    }
}
