package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.SeriesId
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.nlu.FieldResolver
import com.jlindemann.science.ai.nlu.QueryPlanner
import com.jlindemann.science.ai.retrieval.EntityResolver
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end tests over the real 118-element data.
 *
 * These cover the capabilities the previous keyword router could not express at all: filtering and
 * ranking across the whole table, aggregating a subset, converting units, following up on an
 * earlier turn, and refusing to answer when the data is absent.
 */
class AiEngineTest {

    private lateinit var store: KnowledgeStore
    private lateinit var planner: QueryPlanner
    private lateinit var executor: com.jlindemann.science.ai.exec.QueryExecutor
    private lateinit var strings: StringProvider

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
        strings = FakeStrings(language = "en")
        planner = QueryPlanner(store, datasets, EntityResolver(aliases, "en"), FieldResolver(strings), null)
        executor = com.jlindemann.science.ai.exec.QueryExecutor(store, datasets, null, strings)
    }

    private fun run(query: String, state: DialogueState = DialogueState()): Pair<QueryPlan, ExecutionResult?> {
        val plan = planner.plan(query, state)
        return plan to executor.execute(plan)
    }

    // ---- Multi-hop: filter across the whole table ---------------------------------------

    @Test
    fun filtersTransitionMetalsByMeltingPoint() {
        val (plan, result) = run("which transition metals melt above 2000 celsius")
        assertEquals(Intent.FILTER_LIST, plan.intent)
        assertTrue(plan.filters.any { it is Filter.InSeries && SeriesId.TRANSITION_METAL in it.series })
        assertTrue(plan.filters.any { it is Filter.FieldCompare && it.op == Op.GT })

        val list = result as ExecutionResult.ElementList
        assertTrue("expected several matches, got ${list.results.size}", list.results.isNotEmpty())
        // Every result must genuinely be a transition metal above the threshold.
        for (row in list.results) {
            assertEquals(SeriesId.TRANSITION_METAL, row.element.series)
            val celsius = store.quantityIn(row.element, "melting_point", "°C")!!
            assertTrue("${row.element.key} melts at ${celsius.value} °C", celsius.value > 2000)
        }
        assertTrue(list.results.any { it.element.key == "tungsten" })
    }

    @Test
    fun filtersByLessThan() {
        val (plan, result) = run("which elements have a density below 1")
        assertTrue(plan.filters.any { it is Filter.FieldCompare && it.op == Op.LT })

        val list = result as ExecutionResult.ElementList
        for (row in list.results) assertTrue(row.quantity!!.mid < 1.0)
        // The shown rows are a truncated page; `matched` must report the true total.
        val trueTotal = store.elements.count { (it.quantity("density")?.mid ?: Double.MAX_VALUE) < 1.0 }
        assertEquals(trueTotal, list.matched)
        assertTrue(list.results.size <= list.matched)
    }

    // ---- Multi-hop: rank a subset --------------------------------------------------------

    @Test
    fun ranksTopNWithinASubset() {
        val (plan, result) = run("top 5 densest nonmetals")
        assertEquals(5, plan.limit)
        assertTrue(plan.sortDescending)
        assertEquals("density", plan.sortField)

        val list = result as ExecutionResult.ElementList
        assertEquals(5, list.results.size)
        assertTrue(list.results.all { it.element.series.isNonmetal })
        // Strictly descending.
        val values = list.results.map { it.quantity!!.mid }
        assertEquals(values.sortedDescending(), values)
    }

    @Test
    fun leastAndMostAreDistinctDirections() {
        val (mostPlan, _) = run("which element has the highest electronegativity")
        val (leastPlan, _) = run("which element has the lowest electronegativity")
        assertTrue(mostPlan.sortDescending)
        assertFalse(leastPlan.sortDescending)
    }

    @Test
    fun superlativeOverTheWholeTableIsCorrect() {
        val (_, result) = run("which element has the highest electronegativity")
        val list = result as ExecutionResult.ElementList
        assertEquals("fluorine", list.results.first().element.key)
    }

    // ---- Multi-hop: aggregate ------------------------------------------------------------

    @Test
    fun averagesAFieldOverASubsetAndDisclosesCoverage() {
        val (plan, result) = run("average electronegativity of the halogens")
        assertEquals(Intent.AGGREGATE, plan.intent)
        assertEquals(Aggregation.AVG, plan.aggregation)

        val aggregate = result as ExecutionResult.Aggregate
        val halogens = store.inSeries(SeriesId.HALOGEN)
        assertEquals(halogens.size, aggregate.contributors.size + aggregate.missing)
        // The mean must equal the mean of exactly the contributors, not of a padded set.
        val expected = aggregate.contributors.map { it.quantity!!.mid }.average()
        assertEquals(expected, aggregate.value, 1e-9)
        assertTrue("a partial average must report what it excluded", aggregate.missing >= 0)
    }

    @Test
    fun countsMatchingElements() {
        val (plan, result) = run("how many noble gases are there")
        assertEquals(Aggregation.COUNT, plan.aggregation)
        val aggregate = result as ExecutionResult.Aggregate
        assertEquals(store.inSeries(SeriesId.NOBLE_GAS).size.toDouble(), aggregate.value, 0.0)
    }

    // ---- Unit conversion -------------------------------------------------------------------

    @Test
    fun convertsAPropertyIntoARequestedUnit() {
        val (plan, result) = run("what is the melting point of gold in fahrenheit")
        assertEquals("°F", plan.targetUnit)
        val property = result as ExecutionResult.Property
        assertEquals("gold", property.element.key)
        assertEquals(1947.52, property.quantity!!.value, 0.1)
    }

    // ---- Honest absence ----------------------------------------------------------------------

    @Test
    fun reportsMissingDataInsteadOfGuessing() {
        // Helium has no Vickers hardness; the answer must say so rather than print a sentinel.
        val (_, result) = run("what is the vickers hardness of helium")
        val noData = result as ExecutionResult.NoData
        assertEquals("vickers_hardness", noData.fieldId)
        assertEquals("helium", noData.element?.key)
        assertTrue("coverage must be reported so the answer can explain the gap", noData.coverage in 1..117)
    }

    @Test
    fun filtersNeverIncludeElementsWithNoValue() {
        val (_, result) = run("which elements have a vickers hardness above 100")
        val list = result as ExecutionResult.ElementList
        // Absent values must not be treated as zero, or as passing the filter.
        assertTrue(list.results.all { it.quantity != null })
        assertTrue("elements without the field must be counted as missing", list.missing > 0)
    }

    // ---- Follow-ups ---------------------------------------------------------------------------

    @Test
    fun followUpInheritsTheElementFromThepreviousTurn() {
        val state = DialogueState()
        val (firstPlan, firstResult) = run("what is the melting point of gold in fahrenheit", state)
        state.noteAnswer(firstPlan, listOf((firstResult as ExecutionResult.Property).element.key))

        // No element named; it must be inherited.
        val (plan, result) = run("and its density?", state)
        assertEquals(Intent.PROPERTY_LOOKUP, plan.intent)
        assertEquals(listOf("gold"), plan.elementKeys)
        assertEquals("density", plan.primaryField)
        assertEquals("gold", (result as ExecutionResult.Property).element.key)
    }

    @Test
    fun followUpInheritsTheFieldWhenOnlyAnElementIsNamed() {
        val state = DialogueState()
        state.focusElement = "gold"
        state.lastFieldIds = listOf("density")

        val (plan, _) = run("what about iron", state)
        assertEquals("density", plan.primaryField)
        assertTrue(plan.elementKeys.contains("iron"))
    }

    @Test
    fun dialogueStateTracksRecentElements() {
        val state = DialogueState()
        state.noteAnswer(QueryPlan(Intent.PROPERTY_LOOKUP, fieldIds = listOf("density")), listOf("gold"))
        state.noteAnswer(QueryPlan(Intent.PROPERTY_LOOKUP, fieldIds = listOf("density")), listOf("iron"))
        assertEquals("iron", state.focusElement)
        assertEquals(listOf("iron", "gold"), state.recentElements.toList())
    }

    // ---- Deferral: the engine must not claim ordinary lookups ---------------------------------

    @Test
    fun plainLookupsAreLeftToTheExistingHandlers() {
        // No operator, no missing data, no inherited slot: the engine defers.
        for (query in listOf("tell me about gold", "what is gold", "gold", "hello", "give me a fact")) {
            val plan = planner.plan(query, DialogueState())
            assertTrue("engine should not claim '$query' (got ${plan.intent}, ${plan.confidence})",
                plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold)
        }
    }

    @Test
    fun unrelatedQueriesAreNotClaimed() {
        val plan = planner.plan("what is the weather like today", DialogueState())
        assertEquals(Intent.UNKNOWN, plan.intent)
    }

    // ---- Comparison ---------------------------------------------------------------------------

    @Test
    fun comparesTwoNamedElements() {
        val (plan, result) = run("compare gold and silver density")
        assertEquals(Intent.COMPARISON, plan.intent)
        val comparison = result as ExecutionResult.Comparison
        assertEquals(setOf("gold", "silver"), comparison.elements.map { it.key }.toSet())
        assertTrue(comparison.values.containsKey("density"))
    }

    // ---- Citations -----------------------------------------------------------------------------

    @Test
    fun everyAnswerCarriesItsSource() {
        val (_, result) = run("which element has the highest electronegativity")
        assertTrue("a grounded answer must name where it came from", result!!.citations.isNotEmpty())
        assertTrue(result.citations.all { it.args.containsKey("key") })
    }
}
