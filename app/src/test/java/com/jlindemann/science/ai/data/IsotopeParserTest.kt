package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

/**
 * The half-life strings in the element data use 47 distinct unit spellings. Every shape asserted
 * here was observed in the shipped `elements_en.json`.
 */
class IsotopeParserTest {

    private val year = 3.15576e7

    @Test
    fun plainUnitSpellingsNormaliseToSeconds() {
        assertEquals(1.0, IsotopeParser.halfLifeSeconds("1 s")!!, 1e-12)
        assertEquals(1.0, IsotopeParser.halfLifeSeconds("1 seconds")!!, 1e-12)
        assertEquals(1e-3, IsotopeParser.halfLifeSeconds("1 ms")!!, 1e-12)
        assertEquals(1e-3, IsotopeParser.halfLifeSeconds("1 milliseconds")!!, 1e-12)
        assertEquals(1e-6, IsotopeParser.halfLifeSeconds("56 microseconds")!! / 56.0, 1e-12)
        assertEquals(1e-9, IsotopeParser.halfLifeSeconds("1 ns")!!, 1e-15)
        assertEquals(60.0, IsotopeParser.halfLifeSeconds("1 min")!!, 1e-9)
        assertEquals(3600.0, IsotopeParser.halfLifeSeconds("1 hours")!!, 1e-9)
        assertEquals(86400.0, IsotopeParser.halfLifeSeconds("1 days")!!, 1e-9)
        assertEquals(year, IsotopeParser.halfLifeSeconds("1 years")!!, 1.0)
    }

    @Test
    fun bareMMeansMinutesNotMetres() {
        // "8.718 (m)" and "4.12 m" are half-lives; treating m as metres would drop them entirely.
        assertEquals(8.718 * 60.0, IsotopeParser.halfLifeSeconds("8.718 (m)")!!, 1e-6)
        assertEquals(4.12 * 60.0, IsotopeParser.halfLifeSeconds("4.12 m")!!, 1e-6)
    }

    @Test
    fun corruptedMinutesSpellingIsRecovered() {
        // "18.3 minu-Tes" occurs 19 times in the shipped data.
        assertEquals(18.3 * 60.0, IsotopeParser.halfLifeSeconds("18.3 minu-Tes")!!, 1e-6)
    }

    @Test
    fun millionYearsIsHandled() {
        assertEquals(15.6 * 1e6 * year, IsotopeParser.halfLifeSeconds("15.6 million years")!!, 1e6)
    }

    @Test
    fun scientificNotationWithUnicodeSigns() {
        assertEquals(4e-20, IsotopeParser.halfLifeSeconds("~4×10^−20 (s)")!!, 1e-24)
        assertEquals(5.0e-21, IsotopeParser.halfLifeSeconds("5.0×10^−21 (s)")!!, 1e-25)
        assertEquals(5.7e-22, IsotopeParser.halfLifeSeconds("570*10^-24 (s)")!!, 1e-26)
    }

    @Test
    fun gluedUncertaintyIsIgnored() {
        assertEquals(7.17e5 * year, IsotopeParser.halfLifeSeconds("7.17(24)×10^5 (Years)")!!, 1e9)
    }

    @Test
    fun stableIsotopesHaveNoHalfLife() {
        assertTrue(IsotopeParser.isStable("Stable"))
        assertTrue(IsotopeParser.isStable("stable"))
        assertTrue(IsotopeParser.isStable("Observationally Stable"))
        assertTrue(IsotopeParser.isStable("Stable (d)"))
        assertNull(IsotopeParser.halfLifeSeconds("Stable"))
    }

    @Test
    fun unparseableAndSentinelValuesReturnNull() {
        for (raw in listOf("---", "N/A", "???", "?", "unknown", "")) {
            assertNull("'$raw' should not yield a half-life", IsotopeParser.halfLifeSeconds(raw))
        }
    }

    /**
     * Slots are scanned past gaps, not stopped at them. Bromine's real block runs 1-5, skips 6,
     * then continues to 34 — stopping early there lost 29 isotopes including the stable ones.
     */
    @Test
    fun blockScanContinuesPastAGap() {
        val row = mapOf(
            "iso_1" to "Gold-195", "iso_mass_1" to "195", "decay_type_1" to "EC",
            "iso_half_1" to "186.098 days", "iso_Z_1" to "79", "iso_N_1" to "116", "iso_A_1" to "195",
            "iso_2" to "Gold-197", "iso_mass_2" to "197", "decay_type_2" to "---",
            "iso_half_2" to "Stable", "iso_Z_2" to "79", "iso_N_2" to "118", "iso_A_2" to "197",
            // Slot 3 is absent; slot 4 must still be picked up.
            "iso_4" to "Gold-199", "iso_half_4" to "3.139 days"
        )
        val isotopes = IsotopeParser.parse(row)
        assertEquals(3, isotopes.size)
        assertEquals(listOf("Gold-195", "Gold-197", "Gold-199"), isotopes.map { it.name })
        assertEquals("Gold-195", isotopes[0].name)
        assertEquals(186.098 * 86400.0, isotopes[0].halfLifeSeconds!!, 1e-3)
        assertEquals("EC", isotopes[0].decayType)
        assertTrue(isotopes[1].stable)
        assertNull("a '---' decay type is absent, not the literal string", isotopes[1].decayType)
        assertEquals(79, isotopes[1].protons)
        assertEquals(118, isotopes[1].neutrons)
    }

    @Test
    fun sentinelInSlotOneYieldsNoIsotopes() {
        assertTrue(IsotopeParser.parse(mapOf("iso_1" to "---")).isEmpty())
        assertTrue(IsotopeParser.parse(emptyMap()).isEmpty())
    }
}
