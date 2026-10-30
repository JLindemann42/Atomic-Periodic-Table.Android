package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Exercises the index end to end on a small fixture. No Context and no `org.json` are involved,
 * which is the whole point of the [ElementSource] boundary.
 */
class KnowledgeStoreTest {

    private lateinit var store: KnowledgeStore

    private fun element(
        name: String, symbol: String, z: String, group: String, block: String,
        vararg extra: Pair<String, Any?>
    ): ElementRow = mapOf(
        "element" to name, "short" to symbol, "element_atomic_number" to z,
        "element_group" to group, "element_block" to block, *extra
    )

    @Before
    fun setUp() {
        KnowledgeStore.clear()
        store = KnowledgeStore.build(
            ElementTable(
                mapOf(
                    "hydrogen" to element(
                        "Hydrogen", "H", "1", "Reactive Nonmetal", "s - block",
                        "element_density" to "0.00008988 (g/cm^3)",
                        "element_melting_kelvin" to "14.01 (K)",
                        "element_melting_celsius" to "-259.14 (°C)",
                        "element_phase" to "Gas", "radioactive" to "No",
                        "element_electronegativty" to "2.20",
                        "vickers_hardness" to "---"
                    ),
                    "gold" to element(
                        "Gold", "Au", "79", "Transition Metals", "d - block",
                        "element_density" to "19.3 (g/cm^3)",
                        "element_melting_kelvin" to "1337.33 (K)",
                        "element_melting_celsius" to "1064.18 (°C)",
                        "element_phase" to "Solid", "radioactive" to "No",
                        "element_electronegativty" to "2.54",
                        "vickers_hardness" to "188-216 (MPa)",
                        "health" to 1, "flammability" to 0, "instability" to 0,
                        "iso_1" to "Gold-197", "iso_half_1" to "Stable", "iso_A_1" to "197"
                    ),
                    "uranium" to element(
                        "Uranium", "U", "92", "Actinide", "f - block",
                        "element_density" to "19.1 (g/cm^3)",
                        "element_melting_kelvin" to "1405.3 (K)",
                        "element_phase" to "Solid", "radioactive" to "Yes",
                        "vickers_hardness" to "--- (MPa)"
                    )
                )
            )
        )
    }

    @Test
    fun elementsAreSortedByAtomicNumber() {
        assertEquals(listOf("hydrogen", "gold", "uranium"), store.elements.map { it.key })
    }

    @Test
    fun lookupBySymbolNameAndNumber() {
        assertEquals("gold", store.element("gold")?.key)
        assertEquals("gold", store.element("GOLD")?.key)
        assertEquals("gold", store.bySymbol("au")?.key)
        assertEquals("gold", store.byNumber(79)?.key)
        assertNull(store.byNumber(200))
    }

    @Test
    fun periodAndGroupAreDerivedFromAtomicNumber() {
        val gold = store.element("gold")!!
        assertEquals(6, gold.period)
        assertEquals(11, gold.groupNumber)
        // Uranium is an actinide, which sits outside the numbered groups.
        assertNull(store.element("uranium")!!.groupNumber)
    }

    @Test
    fun classificationIsCanonicalised() {
        assertEquals(SeriesId.TRANSITION_METAL, store.element("gold")!!.series)
        assertEquals(SeriesId.ACTINIDE, store.element("uranium")!!.series)
        assertEquals(SeriesId.REACTIVE_NONMETAL, store.element("hydrogen")!!.series)
        assertEquals(Block.D, store.element("gold")!!.block)
        assertTrue(store.element("uranium")!!.radioactive)
        assertFalse(store.element("gold")!!.radioactive)
    }

    @Test
    fun numericValuesAreTypedAndComparable() {
        val densities = store.elements.mapNotNull { it.quantity("density")?.value }
        assertEquals(3, densities.size)
        assertEquals(19.3, store.element("gold")!!.quantity("density")!!.value, 1e-9)
        assertEquals("g/cm³", store.element("gold")!!.quantity("density")!!.unit)
        assertEquals("gold", store.elements.maxByOrNull { it.quantity("density")!!.value }!!.key)
    }

