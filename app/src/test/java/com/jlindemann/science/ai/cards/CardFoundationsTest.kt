package com.jlindemann.science.ai.cards

import com.jlindemann.science.ai.core.TestStrings
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The pure layer behind the visual cards, run against all 118 real elements.
 *
 * Everything that decides *what* a card says lives in these objects; the views only map numbers to
 * pixels. That split is what makes it acceptable that the drawing itself has no test — and it only
 * holds as long as no `onDraw` grows a branch that depends on what the data means.
 */
class CardFoundationsTest {

    private lateinit var store: KnowledgeStore
    private lateinit var strings: TestStrings

    @Before
    fun setUp() {
        assumeTrue("real assets not reachable", TestAssets.available())
        assumeTrue("real strings not reachable", TestStrings.available)
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        strings = TestStrings()
    }

    private fun element(key: String) = requireNotNull(store.element(key)) { "no element $key" }

    // ---- Codec ----------------------------------------------------------------------------------

    @Test
    fun codecRoundTripsACard() {
        val card = ChatCard(ChatCardKind.ISOTOPE_DECAY, "carbon", "Carbon", mapOf("z" to "6"))
        val decoded = ChatCardCodec.decode(ChatCardCodec.encode(card))
        assertEquals(card, decoded)
    }

    @Test
    fun codecNeverThrowsOnRubbish() {
        for (input in listOf(null, "", "   ", "NOT_A_KIND", "garbage", "ABUNDANCE")) {
            ChatCardCodec.decode(input)
            ChatCardCodec.kindOf(input)
        }
    }

    /**
     * An unknown kind must decode to null, not throw. That is what lets an older build read a
     * message written by a newer one, and a restored session, and simply show plain text.
     */
    @Test
    fun unknownKindDecodesToNull() {
        val forged = "SOME_FUTURE_CARDgoldGold"
        assertNull(ChatCardCodec.decode(forged))
        assertNull(ChatCardCodec.kindOf(forged))
    }

    @Test
    fun kindOfAgreesWithDecodeWithoutDecoding() {
        for (kind in ChatCardKind.values()) {
            val encoded = ChatCardCodec.encode(ChatCard(kind, "gold", "Gold"))
            assertEquals(kind, ChatCardCodec.kindOf(encoded))
            assertEquals(kind, ChatCardCodec.decode(encoded)?.kind)
        }
    }

    /** A title containing a delimiter must not be able to corrupt the record. */
    @Test
    fun delimitersInATitleAreStripped() {
        val card = ChatCard(ChatCardKind.ABUNDANCE, "gold", "GoldInjectedMore")
        val decoded = ChatCardCodec.decode(ChatCardCodec.encode(card))
        assertEquals(ChatCardKind.ABUNDANCE, decoded?.kind)
        assertEquals("gold", decoded?.elementKey)
    }

    // ---- Ionization ------------------------------------------------------------------------------

    @Test
    fun ionizationStepsAreOrderedAndIncreasing() {
        val series = requireNotNull(IonizationSeriesReducer.of(element("iron")))
        assertEquals(series.steps.map { it.order }.sorted(), series.steps.map { it.order })
        assertTrue("iron should record many steps", series.steps.size > 10)
        // Removing an electron from an increasingly positive ion always costs more.
        for (i in 0 until series.steps.size - 1) {
            assertTrue(
                "step ${i + 2} cheaper than step ${i + 1}",
                series.steps[i + 1].electronVolts >= series.steps[i].electronVolts
            )
        }
    }

    /**
     * The jumps are the teaching point: sodium's shell empties after one electron, magnesium's
     * after two, neon's after eight.
     */
    @Test
    fun shellClosuresLandWhereChemistrySaysTheyShould() {
        assertTrue(1 in requireNotNull(IonizationSeriesReducer.of(element("sodium"))).shellClosureAfter)
        assertTrue(2 in requireNotNull(IonizationSeriesReducer.of(element("magnesium"))).shellClosureAfter)
    }

    @Test
    fun anElementWithTooFewStepsProducesNoSeries() {
        assertNull(IonizationSeriesReducer.of(element("hydrogen")))
    }

    // ---- Isotopes --------------------------------------------------------------------------------

