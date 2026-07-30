package com.jlindemann.science.ai.core

import com.jlindemann.science.R
import com.jlindemann.science.ai.data.FieldCategory
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.FieldValue
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.data.UnitConverter
import com.jlindemann.science.ai.exec.QueryExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The agent has to state the unit for an abundance figure, and state the right one.
 *
 * The element JSON stores most reservoirs as bare numbers — `"earth_crust": "80500"`,
 * `"sea_water": "0.011"` — so "gold's abundance in sea water is 0.011" was a number with no
 * meaning attached. Two reservoirs write their own unit into the value instead, which is the
 * other half of the problem: append unconditionally and `"8400 mg/kg"` becomes
 * `"8400 mg/kg mg/kg"`, which is what the element screen shipped for years.
 *
 * The units themselves are not interchangeable, which is why this asserts the specific unit per
 * reservoir rather than merely that one is present: sea water is µg/l, and the sun and solar
 * system are atom counts against silicon, not concentrations at all.
 */
class AbundanceUnitsTest {

    private lateinit var store: KnowledgeStore
    private lateinit var strings: TestStrings
    private lateinit var executor: QueryExecutor

    @Before
    fun setUp() {
        assumeTrue("real assets not reachable", TestAssets.available())
        assumeTrue("real strings not reachable", TestStrings.available)
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        strings = TestStrings()
        executor = QueryExecutor(store, com.jlindemann.science.ai.data.DatasetIndex.build(), null, strings)
    }

    private fun element(key: String) = requireNotNull(store.element(key)) { "no element $key" }

    /** What the agent would print for one element's reading of one reservoir. */
    private fun rendered(elementKey: String, fieldId: String): String {
        val plan = QueryPlan(
            intent = Intent.PROPERTY_LOOKUP,
            entities = listOf(EntityRef.Element(elementKey)),
            fieldIds = listOf(fieldId),
            confidence = 1.0
        )
        val result = executor.execute(plan)
        assertTrue("$elementKey/$fieldId produced $result", result is ExecutionResult.Property)
        return (result as ExecutionResult.Property).display
    }

    // ---- The registry ---------------------------------------------------------------------

    /**
     * Every reservoir must be able to state a unit, or the answer is a bare number again.
     *
     * This is the guard on adding a tenth reservoir: a new abundance field that forgets its unit
     * fails here rather than silently answering "0.011".
     */
    @Test
    fun everyAbundanceFieldDeclaresAUnitItCanShow() {
        val abundance = FieldRegistry.byCategory(FieldCategory.ABUNDANCE)
        assertEquals("the app records nine reservoirs", 9, abundance.size)
        for (spec in abundance) {
            assertTrue("${spec.id} does not opt into surfacing its unit", spec.surfaceUnit)
            val labelRes = spec.unitLabelRes
            assertNotNull("${spec.id} declares no unit label", labelRes)
            val label = strings.get(labelRes!!)
            assertFalse("${spec.id}'s unit label does not resolve: $label", label.startsWith("str:"))
        }
    }

    /**
     * The three units the reservoirs actually use, held apart.
     *
     * They used to share `mg/kg`, which is what let the agent label a µg/l sea-water figure and a
     * silicon-relative solar figure as concentrations.
     */
    @Test
    fun theReservoirsDoNotAllShareOneUnit() {
        fun unitOf(id: String) = FieldRegistry.byId.getValue(id).canonicalUnit
        assertEquals("mg/kg", unitOf("abundance_earth_crust"))
        assertEquals("mg/kg", unitOf("abundance_crustal_rocks"))
        assertEquals(FieldRegistry.UNIT_MICROGRAM_PER_LITRE, unitOf("abundance_sea_water"))
        assertEquals(FieldRegistry.UNIT_ATOMS_PER_SILICON, unitOf("abundance_sun"))
        assertEquals(FieldRegistry.UNIT_ATOMS_PER_SILICON, unitOf("abundance_solar_system"))
        assertEquals(
            "sea water and the crust must not be one dimension away from each other",
            3,
            setOf(
                unitOf("abundance_earth_crust"),
                unitOf("abundance_sea_water"),
                unitOf("abundance_sun")
            ).size
        )
    }

