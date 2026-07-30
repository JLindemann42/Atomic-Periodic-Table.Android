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
        // Names from every shipped language, matching how the app builds the resolver: a user
        // may write an element's name in their own language whatever the chat language is.
        val aliases = EntityResolver.buildAliases(
            TestAssets.availableLanguages().associateWith { lang ->
                TestAssets.elementTable(lang).rows.mapValues { (_, row) ->
                    Triple(
                        row["element"]?.toString().orEmpty(),
                        row["short"]?.toString().orEmpty(),
                        row["element_atomic_number"]?.toString().orEmpty()
                    )
                }
            }
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
            "compare hydrogen helium and lithium"
        )) claims(query, Intent.COMPARISON, Intent.FILTER_LIST, Intent.SUPERLATIVE)

        // "which is denser, X or Y" asks for a winner, not a table, so it takes the more
        // direct COMPARATIVE route. See RichAnswerTest.
        claims("which is denser gold or lead", Intent.COMPARATIVE)
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

    /**
     * "group 11" and "period 6" used to be served by a structural handler that was never wired
     * into the router. The engine answers them now, in the languages that handler listed.
     */
    @Test
    fun claimsGroupAndPeriodQueries() {
        for (query in listOf(
            "which elements are in group 11",
            "what elements are in period 6",
            "list the elements in group 17",
            "vilka grundämnen finns i grupp 11",
            "welche elemente sind in gruppe 11"
        )) claims(query, Intent.FILTER_LIST, Intent.AGGREGATE)
    }

    @Test
    fun groupAndPeriodFiltersSelectTheRightElements() {
        val plan = planner.plan("which elements are in group 11", DialogueState())
        val list = executor.execute(plan) as ExecutionResult.ElementList
        val keys = list.results.map { it.element.key }
        assertTrue("group 11 should contain copper, silver and gold, got $keys",
            keys.containsAll(listOf("copper", "silver", "gold")))

        val periodPlan = planner.plan("what elements are in period 2", DialogueState())
        val periodList = executor.execute(periodPlan) as ExecutionResult.ElementList
        assertEquals(8, periodList.matched)
    }

    /**
     * Reactions and similarity are chemistry judgements with no backing field, so the engine must
     * decline them. Reactions are the risky case: "sodium and chlorine react" names two elements
     * and would otherwise be answered as a property comparison.
     */
    @Test
    fun defersReactionAndSimilarityToTheirOwnHandlers() {
        for (query in listOf(
            "what happens when sodium and chlorine react",
            "what happens if I mix sodium and water",
            "which element is similar to gold",
            "what is similar to carbon",
            "vad händer när natrium och klor reagerar"
        )) {
            val plan = planner.plan(query, DialogueState())
            assertEquals(
                "'$query' has no backing field; the engine must defer",
                Intent.UNKNOWN, plan.intent
            )
        }
    }

    /**
     * Isotopes and safety are checked before field resolution, because "isotopes" and
     * "half life" both resolve to a field label — answering with the common-neutron count
     * instead of the isotope list, which is what happened before this intent existed.
     */
    @Test
    fun claimsIsotopeAndSafetyQuestions() {
        for (query in listOf(
            "what are the isotopes of gold",
            "half life of uranium",
            "how many isotopes does tin have",
            "vilka isotoper har guld"
        )) claims(query, Intent.ISOTOPES)

        for (query in listOf(
            "is gold dangerous",
            "what is the nfpa rating of chlorine",
            "is mercury toxic",
            "är kvicksilver farligt"
        )) claims(query, Intent.SAFETY)
    }

    @Test
    fun isotopeAnswersAreOrderedAndComplete() {
        val plan = planner.plan("what are the isotopes of gold", DialogueState())
        val result = executor.execute(plan) as ExecutionResult.Isotopes
        assertEquals(40, result.total)
        assertTrue("should show at most the plan limit", result.shown.size <= plan.limit)
        // Stable first, then longest-lived.
        val decaying = result.shown.filterNot { it.stable }.mapNotNull { it.halfLifeSeconds }
        assertEquals(decaying.sortedDescending(), decaying)
    }

    @Test
    fun safetyReportsRadioactivityEvenWithoutAnNfpaDiamond() {
        val plan = planner.plan("is plutonium dangerous", DialogueState())
        val result = executor.execute(plan)
        assertTrue("plutonium is radioactive and that must be said", when (result) {
            is ExecutionResult.Safety -> result.radioactive
            else -> false
        })
    }

    /** A possessive splits into a bare letter that matches an element symbol. */
    @Test
    fun possessivesDoNotResolveAsElementSymbols() {
        val plan = planner.plan("what is gold's cas number", DialogueState())
        assertEquals(
            "the 's' in \"gold's\" must not resolve to sulfur",
            listOf("gold"), plan.elementKeys
        )
        assertEquals(Intent.PROPERTY_LOOKUP, plan.intent)
    }

    @Test
    fun stillDefersOrdinaryLookupsAndSmallTalk() {
        // Deliberately absent: "who discovered oxygen" resolves to the discovered_by field, and
        // "the molar mass of h2o" is now computed by FORMULA_MASS. The engine answers both, with
        // citations the old handlers never produced.
        for (query in listOf(
            "tell me about gold", "what is gold", "hello", "give me a fact", "quiz me"
        )) {
            val plan = planner.plan(query, DialogueState())
            assertTrue(
                "engine should not claim '$query' (intent=${plan.intent}, conf=${plan.confidence})",
                plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold
            )
        }
    }
}
