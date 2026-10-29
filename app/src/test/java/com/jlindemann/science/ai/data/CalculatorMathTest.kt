package com.jlindemann.science.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The math behind roadmap 2.8's calculators: the ideal gas law, pH, ion configurations, empirical
 * formulas and stoichiometry.
 */
class CalculatorMathTest {

    @Test
    fun oneMoleAtStpIsTwentyTwoPointFourLitres() {
        val s = GasLaw.solve(atm = 1.0, litres = null, moles = 1.0, kelvin = 273.15)!!
        assertEquals(GasLaw.Unknown.VOLUME, s.unknown)
        assertEquals(22.414, s.litres, 0.001)
        assertEquals(1.0, GasLaw.solve(atm = null, litres = 22.414, moles = 1.0, kelvin = 273.15)!!.atm, 0.001)
        assertEquals(273.15, GasLaw.solve(atm = 1.0, litres = 22.414, moles = 1.0, kelvin = null)!!.kelvin, 0.01)
        assertEquals(2.0, GasLaw.solve(atm = 1.0, litres = 44.828, moles = null, kelvin = 273.15)!!.moles, 0.001)
    }

    @Test
    fun theGasLawNeedsExactlyThreePositiveValues() {
        assertNull(GasLaw.solve(1.0, 2.0, null, null))
        assertNull(GasLaw.solve(1.0, 2.0, 3.0, 4.0))
        assertNull(GasLaw.solve(1.0, -2.0, 3.0, null))
    }

    @Test
    fun strongAcidsAndBases() {
        assertEquals(2.0, AcidBase.solve("HCl", false, 0.01)!!.pH, 1e-6)
        assertEquals(12.0, AcidBase.solve("NaOH", true, 0.01)!!.pH, 1e-6)
        // Two protons and two hydroxides.
        assertEquals(1.0, AcidBase.solve("H2SO4", false, 0.05)!!.pH, 1e-6)
        assertEquals(13.0, AcidBase.solve("Ca(OH)2", true, 0.05)!!.pH, 1e-6)
        // Water's own ions keep a very dilute acid below 7.
        assertEquals(6.98, AcidBase.solve("HCl", false, 1e-8)!!.pH, 0.005)
        assertNull(AcidBase.solve("CH3COOH", false, 0.1))
    }

    @Test
    fun aStrongAcidIsFoundByItsComposition() {
        fun composition(f: String): Map<String, Int>? = ChemistryMath.parseFormula(f) { 1.0 }?.parts?.associate { it.symbol to it.count }
        assertEquals("HNO3" to false, AcidBase.strong("HNO3", ::composition))
        assertEquals("Ba(OH)2" to true, AcidBase.strong("BaO2H2", ::composition))
        assertNull(AcidBase.strong("NaCl", ::composition))
    }

    @Test
    fun ionConfigurations() {
        assertEquals("[Ar] 3d⁵", IonConfig.ion(26, "3d^6 4s^2", 3).toString())
        assertEquals("[Ar] 3d⁶", IonConfig.ion(26, "3d^6 4s^2", 2).toString())
        assertEquals("[Ar] 3d¹⁰", IonConfig.ion(29, "3d^10 4s^1", 1).toString())
        assertEquals("[Ne]", IonConfig.ion(11, "3s^1", 1).toString())
        assertEquals("Ne", IonConfig.ion(11, "3s^1", 1)!!.isoelectronicWith)
        assertEquals("[Ar]", IonConfig.ion(17, "3s^2 3p^5", -1).toString())
        assertEquals("[Ne]", IonConfig.ion(8, "2s^2 2p^4", -2).toString())
        assertEquals("[He]", IonConfig.ion(1, "1s^1", -1).toString())
        // The inert pair: lead keeps its 6s².
        assertEquals("[Xe] 4f¹⁴ 5d¹⁰ 6s²", IonConfig.ion(82, "4f^14, 5d^10, 6s^2, 6p^2", 2).toString())
        // Sodium cannot lose a second electron without breaking into the neon core.
        assertNull(IonConfig.ion(11, "3s^1", 2))
        assertEquals("Fe³⁺", IonConfig.ionLabel("Fe", 3))
        assertEquals("Cl⁻", IonConfig.ionLabel("Cl", -1))
    }

