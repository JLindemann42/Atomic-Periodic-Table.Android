package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.compose.AnswerComposer
import com.jlindemann.science.ai.data.*
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.*
import com.jlindemann.science.ai.retrieval.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Questions that combine several conditions, or that refer to one element to constrain another.
 *
 * Each of these previously produced a confident but wrong answer — an unfiltered list, the first
 * ranked element instead of the third, or an inverted set — rather than declining.
 */
class ComplexQueryTest {

    private lateinit var store: KnowledgeStore
    private lateinit var planner: QueryPlanner
    private lateinit var executor: QueryExecutor
    private lateinit var composer: AnswerComposer

    @Before fun setUp() {
        assumeTrue(TestAssets.available()); assumeTrue(TestStrings.available)
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        val ds = DatasetIndex.build()
        val al = EntityResolver.buildAliases(mapOf("en" to store.elements.associate {
            it.key to Triple(it.key, it.symbol, it.atomicNumber.toString()) }))
        val er = EntityResolver(al, "en")
        val s = TestStrings()
        val idx = Bm25Index(HybridRetriever.buildCorpus(store, ds, null))
        planner = QueryPlanner(store, ds, er, FieldResolver(s), HybridRetriever(store, ds, idx, er, null))
        executor = QueryExecutor(store, ds, null, s)
        composer = AnswerComposer(store, null, s)
    }

    private fun run(q: String): Pair<QueryPlan, ExecutionResult?> {
        val plan = planner.plan(q, DialogueState())
        return plan to (if (plan.intent == Intent.UNKNOWN) null else executor.execute(plan))
    }

    private fun text(q: String): String {
        val (plan, r) = run(q)
        return r?.let { composer.compose(it, plan).text } ?: ""
    }

    // ---- A threshold given as another element -------------------------------------------------

    @Test fun comparesAgainstAnotherElementsValue() {
        val (plan, result) = run("which transition metals are denser than iron")
        assertEquals(Intent.FILTER_LIST, plan.intent)
        val ironDensity = store.element("iron")!!.quantity("density")!!.mid
        val list = result as ExecutionResult.ElementList
        for (row in list.results) {
            assertEquals(SeriesId.TRANSITION_METAL, row.element.series)
            assertTrue("${row.element.key} is not denser than iron", row.quantity!!.mid > ironDensity)
        }
        // Iron itself supplies the bound; it is not one of the answers.
        assertTrue(list.results.none { it.element.key == "iron" })
        assertTrue("should exclude the ones below iron", list.matched < store.inSeries(SeriesId.TRANSITION_METAL).size)
    }

    @Test fun lessThanAnElementWorksToo() {
        val (_, result) = run("which metals are lighter than aluminium")
        val aluminium = store.element("aluminium")!!.quantity("density")!!.mid
        val list = result as ExecutionResult.ElementList
        assertTrue(list.results.isNotEmpty())
        for (row in list.results) assertTrue(row.quantity!!.mid < aluminium)
    }

    // ---- Ranges ---------------------------------------------------------------------------------

    @Test fun filtersOnARangeOfValues() {
        val (plan, result) = run("which elements melt between 1000 and 2000 kelvin")
        assertEquals(Intent.FILTER_LIST, plan.intent)
        val list = result as ExecutionResult.ElementList
        assertTrue(list.results.isNotEmpty())
        for (row in list.results) {
            val kelvin = store.quantityIn(row.element, "melting_point", "K")!!.mid
            assertTrue("${row.element.key} melts at $kelvin K", kelvin in 1000.0..2000.0)
        }
    }

    // ---- Ordinal rank ------------------------------------------------------------------------------

    @Test fun answersTheNthRankedElement() {
        val byDensity = store.elements
            .mapNotNull { e -> e.quantity("density")?.let { e to it.mid } }
            .sortedByDescending { it.second }
        val (plan, result) = run("what is the third densest element")
        assertEquals(Intent.SUPERLATIVE, plan.intent)
        val list = result as ExecutionResult.ElementList
        assertEquals(2, list.rankOffset)
        assertEquals(byDensity[2].first.key, list.results.first().element.key)
    }

    @Test fun theNthAnswerSaysWhichPlaceItIs() {
        val answer = text("what is the third densest element")
        assertTrue("must say it is the third, or it looks like a wrong answer", answer.contains("3"))
        assertTrue(answer.contains("rd"))
    }

    // ---- Same group or period --------------------------------------------------------------------

    @Test fun findsElementsSharingAGroup() {
        val (plan, result) = run("which elements are in the same group as carbon")
        assertEquals(Intent.FILTER_LIST, plan.intent)
        val list = result as ExecutionResult.ElementList
        val carbonGroup = store.element("carbon")!!.groupNumber
        assertTrue(list.results.all { it.element.groupNumber == carbonGroup })
        assertTrue(list.results.any { it.element.key == "silicon" })
    }

    @Test fun findsElementsSharingAPeriod() {
        val (_, result) = run("what else is in the same period as iron")
        val list = result as ExecutionResult.ElementList
        assertTrue(list.results.all { it.element.period == 4 })
        assertEquals(18, list.matched)
    }

    // ---- Negation ------------------------------------------------------------------------------------

    @Test fun negationInvertsOnlyTheNegatedCondition() {
        val (_, result) = run("which noble gases are not radioactive")
        val list = result as ExecutionResult.ElementList
        assertTrue("the series must survive the negation",
            list.results.all { it.element.series == SeriesId.NOBLE_GAS })
        assertTrue("only the radioactivity is inverted",
            list.results.none { it.element.radioactive })
        assertTrue(list.results.any { it.element.key == "helium" })
    }

    // ---- Richer answers --------------------------------------------------------------------------------

    @Test fun superlativesNameTheRunnerUp() {
        val answer = text("which element has the highest melting point")
        assertTrue("a winner means more with something to compare against", answer.contains("Next is"))
    }

    @Test fun listsDescribeTheirSpread() {
        val answer = text("top 5 densest elements")
        assertTrue("a list should say the span it covers", answer.contains("Values run from"))
    }
}
