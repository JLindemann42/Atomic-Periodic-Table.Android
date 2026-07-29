package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.compose.AnswerComposer
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.data.Tier
import com.jlindemann.science.ai.exec.QueryExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The agent must not be a way around the app's paywall.
 *
 * Every field asserted here is one the element screens already show as a lock. If the agent answers
 * it in full, a free user gets PRO data by typing a question instead of scrolling — and the leak is
 * wider than a single value, because a superlative over a gated field is a ranking of the whole
 * gated column and an average is that column summarised.
 *
 * The other half matters just as much: free content must stay free. A test that only checked the
 * locking would be satisfied by an agent that refused everything.
 */
class EntitlementTest {

    private lateinit var store: KnowledgeStore
    private lateinit var datasets: DatasetIndex
    private lateinit var strings: TestStrings

    private val free = Entitlements(isPro = false, isProPlus = false)
    private val pro = Entitlements(isPro = true, isProPlus = false)
    private val proPlus = Entitlements(isPro = true, isProPlus = true)

    @Before
    fun setUp() {
        assumeTrue("real assets not reachable", TestAssets.available())
        assumeTrue("real strings not reachable", TestStrings.available)
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        datasets = DatasetIndex.build()
        strings = TestStrings()
    }

    private fun executor(entitlements: Entitlements) =
        QueryExecutor(store, datasets, null, strings, entitlements)

    private fun plan(intent: Intent, field: String?, element: String? = "gold", sort: String? = null) =
        QueryPlan(
            intent = intent,
            entities = listOfNotNull(element?.let { EntityRef.Element(it) }),
            fieldIds = listOfNotNull(field),
            sortField = sort,
            confidence = 1.0
        )

    /** The exact set the element screen locks. Drifting from it in either direction is a bug. */
    private val proFields = listOf(
        "speed_of_sound_solid", "speed_of_sound_liquid", "speed_of_sound_gas",
        "poisson_ratio", "bulk_modulus", "young_modulus", "shear_modulus",
        "mohs_hardness", "vickers_hardness", "brinell_hardness",
        "electron_affinity", "curie_point", "neel_point",
        "space_group_name", "space_group_number", "refractive_index"
    )

    @Test
    fun theRegistryGatesExactlyWhatTheElementScreenGates() {
        for (id in proFields) {
            assertEquals("$id should be PRO", Tier.PRO, FieldRegistry.byId[id]?.tier)
        }
        // Spot-check the other direction: common fields must stay free.
        for (id in listOf("density", "melting_point", "atomic_mass", "electronegativity", "symbol")) {
            assertEquals("$id must stay free", Tier.FREE, FieldRegistry.byId[id]?.tier)
        }
    }

    @Test
    fun aFreeUserIsRefusedAGatedPropertyAndTheValueNeverReachesTheText() {
        val composer = AnswerComposer(store, null, strings)
        for (id in proFields) {
            val request = plan(Intent.PROPERTY_LOOKUP, id)
            val result = executor(free).execute(request)
            assertTrue("$id leaked to a free user: $result", result is ExecutionResult.Locked)

            val text = composer.compose(result!!, request).text
            val actual = store.element("gold")?.value(id)?.asQuantity()?.display
            if (!actual.isNullOrBlank()) {
                assertFalse("$id printed its value anyway: $text", text.contains(actual))
            }
        }
    }

    /**
     * The leak that is easy to miss.
     *
     * "Which element has the highest Young's modulus" never names a gated field in its answer — it
     * names an element — but it is a ranking computed over the entire gated column, so it has to be
     * refused too. Same for an average.
     */
    @Test
    fun aFreeUserCannotRankOrAverageOverAGatedField() {
        for (intent in listOf(Intent.SUPERLATIVE, Intent.FILTER_LIST, Intent.AGGREGATE)) {
            val request = plan(intent, "young_modulus", element = null, sort = "young_modulus")
            val result = executor(free).execute(request)
            assertTrue("$intent leaked a ranking over a gated field", result is ExecutionResult.Locked)
        }
    }

