package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

class FieldRegistryTest {

    @Test
    fun fieldIdsAreUnique() {
        val duplicates = FieldRegistry.ALL.groupBy { it.id }.filter { it.value.size > 1 }.keys
        assertTrue("duplicate field ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun everyFieldHasALabelAndAtLeastOneJsonKey() {
        for (spec in FieldRegistry.ALL) {
            assertNotEquals("${spec.id} has no label resource", 0, spec.labelRes)
            assertTrue("${spec.id} has no JSON key", spec.jsonKeys.isNotEmpty())
        }
    }

    @Test
    fun numericFieldsDeclareTheirDimension() {
        val missing = FieldRegistry.numericFields
            .filter { it.canonicalUnit != null && it.dimension == null }
            .map { it.id }
        assertTrue("numeric fields with a unit but no dimension: $missing", missing.isEmpty())
    }

    @Test
    fun rangesAreOnlyAllowedWhereAHyphenMeansTo() {
        // Elsewhere a hyphen is a minus sign; a wrong flag makes values silently wrong, not missing.
        val expected = setOf(
            "young_modulus", "bulk_modulus", "shear_modulus", "poisson_ratio",
            "mohs_hardness", "vickers_hardness", "brinell_hardness",
            "speed_of_sound_solid", "speed_of_sound_liquid", "speed_of_sound_gas",
            "refractive_index", "superconducting_point"
        )
        assertEquals(expected, FieldRegistry.ALL.filter { it.allowsRange }.map { it.id }.toSet())
    }

    @Test
    fun temperatureFieldsCarryAllThreeScales() {
        val melting = FieldRegistry.byId.getValue("melting_point")
        assertEquals(
            listOf("element_melting_kelvin", "element_melting_celsius", "element_melting_fahrenheit"),
            melting.jsonKeys
        )
    }

    @Test
    fun ionizationEnergyIsOneBankedFieldNotThirty() {
        val spec = FieldRegistry.byId.getValue("ionization_energy")
        assertEquals(1..30, spec.ordinalRange)
        assertEquals("element_ionization_energy1", spec.jsonKeyForOrdinal(null))
        assertEquals("element_ionization_energy2", spec.jsonKeyForOrdinal(2))
        assertEquals("element_ionization_energy30", spec.jsonKeyForOrdinal(30))
        // Out-of-range slots clamp rather than producing a key that does not exist.
        assertEquals("element_ionization_energy30", spec.jsonKeyForOrdinal(99))
    }

    @Test
    fun reverseLookupFromJsonKeyWorks() {
        assertEquals("density", FieldRegistry.byJsonKey.getValue("element_density").id)
        assertEquals("melting_point", FieldRegistry.byJsonKey.getValue("element_melting_celsius").id)
        assertEquals("electronegativity", FieldRegistry.byJsonKey.getValue("element_electronegativty").id)
    }

    /**
     * Period and group are derived here because the JSON has no `element_period` or
     * `element_group_number` key at all — the legacy agent read both and always got an empty
     * string.
     */
    @Test
    fun periodsFollowTheRowBoundaries() {
        assertEquals(1, FieldRegistry.periodOf(1))    // H
        assertEquals(1, FieldRegistry.periodOf(2))    // He
        assertEquals(2, FieldRegistry.periodOf(3))    // Li
        assertEquals(2, FieldRegistry.periodOf(10))   // Ne
        assertEquals(3, FieldRegistry.periodOf(18))   // Ar
        assertEquals(4, FieldRegistry.periodOf(36))   // Kr
        assertEquals(5, FieldRegistry.periodOf(54))   // Xe
        assertEquals(6, FieldRegistry.periodOf(86))   // Rn
        assertEquals(7, FieldRegistry.periodOf(118))  // Og
    }

    @Test
    fun groupsMatchThePeriodicTableColumns() {
        assertEquals(1, FieldRegistry.groupOf(1))     // H
        assertEquals(18, FieldRegistry.groupOf(2))    // He
        assertEquals(1, FieldRegistry.groupOf(3))     // Li
        assertEquals(2, FieldRegistry.groupOf(4))     // Be
        assertEquals(14, FieldRegistry.groupOf(6))    // C
        assertEquals(16, FieldRegistry.groupOf(8))    // O
        assertEquals(17, FieldRegistry.groupOf(17))   // Cl
        assertEquals(18, FieldRegistry.groupOf(18))   // Ar
        assertEquals(3, FieldRegistry.groupOf(21))    // Sc
        assertEquals(6, FieldRegistry.groupOf(24))    // Cr
        assertEquals(11, FieldRegistry.groupOf(29))   // Cu
        assertEquals(12, FieldRegistry.groupOf(30))   // Zn
        assertEquals(11, FieldRegistry.groupOf(47))   // Ag
        assertEquals(11, FieldRegistry.groupOf(79))   // Au
        assertEquals(12, FieldRegistry.groupOf(80))   // Hg
        assertEquals(14, FieldRegistry.groupOf(82))   // Pb
        assertEquals(18, FieldRegistry.groupOf(86))   // Rn
        assertEquals(4, FieldRegistry.groupOf(104))   // Rf
        assertEquals(18, FieldRegistry.groupOf(118))  // Og
    }

    @Test
    fun fBlockElementsHaveNoNumberedGroup() {
        assertNull(FieldRegistry.groupOf(58))   // Ce, lanthanoid
        assertNull(FieldRegistry.groupOf(64))   // Gd, lanthanoid
        assertNull(FieldRegistry.groupOf(92))   // U, actinide
        // Lu and Lr are conventionally placed in group 3.
        assertEquals(3, FieldRegistry.groupOf(71))
        assertEquals(3, FieldRegistry.groupOf(103))
    }
}