    @Test
    fun missingValuesAreMissingRegardlessOfSentinelShape() {
        // Bare "---" and unit-suffixed "--- (MPa)" must both be absent.
        assertTrue(store.element("hydrogen")!!.value("vickers_hardness").isMissing)
        assertTrue(store.element("uranium")!!.value("vickers_hardness").isMissing)
        assertFalse(store.element("gold")!!.value("vickers_hardness").isMissing)
    }

    @Test
    fun coverageCountsOnlyElementsThatActuallyHaveTheField() {
        assertEquals(3, store.coverageOf("density"))
        assertEquals(1, store.coverageOf("vickers_hardness"))
        assertEquals(0, store.coverageOf("curie_point"))
    }

    @Test
    fun storedTemperatureScalesAreQueryableWithoutConverting() {
        val gold = store.element("gold")!!
        assertEquals(1337.33, store.quantityIn(gold, "melting_point", "K")!!.value, 1e-6)
        assertEquals(1064.18, store.quantityIn(gold, "melting_point", "°C")!!.value, 1e-6)
        // Fahrenheit is not stored for this fixture, so it converts instead.
        assertEquals(1947.52, store.quantityIn(gold, "melting_point", "°F")!!.value, 0.01)
    }

    @Test
    fun rangeValuesRankByMidpointAndStayFlaggedAsRanges() {
        val q = store.element("gold")!!.quantity("vickers_hardness")!!
        assertTrue(q.isRange)
        assertEquals(188.0, q.value, 0.0)
        assertEquals(216.0, q.high!!, 0.0)
        assertEquals(202.0, q.mid, 0.0)
    }

    @Test
    fun nfpaAndIsotopesArePopulated() {
        val gold = store.element("gold")!!
        assertEquals(1, gold.nfpa?.health)
        assertEquals(0, gold.nfpa?.flammability)
        assertEquals(1, gold.isotopes.size)
        assertTrue(gold.isotopes[0].stable)
        assertEquals(1, gold.stableIsotopeCount)
        // Elements with no NFPA data get null rather than a hollow record.
        assertNull(store.element("uranium")!!.nfpa)
    }

    @Test
    fun seriesFilteringUsesTheCanonicalClassification() {
        assertEquals(listOf("gold"), store.inSeries(SeriesId.TRANSITION_METAL).map { it.key })
        assertEquals(listOf("uranium"), store.inSeries(SeriesId.ACTINIDE).map { it.key })
        assertTrue(store.inSeries(SeriesId.NOBLE_GAS).isEmpty())
    }

    @Test
    fun sharedInstanceIsBuiltOnceAndClearable() {
        KnowledgeStore.clear()
        val source = MapElementSource(mapOf("en" to ElementTable(mapOf("gold" to element("Gold", "Au", "79", "Transition Metals", "d - block")))))
        val first = KnowledgeStore.get(source)
        val second = KnowledgeStore.get(source)
        assertSame(first, second)
        KnowledgeStore.clear()
        assertNotSame(first, KnowledgeStore.get(source))
    }

    @Test
    fun localizedOverlayCarriesOnlyTheSevenTranslatedFields() {
        KnowledgeStore.clear()
        val source = MapElementSource(
            mapOf(
                "sv" to ElementTable(
                    mapOf(
                        "gold" to mapOf(
                            "element" to "Guld", "description" to "Guld är ett grundämne.",
                            "element_group" to "Övergångsmetaller", "element_phase" to "Fast"
                        )
                    )
                )
            )
        )
        val view = KnowledgeStore.overlay(source, "sv")!!
        assertEquals("Guld", view.name("gold"))
        assertEquals("sv", view.language)
    }
}
