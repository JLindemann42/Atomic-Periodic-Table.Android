package com.jlindemann.science.ai.data

import com.jlindemann.science.ai.retrieval.Bm25Index
import com.jlindemann.science.ai.retrieval.EntityResolver
import com.jlindemann.science.ai.retrieval.HybridRetriever
import com.jlindemann.science.ai.retrieval.RetrievedRef
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the whole index against the 118 real elements, rather than fixtures.
 *
 * Fixture tests prove each rule in isolation; only this catches the case where real authored data
 * takes a path no fixture covers. That matters here because the element JSON was written by hand
 * over years and contains genuinely irregular values.
 */
class RealCorpusTest {

    private lateinit var store: KnowledgeStore

    @Before
    fun setUp() {
        assumeTrue("real assets not reachable from the test working directory", TestAssets.available())
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
    }

    @Test
    fun allElementsParse() {
        assertEquals(118, store.size)
        assertEquals(118, store.elements.map { it.key }.distinct().size)
    }

    @Test
    fun everyElementHasAnAtomicNumberSymbolAndSeries() {
        for (element in store.elements) {
            assertTrue("${element.key} has no atomic number", element.atomicNumber in 1..118)
            assertTrue("${element.key} has no symbol", element.symbol.isNotBlank())
            assertNotEquals("${element.key} fell through to an unknown series", SeriesId.UNKNOWN, element.series)
            assertNotEquals("${element.key} fell through to an unknown block", Block.UNKNOWN, element.block)
        }
    }

    @Test
    fun atomicNumbersAreTheCompleteRangeOneToOneEighteen() {
        assertEquals((1..118).toList(), store.elements.map { it.atomicNumber })
    }

