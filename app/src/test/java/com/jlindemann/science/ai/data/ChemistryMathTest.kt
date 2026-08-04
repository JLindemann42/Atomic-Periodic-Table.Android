package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

class ChemistryMathTest {

    /** Standard atomic weights, enough for the formulas under test. */
    private val masses = mapOf(
        "H" to 1.008, "C" to 12.011, "N" to 14.007, "O" to 15.999, "Na" to 22.990,
        "S" to 32.06, "Cl" to 35.45, "K" to 39.098, "Ca" to 40.078, "Fe" to 55.845,
        "Cu" to 63.546, "Zn" to 65.38
    )

    private fun parse(formula: String) = ChemistryMath.parseFormula(formula) { masses[it] }

    @Test
    fun computesMolarMassOfSimpleFormulas() {
        assertEquals(18.015, parse("H2O")!!.molarMass, 0.01)
        assertEquals(98.07, parse("H2SO4")!!.molarMass, 0.02)
        assertEquals(58.44, parse("NaCl")!!.molarMass, 0.01)
        assertEquals(44.009, parse("CO2")!!.molarMass, 0.01)
        assertEquals(180.156, parse("C6H12O6")!!.molarMass, 0.02)
    }

    @Test
    fun handlesParenthesisedGroups() {
        // Ca(OH)2 = 40.078 + 2*(15.999 + 1.008)
        assertEquals(74.092, parse("Ca(OH)2")!!.molarMass, 0.01)
        // Fe2(SO4)3 needs the multiplier applied to every element inside the group.
        val result = parse("Fe2(SO4)3")!!
        assertEquals(2, result.parts.first { it.symbol == "Fe" }.count)
        assertEquals(3, result.parts.first { it.symbol == "S" }.count)
        assertEquals(12, result.parts.first { it.symbol == "O" }.count)
    }

    @Test
    fun acceptsSubscriptCharactersUsersActuallyType() {
        assertEquals(parse("H2SO4")!!.molarMass, parse("H₂SO₄")!!.molarMass, 1e-9)
    }

    @Test
    fun percentageCompositionSumsToOneHundred() {
        val water = parse("H2O")!!
        assertEquals(100.0, water.parts.sumOf { it.percent }, 0.001)
        assertEquals(11.19, water.parts.first { it.symbol == "H" }.percent, 0.05)
        assertEquals(88.81, water.parts.first { it.symbol == "O" }.percent, 0.05)
    }

    @Test
    fun repeatedElementsAreCombined() {
        // Acetic acid written as CH3COOH has carbon in two places.
        val acetic = parse("CH3COOH")!!
        assertEquals(2, acetic.parts.first { it.symbol == "C" }.count)
        assertEquals(4, acetic.parts.first { it.symbol == "H" }.count)
        assertEquals(2, acetic.parts.first { it.symbol == "O" }.count)
    }

    @Test
    fun acceptsLowercaseFormulasUsersActuallyType() {
        assertEquals(parse("H2SO4")!!.molarMass, parse("h2so4")!!.molarMass, 1e-9)
        assertEquals("H2SO4", parse("h2so4")!!.formula)
        assertEquals(parse("H2O")!!.molarMass, parse("h2o")!!.molarMass, 1e-9)
    }

    /**
     * Element symbols tile ordinary English words unnervingly well. "percentage" is a valid
     * formula — P, Er, Ce, N, Ta, Ge — so case recovery is restricted to candidates containing
     * a digit, or "the percentage composition of water" answers with a 605 g/mol compound.
     */
    @Test
    fun ordinaryWordsAreNotParsedAsFormulas() {
        for (word in listOf("percentage", "composition", "since", "notice", "brass", "conscious")) {
            assertNull("'$word' must not parse as a formula", parse(word))
        }
    }

    @Test
    fun rejectsWhatIsNotAFormula() {
        assertNull(parse(""))
        assertNull(parse("hello world"))
        assertNull(parse("Xx2"))          // unknown element
        assertNull(parse("Ca(OH2"))       // unbalanced group
    }

