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
 * Questions with a direct answer, and the context that makes a bare figure mean something.
 *
 * A number on its own is not much of an answer: "19.3 g/cm³" only lands if you already know the
 * range it sits in. And "is gold denser than lead" deserves a yes, not a two-column table.
 */
class RichAnswerTest {

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

    private fun answer(q: String): Pair<QueryPlan, String> {
        val plan = planner.plan(q, DialogueState())
        val r = if (plan.intent == Intent.UNKNOWN) null else executor.execute(plan)
        return plan to (r?.let { composer.compose(it, plan).text } ?: "")
    }

    // ---- Rich context on a plain lookup ----------------------------------------------------

    @Test fun propertyAnswersSayWhereTheValueRanks() {
        val (_, text) = answer("what is the density of gold")
        assertTrue(text.contains("19.3"))
        assertTrue("a bare figure needs context", text.contains("ranks") || text.contains("highest"))
        assertTrue("should say how many elements it ranked against", text.contains("105"))
    }

    @Test fun theTopValueIsCalledOutAsTheHighest() {
        val (_, text) = answer("what is the electronegativity of fluorine")
        assertTrue("fluorine is the most electronegative element", text.contains("highest"))
    }

    /** Ranking an atomic number or a discovery year would be noise, not context. */
    @Test fun fieldsWhereRankingIsMeaninglessGetNoRank() {
        for (q in listOf("what is the atomic number of gold", "when was gold discovered")) {
            val (_, text) = answer(q)
            assertFalse("'$q' should not be ranked: $text", text.contains("ranks"))
        }
    }

    // ---- Yes/no ------------------------------------------------------------------------------

    @Test fun yesNoQuestionsGetAYesOrNo() {
        val (planYes, yes) = answer("is gold denser than lead")
        assertEquals(Intent.COMPARATIVE, planYes.intent)
        assertTrue("gold is denser than lead", yes.startsWith("**Yes."))

        val (_, no) = answer("is aluminium denser than gold")
        assertTrue("aluminium is not denser than gold", no.startsWith("**No"))
        assertTrue("a correction should still give the figures", no.contains("19.3"))
    }

    @Test fun theAdjectiveSuppliesThePropertyWhenNoneIsNamed() {
        val (plan, _) = answer("is gold heavier than silver")
        assertEquals("'heavier' means atomic mass", listOf("atomic_mass"), plan.fieldIds)
        val (densityPlan, _) = answer("is gold denser than silver")
        assertEquals("'denser' means density", listOf("density"), densityPlan.fieldIds)
    }

    @Test fun directionIsRespectedForNegativeAdjectives() {
        val (_, text) = answer("is aluminium lighter than gold")
        assertTrue("aluminium is less dense, so the claim holds", text.startsWith("**Yes."))
    }

    // ---- Which of two, and by how much --------------------------------------------------------

    @Test fun whichOfTwoNamesTheWinnerFirst() {
        val (plan, text) = answer("which is heavier gold or silver")
        assertEquals(Intent.COMPARATIVE, plan.intent)
        assertTrue("the winner should lead the answer", text.startsWith("**Gold."))
    }

    @Test fun byHowMuchGivesTheRatio() {
        val (plan, text) = answer("how much denser is gold than aluminium")
        assertEquals(Intent.COMPARATIVE, plan.intent)
        // 19.3 / 2.70 is about 7.15
        assertTrue("expected a ratio, got: $text", text.contains("7.15") || text.contains("7.1"))
        assertTrue(text.contains("times"))
    }

    // ---- Neighbours ---------------------------------------------------------------------------

    @Test fun neighbourQuestionsNameTheElementAndPlaceIt() {
        val (plan, after) = answer("what comes after carbon in the periodic table")
        assertEquals(Intent.NEIGHBOUR, plan.intent)
        assertTrue(after.contains("Nitrogen"))
        assertTrue("should give the atomic number", after.contains("7"))
        assertTrue("should place the element", after.contains("period"))

        val (_, before) = answer("what comes before carbon")
        assertTrue(before.contains("Boron"))
    }

    // ---- Facts about the table itself ------------------------------------------------------------

    @Test fun countsAllElements() {
        val (plan, text) = answer("how many elements are there")
        assertEquals(Aggregation.COUNT, plan.aggregation)
        assertTrue(text.contains("118"))
    }

    @Test fun mostAbundantInTheUniverseIsHydrogen() {
        val (_, text) = answer("what is the most abundant element in the universe")
        assertTrue("expected hydrogen, got: ${text.take(120)}", text.contains("Hydrogen"))
    }

    @Test fun booleanPropertyQuestionsAnswerFromTheEnum() {
        val (_, text) = answer("does iron conduct electricity")
        assertTrue(text.contains("Conductor"))
    }

    // ---- Guard rails ------------------------------------------------------------------------------

    @Test fun ordinaryComparisonsAreStillTables() {
        val (plan, _) = answer("compare gold and silver")
        assertEquals("no comparative framing, so the table is right", Intent.COMPARISON, plan.intent)
    }

    @Test fun comparativesNeedTwoElements() {
        val plan = planner.plan("is gold dense", DialogueState())
        assertNotEquals(Intent.COMPARATIVE, plan.intent)
    }
}