    /**
     * µg/l and the silicon reference must not gain a conversion factor by accident.
     *
     * Mass per volume does not become mass per mass without a density, and an atom count against
     * silicon is not a concentration at all. A wrong factor here would be far worse than no
     * conversion: it would answer confidently with a number that is off by orders of magnitude.
     */
    @Test
    fun theOutlierUnitsRefuseToConvertToConcentration() {
        assertEquals(
            null,
            UnitConverter.convert(1.0, FieldRegistry.UNIT_MICROGRAM_PER_LITRE, "mg/kg")
        )
        assertEquals(
            null,
            UnitConverter.convert(1.0, FieldRegistry.UNIT_ATOMS_PER_SILICON, "mg/kg")
        )
        // But the spelling still normalises, so grouping and comparison agree on one token.
        assertEquals("µg/l", UnitConverter.canonical("μg/l"))
        assertEquals("µg/l", UnitConverter.canonical("ug/l"))
    }

    // ---- Rendered answers -----------------------------------------------------------------

    /** A bare number in the source comes back with the reservoir's unit attached. */
    @Test
    fun bareReadingsGainTheirUnit() {
        assertTrue(
            "sea water must be stated in µg/l",
            rendered("gold", "abundance_sea_water").endsWith(strings.get(R.string.ai_unit_ug_per_l))
        )
        assertTrue(
            "crustal rock must be stated in mg/kg",
            rendered("oxygen", "abundance_crustal_rocks")
                .endsWith(strings.get(R.string.ai_unit_mg_per_kg))
        )
        assertTrue(
            "the sun is counted against silicon, not as a concentration",
            rendered("helium", "abundance_sun").endsWith(strings.get(R.string.ai_unit_atoms_per_si))
        )
    }

    /**
     * A reading that already carries its unit is left alone.
     *
     * `meteorites` is authored as `"8400 mg/kg"` throughout, so appending the registry's unit on
     * top would double it — the defect this whole change exists to remove.
     */
    @Test
    fun authoredUnitsAreNeverDoubled() {
        val unit = strings.get(R.string.ai_unit_mg_per_kg)
        for (record in store.elements) {
            val value = record.value("abundance_meteorites") as? FieldValue.Num ?: continue
            if (!value.quantity.unitAuthored) continue
            val display = rendered(record.key, "abundance_meteorites")
            assertEquals(
                "${record.key} had its unit appended on top of an authored one: $display",
                value.quantity.display,
                display
            )
            assertFalse("$display ends with an appended $unit", display.endsWith(" $unit"))
        }
    }

    /**
     * Human-body figures are authored as either mg/kg or a percentage, per element.
     *
     * Whichever it is, it is already inside the value, so nothing may be added to it.
     */
    @Test
    fun humanBodyReadingsKeepTheirOwnUnit() {
        val oxygen = element("oxygen").value("abundance_human_body")
        assumeTrue("oxygen has no human-body reading", oxygen is FieldValue.Num)
        val quantity = (oxygen as FieldValue.Num).quantity
        assertTrue("oxygen's share of the body is authored as a percentage", quantity.unitAuthored)
        assertEquals(quantity.display, rendered("oxygen", "abundance_human_body"))
    }

    /**
     * "Trace" means present but unquantified.
     *
     * It used to render as `ai_abundance_relative` — "(relative to H=10¹²)" — so an element found
     * in trace amounts in the body was answered with a solar-abundance unit note under a
     * convention this app does not even use.
     */
    @Test
    fun traceReadsAsTraceRatherThanASolarUnitNote() {
        val traced = store.elements.firstOrNull { it.value("abundance_human_body") is FieldValue.Trace }
        assumeTrue("no element records a trace reading", traced != null)
        val display = rendered(traced!!.key, "abundance_human_body")
        assertEquals(strings.get(R.string.ai_value_trace), display)
        assertFalse(
            "a trace reading must not borrow the H=10¹² note",
            display.contains(strings.get(R.string.ai_abundance_relative))
        )
    }

    /** Everything outside the abundance family still prints exactly as the source authored it. */
    @Test
    fun otherFieldsAreLeftVerbatim() {
        for (id in listOf("density", "melting_point", "atomic_mass", "resistivity")) {
            val spec = FieldRegistry.byId.getValue(id)
            assertFalse("$id must not have opted into unit surfacing", spec.surfaceUnit)
            val value = element("gold").value(id) as? FieldValue.Num ?: continue
            assertEquals(value.quantity.display, rendered("gold", id))
        }
    }
}