    /**
     * The sentinel `"---"` — including unit-suffixed forms like `"--- (pm)"` — must never survive
     * parsing. The legacy agent printed these straight to users.
     */
    @Test
    fun noSentinelValueEverSurvivesAsData() {
        for (element in store.elements) {
            for ((fieldId, value) in element.values) {
                when (value) {
                    is FieldValue.Text -> assertFalse(
                        "${element.key}.$fieldId leaked a sentinel: '${value.raw}'",
                        ValueParser.isNullToken(value.raw)
                    )
                    is FieldValue.Enum -> assertFalse(
                        "${element.key}.$fieldId leaked a sentinel: '${value.localized}'",
                        ValueParser.isNullToken(value.localized)
                    )
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun coreNumericFieldsAreWidelyPopulatedAndFinite() {
        // These are the exact counts the shipped data supports, not aspirational floors: 19
        // elements carry no electronegativity, 16 no melting point on any scale, 20 no boiling
        // point. Pinning the real numbers means a parsing regression shows up as a drop.
        val minimum = mapOf(
            "density" to 105, "atomic_mass" to 118, "electronegativity" to 99,
            "melting_point" to 102, "boiling_point" to 98
        )
        for ((fieldId, floor) in minimum) {
            val values = store.elements.mapNotNull { it.quantity(fieldId)?.value }
            assertTrue("$fieldId parsed on only ${values.size} elements, expected >= $floor",
                values.size >= floor)
            assertTrue("$fieldId produced a non-finite value", values.all { it.isFinite() })
        }
    }

    @Test
    fun symbolsParseForEveryElementIncludingTheAmbiguousOnes() {
        assertEquals("Au", store.element("gold")!!.symbol)
        // "Na" must survive: it looks like an absent-value marker but is sodium's symbol.
        assertEquals("Na", store.element("sodium")!!.symbol)
        assertEquals("sodium", store.bySymbol("na")?.key)
        // "No" is nobelium, and equally must not be read as a boolean or a sentinel.
        assertEquals("No", store.element("nobelium")!!.symbol)
    }

    @Test
    fun knownValuesParseToTheRightNumbers() {
        val gold = store.element("gold")!!
        assertEquals(79, gold.atomicNumber)
        assertEquals("Au", gold.symbol)
        assertEquals(SeriesId.TRANSITION_METAL, gold.series)
        assertEquals(19.3, gold.quantity("density")!!.value, 0.05)
        assertEquals(1337.33, gold.quantity("melting_point")!!.value, 1.0)

        val tungsten = store.element("tungsten")!!
        assertEquals(74, tungsten.atomicNumber)
        assertEquals(3695.0, tungsten.quantity("melting_point")!!.value, 1.0)
    }

    /**
     * All melting points must land on one scale so they can be ranked against each other.
     *
     * This asserts the parser's job, not the chemistry, because two rows in `elements_en.json`
     * hold the wrong number: lanthanum records 3737 K and cerium 3716 K, which are their boiling
     * points (the real melting points are 1193 K and 1068 K). Both outrank tungsten, so a
     * "highest melting point" assertion would be testing a data error rather than the index.
     */
    @Test
    fun meltingPointsAreAllComparableOnTheKelvinScale() {
        val meltingPoints = store.elements.mapNotNull { e -> e.quantity("melting_point")?.let { e to it } }
        for ((element, quantity) in meltingPoints) {
            assertEquals("${element.key} melting point is not in Kelvin", "K", quantity.unit)
            assertTrue("${element.key} has a non-physical melting point", quantity.value > 0)
            assertTrue("${element.key} melting point is implausibly high", quantity.value < 6000)
        }
        // Tungsten is the highest correctly-recorded melting point in the data.
        val ranked = meltingPoints.sortedByDescending { it.second.value }.map { it.first.key }
        assertEquals("tungsten", ranked.first { it !in KNOWN_BAD_MELTING_POINTS })
    }

    private companion object {
        /** Rows whose melting point is really their boiling point. Tracked as a data defect. */
        val KNOWN_BAD_MELTING_POINTS = setOf("lanthanum", "cerium")
    }

    @Test
    fun superlativesOverTheRealDataAreCorrect() {
        val densities = store.elements.mapNotNull { e -> e.quantity("density")?.let { e to it.value } }
        for ((element, value) in densities) {
            assertEquals("${element.key} density is not in g/cm³", "g/cm³", element.quantity("density")!!.unit)
            assertTrue("${element.key} has a non-physical density", value > 0)
        }
        // Osmium is the densest measured element. Everything above it in this data is a
        // superheavy synthetic whose density is predicted rather than measured.
        val densestMeasured = densities.sortedByDescending { it.second }
            .map { it.first }
            .first { it.atomicNumber <= 100 }
        assertEquals("osmium", densestMeasured.key)

        val mostElectronegative = store.elements
            .mapNotNull { e -> e.quantity("electronegativity")?.let { e to it } }
            .maxByOrNull { it.second.value }!!.first
        assertEquals("fluorine", mostElectronegative.key)
    }

    @Test
    fun periodsAndGroupsCoverTheWholeTable() {
        assertEquals((1..7).toSet(), store.elements.map { it.period }.toSet())
        // Exactly the 28 f-block elements sit outside the numbered groups.
        assertEquals(28, store.elements.count { it.groupNumber == null })
    }

    @Test
    fun radioactiveElementsAreIdentified() {
        assertTrue(store.element("uranium")!!.radioactive)
        assertTrue(store.element("plutonium")!!.radioactive)
        assertFalse(store.element("gold")!!.radioactive)
        assertEquals(37, store.elements.count { it.radioactive })
    }

    @Test
    fun isotopesParseAcrossTheWholeTable() {
        // 12 superheavy synthetic elements carry "--" for iso_1, so 106 is full coverage here.
        val withIsotopes = store.elements.count { it.isotopes.isNotEmpty() }
        assertEquals(106, withIsotopes)
        // Half-lives that parsed must be positive and finite.
        for (element in store.elements) {
            for (isotope in element.isotopes) {
                isotope.halfLifeSeconds?.let {
                    assertTrue("${element.key}/${isotope.name} has a bad half-life: $it", it > 0 && it.isFinite())
                }
            }
        }
        // 69 elements flag at least one stable isotope. Gold is not among them: its block runs
        // Au-170..Au-209 and never marks Au-197 stable, which is a data gap rather than a
        // parsing one, so the assertion targets the elements that do carry the flag.
        assertEquals(69, store.elements.count { e -> e.isotopes.any { it.stable } })
        assertTrue(store.element("aluminium")!!.isotopes.any { it.stable })
        assertEquals(40, store.element("gold")!!.isotopes.size)
    }

    @Test
    fun rangesOnlyAppearOnFieldsThatAllowThem() {
        val allowed = FieldRegistry.ALL.filter { it.allowsRange }.map { it.id }.toSet()
        for (element in store.elements) {
            for ((fieldId, value) in element.values) {
                val quantity = value.asQuantity() ?: continue
                if (!quantity.isRange) continue
                val baseId = fieldId.substringBefore('#').substringBefore('@')
                assertTrue("${element.key}.$fieldId parsed as a range but is not a range field",
                    baseId in allowed)
            }
        }
    }

    @Test
    fun coverageReportsAreConsistentWithTheData() {
        for ((fieldId, count) in store.coverage) {
            val actual = store.elements.count { !it.value(fieldId).isMissing }
            assertEquals("coverage mismatch for $fieldId", actual, count)
            assertTrue(count in 0..118)
        }
        // A sparse field must be reported as sparse. Vickers hardness is authored on 72 elements
        // but 27 of those hold a sentinel, so only 45 carry a real value — exactly the
        // distinction that lets the agent say "available for 45 of 118" instead of guessing.
        assertEquals(45, store.coverageOf("vickers_hardness"))
        assertEquals(118, store.coverageOf("atomic_number"))
    }

    @Test
    fun bankedIonizationEnergiesAreAddressableBySlot() {
        val gold = store.element("gold")!!
        assertNotNull(gold.values["ionization_energy#1"])
        assertEquals(gold.values["ionization_energy#1"], gold.values["ionization_energy"])
        // Hydrogen has exactly one ionization energy, so slot 2 must be absent rather than wrong.
        assertNull(store.element("hydrogen")!!.values["ionization_energy#2"])
    }

    @Test
    fun retrievalOverTheRealCorpusFindsTheObviousAnswers() {
        val datasets = DatasetIndex.build()
        val corpus = HybridRetriever.buildCorpus(store, datasets, null)
        assertTrue("corpus should hold every element plus every dataset row",
            corpus.size >= store.size + 400)

        val index = Bm25Index(corpus)
        val aliases = EntityResolver.buildAliases(
            mapOf("en" to store.elements.associate {
                it.key to Triple(it.key, it.symbol, it.atomicNumber.toString())
            })
        )
        val retriever = HybridRetriever(store, datasets, index, EntityResolver(aliases, "en"), null)

        val gold = retriever.best("what is the density of gold")
        assertTrue("expected gold, got ${gold?.ref}", (gold?.ref as? RetrievedRef.Element)?.key == "gold")

        // A query naming no element should still reach the right dataset row.
        val dictionary = retriever.best("what does electronegativity mean")
        assertNotNull("a concept question should retrieve something", dictionary)
    }

    @Test
    fun everyShippedLanguageParsesAndOverlays() {
        val languages = TestAssets.availableLanguages()
        assertEquals(12, languages.size)
        for (language in languages) {
            val table = TestAssets.elementTable(language)
            assertEquals("$language has the wrong element count", 118, table.size)
            assertEquals("$language has different keys than English", store.byKey.keys.size, table.size)
            for (element in store.elements) {
                val name = table[element.key]?.get("element")?.toString()
                assertFalse("$language is missing a name for ${element.key}", name.isNullOrBlank())
            }
        }
    }
}