    @Test
    fun aFreeUserIsRefusedTheHazardRatingsAndCompoundAnalysis() {
        assertTrue(
            "NFPA ratings are PRO on the element screen",
            executor(free).execute(plan(Intent.SAFETY, null)) is ExecutionResult.Locked
        )
        val formula = QueryPlan(intent = Intent.FORMULA_MASS, rawQuery = "H2SO4", confidence = 1.0)
        assertTrue(
            "compound analysis is PRO+ in the legacy handlers",
            executor(free).execute(formula) is ExecutionResult.Locked
        )
        assertTrue(
            "PRO alone does not unlock a PRO+ feature",
            executor(pro).execute(formula) is ExecutionResult.Locked
        )
    }

    @Test
    fun freeContentStaysFree() {
        for (id in listOf("density", "melting_point", "atomic_mass", "electronegativity", "symbol")) {
            val result = executor(free).execute(plan(Intent.PROPERTY_LOOKUP, id))
            assertFalse("$id was withheld from a free user", result is ExecutionResult.Locked)
            assertTrue("$id produced no answer at all", result != null)
        }
    }

    /**
     * A family holding both free and gated fields answers with the free ones.
     *
     * Locking the whole family would withhold more than the app does — the Mechanical section still
     * shows its free rows to everyone.
     */
    @Test
    fun aMixedCategoryAnswersWithWhateverIsFree() {
        val mechanical = FieldRegistry.byCategory(com.jlindemann.science.ai.data.FieldCategory.MECHANICAL)
            .map { it.id }
        val request = QueryPlan(
            intent = Intent.CATEGORY_LOOKUP,
            entities = listOf(EntityRef.Element("iron")),
            fieldIds = mechanical,
            confidence = 1.0
        )
        val result = executor(free).execute(request)
        if (result is ExecutionResult.Comparison) {
            assertTrue(
                "a gated field appeared in a free category answer: ${result.fieldIds}",
                result.fieldIds.none { FieldRegistry.byId[it]?.tier != Tier.FREE }
            )
        } else {
            // Every mechanical field is gated for this element, so locking the family is correct.
            assertTrue(result is ExecutionResult.Locked)
        }
    }

    @Test
    fun aSubscriberSeesEverything() {
        for (id in proFields) {
            assertFalse(
                "$id was withheld from a PRO user",
                executor(pro).execute(plan(Intent.PROPERTY_LOOKUP, id)) is ExecutionResult.Locked
            )
        }
        val formula = QueryPlan(intent = Intent.FORMULA_MASS, rawQuery = "H2SO4", confidence = 1.0)
        assertFalse(
            "compound analysis was withheld from a PRO+ user",
            executor(proPlus).execute(formula) is ExecutionResult.Locked
        )
    }

    /** A gated field asked for in another unit, or by bank slot, is still gated. */
    @Test
    fun unitAndSlotSuffixesDoNotBypassTheGate() {
        assertEquals(Tier.PRO, free.tierOf("young_modulus"))
        assertEquals(Tier.PRO, free.tierOf("curie_point@K"))
        assertFalse(free.allowsField("poisson_ratio"))
        assertTrue(free.allowsField("density"))
    }

    @Test
    fun aWithheldAnswerOffersTheUpgrade() {
        val composer = AnswerComposer(store, null, strings)
        val request = plan(Intent.PROPERTY_LOOKUP, "young_modulus")
        val result = executor(free).execute(request)!!
        val composed = composer.compose(result, request)
        assertTrue("no upgrade chip on a locked answer", composed.actions.isNotEmpty())
        assertTrue(
            "the upgrade chip should go to the PRO page",
            composed.actions.any { it.target == com.jlindemann.science.ai.data.DeepLinkTarget.PRO_PAGE }
        )
        // It should say what is locked, not pretend the value does not exist.
        assertFalse(
            "a locked answer must not claim the data is missing",
            composed.text.contains("don't have", ignoreCase = true)
        )
    }
}
