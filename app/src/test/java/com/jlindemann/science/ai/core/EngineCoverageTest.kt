package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.FieldResolver
import com.jlindemann.science.ai.nlu.QueryPlanner
import com.jlindemann.science.ai.retrieval.EntityResolver
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The engine must claim every query shape whose legacy handler has been removed.
 *
 * `handleSuperlativeQuery`, `handleComparison`, `handleListQuery`, `handleSeriesQuery` and
 * `handleBlockQuery` were deleted once these all passed. A regression here means those questions
 * fall through to the generic element handler and answer worse than before, so this is the
 * safety net for that deletion.
 */
class EngineCoverageTest {

    private lateinit var store: KnowledgeStore
    private lateinit var planner: QueryPlanner
    private lateinit var executor: QueryExecutor

    @Before
    fun setUp() {
        assumeTrue("real assets not reachable", TestAssets.available())
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        val datasets = DatasetIndex.build()
        val aliases = EntityResolver.buildAliases(
            mapOf("en" to store.elements.associate {
                it.key to Triple(it.key, it.symbol, it.atomicNumber.toString())
            })
        )
        val strings = TestStrings()
        planner = QueryPlanner(store, datasets, EntityResolver(aliases, "en"), FieldResolver(strings), null)
        executor = QueryExecutor(store, datasets, null, strings)
    }

    /** Assert the engine plans and executes a query into one of the expected intents. */
    private fun claims(query: String, vararg expected: Intent) {
        val plan = planner.plan(query, DialogueState())
        assertTrue(
            "engine did not claim '$query' (intent=${plan.intent}, conf=${plan.confidence})",
            plan.intent in expected && plan.confidence >= planner.threshold
        )
        val result = executor.execute(plan)
        assertNotNull("'$query' planned as ${plan.intent} but produced no result", result)
        assertFalse(
            "'$query' produced an empty result",
            result is ExecutionResult.Empty
        )
    }

    @Test
    fun claimsSuperlativesInManyPhrasings() {
        for (query in listOf(
            "which element has the highest melting point",
            "which element has the lowest melting point",
            "what is the densest element",
            "what is the heaviest element",
            "what is the lightest element",
            "which element has the highest electronegativity",
            "which element has the lowest density",
            "hardest element",
            "element with the highest boiling point",
            "what element has the greatest atomic mass"
        )) claims(query, Intent.SUPERLATIVE, Intent.FILTER_LIST)
    }

    @Test
    fun claimsSuperlativesConstrainedToASubset() {
        for (query in listOf(
            "densest transition metal",
            "which noble gas has the highest boiling point",
            "lightest halogen",
            "which alkali metal has the lowest melting point",
            "heaviest lanthanide"
        )) claims(query, Intent.SUPERLATIVE, Intent.FILTER_LIST)
    }

    @Test
    fun claimsComparisons() {
        for (query in listOf(
            "compare gold and silver",
            "compare gold and silver density",
            "gold vs iron",
            "which is denser gold or lead",
            "compare hydrogen helium and lithium"
        )) claims(query, Intent.COMPARISON, Intent.FILTER_LIST, Intent.SUPERLATIVE)
    }

    @Test
    fun claimsSubsetLists() {
        for (query in listOf(
            "list the noble gases",
            "which elements are radioactive",
            "what elements are in the d block",
            "show me all the halogens",
            "list all alkali metals",
            "which elements are metalloids"
        )) claims(query, Intent.FILTER_LIST, Intent.AGGREGATE)
    }

    @Test
    fun claimsThresholdQueries() {
        for (query in listOf(
            "which elements melt above 3000 kelvin",
            "which metals have a density above 20",
            "elements with an electronegativity below 1",
            "which transition metals boil above 5000 kelvin"
        )) claims(query, Intent.FILTER_LIST)
    }

    @Test
    fun claimsAggregations() {
        for (query in listOf(
            "average density of the noble gases",
            "how many halogens are there",
            "how many elements are radioactive",
            "average atomic mass of the alkali metals"
        )) claims(query, Intent.AGGREGATE)
    }

    /**
     * Reactivity has no backing field — it is scored from group and position — so the engine must
     * decline every phrasing and let `handleReactivityQuery` answer.
     *
     * "which is the most reactive metal" is the dangerous one: without an explicit guard the
     * planner sees a metal subset plus a list question and would answer with a list of metals,
     * silently ignoring what was actually asked.
     */
    @Test
    fun defersEveryReactivityPhrasing() {
        for (query in listOf(
            "what is the most reactive element",
            "which is the most reactive metal",
            "least reactive element",
            "most reactive alkali metal",
            "compare the reactivity of sodium and gold",
            "vilket är det reaktivaste grundämnet"
        )) {
            val plan = planner.plan(query, DialogueState())
            assertEquals(
                "reactivity has no data field; the engine must defer for '$query'",
                Intent.UNKNOWN, plan.intent
            )
        }
    }

    @Test
    fun stillDefersOrdinaryLookupsAndSmallTalk() {
        // "who discovered oxygen" is deliberately absent: it resolves to the discovered_by field
        // and the engine now answers it, with a citation the old handler never produced.
        for (query in listOf(
            "tell me about gold", "what is gold", "hello", "give me a fact",
            "quiz me", "what is the molar mass of h2o"
        )) {
            val plan = planner.plan(query, DialogueState())
            assertTrue(
                "engine should not claim '$query' (intent=${plan.intent}, conf=${plan.confidence})",
                plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold
            )
        }
    }
}