    @Test
    fun neutronCountIsMassNumberMinusProtons() {
        assertEquals(146, ChemistryMath.neutronsIn(238, 92))   // uranium-238
        assertEquals(143, ChemistryMath.neutronsIn(235, 92))   // uranium-235
        assertEquals(6, ChemistryMath.neutronsIn(12, 6))       // carbon-12
        assertEquals(8, ChemistryMath.neutronsIn(14, 6))       // carbon-14
        assertEquals(0, ChemistryMath.neutronsIn(1, 1))        // protium
        assertNull("a mass number below the proton count is impossible",
            ChemistryMath.neutronsIn(1, 6))
    }

    @Test
    fun moleConversionsRoundTrip() {
        assertEquals(1.204e24, ChemistryMath.molesToParticles(2.0), 1e21)
        assertEquals(2.0, ChemistryMath.particlesToMoles(ChemistryMath.molesToParticles(2.0)), 1e-9)
        assertEquals(0.5, ChemistryMath.massToMoles(9.0, 18.0)!!, 1e-9)
        assertNull(ChemistryMath.massToMoles(9.0, 0.0))
    }

    @Test
    fun averageAtomicMassIsAbundanceWeighted() {
        // Chlorine: 75.76% Cl-35 (34.969), 24.24% Cl-37 (36.966) -> 35.45
        val average = ChemistryMath.averageAtomicMass(
            listOf(34.969 to 75.76, 36.966 to 24.24)
        )!!
        assertEquals(35.45, average, 0.01)

        // Fractions rather than percentages must give the same answer.
        val asFractions = ChemistryMath.averageAtomicMass(
            listOf(34.969 to 0.7576, 36.966 to 0.2424)
        )!!
        assertEquals(average, asFractions, 1e-9)
        assertNull(ChemistryMath.averageAtomicMass(emptyList()))
    }

    private fun counts(formula: String) =
        ChemistryMath.elementCounts(formula) { it in masses }

    @Test
    fun elementCountsResolvesGroupsAndMultipliers() {
        assertEquals(mapOf("Fe" to 2, "S" to 3, "O" to 12), counts("Fe2(SO4)3"))
        assertEquals(mapOf("H" to 2, "O" to 1), counts("H2O"))
        assertEquals(mapOf("Ca" to 3, "P" to 2, "O" to 8), ChemistryMath.elementCounts("Ca3(PO4)2") {
            it in masses || it == "P"
        })
    }

    /** The balancer needs counts even when the app has no atomic mass for a symbol. */
    @Test
    fun elementCountsDoesNotNeedAtomicMasses() {
        assertEquals(mapOf("U" to 1, "O" to 2), ChemistryMath.elementCounts("UO2") {
            it == "U" || it == "O"
        })
    }

    @Test
    fun elementCountsRecapitalisesLowercaseFormulas() {
        assertEquals(mapOf("H" to 2, "S" to 1, "O" to 4), counts("h2so4"))
    }

    /** The same digit gate parseFormula relies on: without it "percentage" is a compound. */
    @Test
    fun elementCountsRejectsOrdinaryWords() {
        assertNull(counts("percentage"))
        assertNull(counts("hello"))
        assertNull(counts(""))
    }

    @Test
    fun stateAnnotationsAreStrippedButStructureIsNot() {
        assertEquals(mapOf("H" to 2, "O" to 1), counts("H2O(l)"))
        assertEquals(mapOf("O" to 2), counts("O2 (g)"))
        assertEquals(mapOf("Na" to 1, "Cl" to 1), counts("NaCl(aq)"))
        // (OH) is structure, not a phase marker.
        assertEquals(mapOf("Ca" to 1, "O" to 2, "H" to 2), counts("Ca(OH)2"))
    }

    @Test
    fun existingFormulaParsingIsUnchangedByTheSplit() {
        assertEquals(18.015, parse("H2O")!!.molarMass, 0.01)
        assertEquals(mapOf("Fe" to 2, "S" to 3, "O" to 12), parse("Fe2(SO4)3")!!.parts
            .associate { it.symbol to it.count })
        assertNull(parse("percentage"))
    }
}