    private val masses = mapOf("H" to 1.00784, "C" to 12.011, "O" to 15.999, "Fe" to 55.845, "N" to 14.007)

    @Test
    fun empiricalFormulasFromPercentages() {
        assertEquals("CH2O", Empirical.fromMasses(mapOf("C" to 40.0, "H" to 6.7, "O" to 53.3), masses::get)!!.formula)
        // A ratio of 1.33 is cleared by three.
        val magnetite = Empirical.fromMasses(mapOf("Fe" to 72.4, "O" to 27.6), masses::get)!!
        assertEquals("Fe3O4", magnetite.formula)
        assertEquals(3, magnetite.multiplier)
        // Hill order: carbon, hydrogen, then the rest alphabetically, whatever order they were given in.
        assertEquals("CH2O", Empirical.fromMasses(mapOf("O" to 53.3, "H" to 6.7, "C" to 40.0), masses::get)!!.formula)
        assertNull(Empirical.fromMasses(mapOf("C" to 40.0), masses::get))
    }

    @Test
    fun empiricalAndMolecularFormulas() {
        val glucose = Empirical.fromFormula("C6H12O6", mapOf("C" to 6, "H" to 12, "O" to 6), masses::get)!!
        assertEquals("CH2O", glucose.formula)
        assertEquals(6, glucose.divisor)
        val butene = Empirical.fromMasses(mapOf("C" to 85.6, "H" to 14.4), masses::get, molarMass = 56.0)!!
        assertEquals("CH2", butene.formula)
        assertEquals("C4H8", butene.molecular!!.formula)
        // Not near a whole multiple of 30.03.
        assertNull(Empirical.molecular(mapOf("C" to 1, "H" to 2, "O" to 1), 30.026, 45.0))
    }

    private fun line(formula: String, coefficient: Int, reactant: Boolean) =
        Stoichiometry.Line(formula, coefficient, ChemistryMath.parseFormula(formula, masses::get)!!.molarMass, reactant)

    private val water = listOf(line("H2", 2, true), line("O2", 1, true), line("H2O", 2, false))

    @Test
    fun gramsThroughAnEquation() {
        val r = Stoichiometry.solve(water, listOf(Stoichiometry.Given(0, 4 / 2.01568, 4.0)))!!
        assertNull(r.limiting)
        assertEquals(35.749, r.amounts[2].grams, 0.01)
        assertEquals(31.75, r.amounts[1].grams, 0.01)
        assertEquals("2H2 + O2 → 2H2O", r.equation)
        // From a product back to a reactant.
        val back = Stoichiometry.solve(water, listOf(Stoichiometry.Given(2, 2.0, null)))!!
        assertEquals(2.0, back.amounts[0].moles, 1e-9)
    }

    @Test
    fun theLimitingReagentRunsOutFirst() {
        val r = Stoichiometry.solve(water, listOf(
            Stoichiometry.Given(0, 4 / 2.01568, 4.0), Stoichiometry.Given(1, 40 / 31.998, 40.0)
        ))!!
        assertEquals(0, r.limiting)
        assertEquals(35.749, r.amounts[2].grams, 0.01)
        assertEquals(1, r.leftovers.size)
        assertEquals(8.25, r.leftovers[0].grams, 0.01)
        assertNull(Stoichiometry.solve(water, emptyList()))
    }

    @Test
    fun anEquationIsCutOutOfItsSentence() {
        val known: (String) -> Boolean = { it in setOf("H", "O", "C", "N", "In", "I") }
        assertEquals("2H2 + O2 -> 2H2O",
            EquationBalancer.span("how many grams of water from 4 g of H2 in 2H2 + O2 -> 2H2O", known)!!.equation)
        assertEquals("2H2 + O2 -> 2H2O",
            EquationBalancer.span("2H2 + O2 -> 2H2O with 4 g H2 and 40 g O2", known)!!.equation)
        assertEquals("2H2 + O2 -> 2H2O", EquationBalancer.span("2 H2 + O2 = 2 H2O", known)!!.equation)
        assertEquals("H2 + O2 -> H2O", EquationBalancer.span("H2+O2->H2O", known)!!.equation)
        assertNull(EquationBalancer.span("the arrow -> points nowhere", known))
    }
}