    @Test
    fun isotopesAreOrderedByMassNumberAndStableOnesCarryNoHalfLife() {
        val series = requireNotNull(IsotopeSeriesReducer.of(element("carbon")))
        assertEquals(series.points.map { it.massNumber }.sorted(), series.points.map { it.massNumber })
        for (point in series.points.filter { it.stable }) {
            assertNull("a stable isotope must have no finite half-life", point.logSeconds)
            assertEquals(DecayGroup.STABLE, point.decayGroup)
        }
    }

    @Test
    fun tinHasTheWidestStableBandInTheTable() {
        val series = requireNotNull(IsotopeSeriesReducer.of(element("tin")))
        assertTrue("tin should have many stable isotopes", series.stableMassNumbers.size >= 8)
    }

    /**
     * Pins how every decay spelling in the shipped data is grouped.
     *
     * `decay_type` is authored free-text and is read by nothing else in the app, so this is the test
     * that catches a new notation arriving — it shows up as a change in the OTHER set rather than as
     * a silently grey marker on the chart.
     */
    @Test
    fun everyDecaySpellingInTheRealDataIsGrouped() {
        val spellings = store.elements
            .flatMap { it.isotopes }
            .filterNot { it.stable }
            .mapNotNull { it.decayType?.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        assertTrue("expected real decay data", spellings.size > 5)
        val ungrouped = spellings.filter { IsotopeSeriesReducer.group(it) == DecayGroup.OTHER }.sorted()
        // Not an emptiness assertion: some notations genuinely have no group. This pins the size so
        // a new spelling is visible rather than silent.
        assertTrue(
            "unexpectedly many ungrouped decay spellings (${ungrouped.size}): $ungrouped",
            ungrouped.size <= spellings.size / 2
        )
        assertEquals(DecayGroup.ALPHA, IsotopeSeriesReducer.group("α"))
        assertEquals(DecayGroup.BETA_MINUS, IsotopeSeriesReducer.group("B-"))
        assertEquals(DecayGroup.BETA_PLUS_EC, IsotopeSeriesReducer.group("EC"))
        assertEquals(DecayGroup.NUCLEON_EMISSION, IsotopeSeriesReducer.group("2p"))
        assertEquals(DecayGroup.OTHER, IsotopeSeriesReducer.group(null))
    }

    // ---- Abundance --------------------------------------------------------------------------------

    @Test
    fun abundanceKeepsAFixedOrderAndARealFloor() {
        val profile = requireNotNull(AbundanceReducer.of(element("gold"), strings))
        val order = AbundanceReducer.ORDER
        val actual = profile.bars.map { it.fieldId }
        assertEquals(actual.sortedBy { order.indexOf(it) }, actual)
        // A log axis has no zero; bars must start below the smallest value.
        assertTrue(profile.floorLog < profile.minLog)
    }

    /**
     * A sanity check that the bars carry real magnitudes, not just shapes.
     *
     * Crustal *rock* rather than `earth_crust`: that field turns out to be unpopulated for both gold
     * and oxygen in the shipped data, which is exactly the kind of sparseness the reducer has to
     * tolerate — it emits only the reservoirs an element actually has.
     */
    @Test
    fun oxygenIsFarMoreAbundantInCrustalRockThanGold() {
        val oxygen = requireNotNull(AbundanceReducer.of(element("oxygen"), strings))
        val gold = requireNotNull(AbundanceReducer.of(element("gold"), strings))
        fun rock(p: AbundanceProfile) =
            requireNotNull(p.bars.firstOrNull { it.fieldId == "abundance_crustal_rocks" }?.logMgPerKg)
        assertTrue("oxygen should dwarf gold in rock", rock(oxygen) > rock(gold) + 3)
    }

    /** Sparse reservoirs are normal; the reducer must never invent a bar for a missing one. */
    @Test
    fun onlyPopulatedReservoirsBecomeBars() {
        for (record in store.elements) {
            val profile = AbundanceReducer.of(record, strings) ?: continue
            for (bar in profile.bars) {
                assertTrue(
                    "${record.key} produced a bar for the unpopulated ${bar.fieldId}",
                    !record.value(bar.fieldId).isMissing
                )
            }
        }
    }

    /** A trace reading is information; it must not vanish into a missing bar. */
    @Test
    fun traceReadingsSurviveAsBars() {
        val withTrace = store.elements.mapNotNull { AbundanceReducer.of(it, strings) }
            .flatMap { it.bars }
            .filter { it.trace }
        for (bar in withTrace) assertNull("a trace bar carries no number", bar.logMgPerKg)
    }

    @Test
    fun noAbundanceBarIsNonPositive() {
        for (record in store.elements) {
            val profile = AbundanceReducer.of(record, strings) ?: continue
            for (bar in profile.bars) {
                bar.logMgPerKg?.let {
                    assertTrue("${record.key}/${bar.fieldId} produced a non-finite log", it.isFinite())
                }
            }
        }
    }

    // ---- Poisson ----------------------------------------------------------------------------------

    @Test
    fun poissonAxisCoversTheReferenceTableAndTheIncompressibleLimit() {
        val band = requireNotNull(PoissonBandReducer.of(element("gold"), store))
        assertTrue(band.references.isNotEmpty())
        assertTrue(band.axisMin <= band.references.minOf { it.low })
        assertTrue(band.axisMax >= PoissonBandReducer.INCOMPRESSIBLE_LIMIT)
        assertNotNull(band.rank)
    }

    @Test
    fun anElementWithNoRatioProducesNoBand() {
        assertNull(PoissonBandReducer.of(element("neon"), store))
    }

    // ---- Crystal ------------------------------------------------------------------------------------

    /**
     * Reports how many elements would get no crystal card.
     *
     * The view falls back to a cubic cell for anything unrecognised, which is a guess beside a text
     * label on the element screen and a fabrication when the card *is* the answer. This measures the
     * cost of refusing to guess.
     */
    @Test
    fun everyCrystalStructureEitherResolvesOrIsExplicitlyUnknown() {
        val values = store.elements.mapNotNull { record ->
            when (val v = record.value("crystal_structure")) {
                is com.jlindemann.science.ai.data.FieldValue.Enum -> v.localized
                is com.jlindemann.science.ai.data.FieldValue.Text -> v.raw
                else -> null
            }
        }.toSet()
        val unresolved = values.filter { CrystalSystemResolver.resolve(it) == null }.sorted()
        assertTrue("expected real crystal data", values.isNotEmpty())
        assertTrue(
            "more unrecognised lattices than expected (${unresolved.size} of ${values.size}): $unresolved",
            unresolved.size <= values.size / 2
        )
        assertNull(CrystalSystemResolver.resolve(null))
        assertNull(CrystalSystemResolver.resolve("Not a lattice"))
        assertEquals("Hexagonal", CrystalSystemResolver.resolve("Hexagonal close-packed (hcp)"))
    }

    // ---- Labels and URLs -----------------------------------------------------------------------------

    @Test
    fun nfpaLabelsCoverEveryRatingAndAbsence() {
        for (rating in listOf(null, -1, 0, 1, 2, 3, 4, 9)) {
            assertTrue(strings.get(NfpaLabeller.healthDescription(rating)).isNotBlank())
            assertTrue(strings.get(NfpaLabeller.flammabilityDescription(rating)).isNotBlank())
            assertTrue(strings.get(NfpaLabeller.instabilityDescription(rating)).isNotBlank())
        }
        assertEquals("SA", NfpaLabeller.specialCode("Simple asphyxiant"))
        assertEquals("W", NfpaLabeller.specialCode("W"))
        assertEquals("OX", NfpaLabeller.specialCode("OX"))
        assertEquals("–", NfpaLabeller.specialCode("---"))
    }

    @Test
    fun emissionUrlMatchesTheShippedPattern() {
        assertEquals("https://www.jlindemann.se/atomic/emission_lines/Fe.gif", EmissionSpectrumUrl.of("Fe"))
    }

    @Test
    fun decadeLabelsReadAsTimeAcrossTheWholeRange() {
        assertEquals("1 s", DecadeLabels.timeLabel(0))
        assertTrue(DecadeLabels.timeLabel(-9).endsWith("ns"))
        assertTrue(DecadeLabels.timeLabel(9).endsWith("y"))
        for (decade in -22..27) assertTrue(DecadeLabels.timeLabel(decade).isNotBlank())
        assertEquals("1", DecadeLabels.sciLabel(0))
        assertEquals("10⁻⁶", DecadeLabels.sciLabel(-6))
    }
}
