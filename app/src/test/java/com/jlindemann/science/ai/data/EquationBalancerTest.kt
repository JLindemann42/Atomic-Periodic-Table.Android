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

    /** A comparison operator is not a reaction arrow. */
    @Test
    fun comparisonOperatorsAreNotEquations() {
        assertNull(EquationBalancer.parseEquation("density >= 5") { it in known })
        assertNull(EquationBalancer.parseEquation("mass == 12") { it in known })
    }

    @Test
    fun tooManySpeciesIsDeclined() {
        val many = (1..8).joinToString(" + ") { "H2" } + " -> " + (1..8).joinToString(" + ") { "H2" }
        assertNotNull(reasonFor(many))
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
