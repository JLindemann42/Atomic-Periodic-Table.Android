package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.compose.AnswerComposer
import com.jlindemann.science.ai.data.*
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.*
import com.jlindemann.science.ai.retrieval.EntityResolver
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class IsotopeComparisonTest {

    private lateinit var planner: QueryPlanner
    private lateinit var executor: QueryExecutor
    private lateinit var composer: AnswerComposer

    @Before fun setUp() {
        assumeTrue(TestAssets.available()); assumeTrue(TestStrings.available)
        KnowledgeStore.clear()
        val store = KnowledgeStore.build(TestAssets.elementTable("en"))
        val datasets = DatasetIndex.build()
        val aliases = EntityResolver.buildAliases(mapOf("en" to store.elements.associate {
            it.key to Triple(it.key, it.symbol, it.atomicNumber.toString()) }))
        val s = TestStrings()
        planner = QueryPlanner(store, datasets, EntityResolver(aliases, "en"), FieldResolver(s), null)
        executor = QueryExecutor(store, datasets, null, s)
        composer = AnswerComposer(store, null, s)
    }

    private fun answer(q: String): Pair<QueryPlan, String> {
        val plan = planner.plan(q, DialogueState())
        val result = executor.execute(plan)
        return plan to (result?.let { composer.compose(it, plan).text } ?: "")
    }

    @Test fun comparesTwoIsotopesOfTheSameElement() {
        val (plan, text) = answer("uranium-235 vs uranium-238")
        assertEquals(Intent.ISOTOPE_COMPARISON, plan.intent)
        // Same element: protons are shared, neutrons are the difference.
        assertTrue("should name both nuclides", text.contains("Uranium-235") && text.contains("Uranium-238"))
        assertTrue("should state the shared proton count", text.contains("92"))
        assertTrue("235 has 143 neutrons", text.contains("143"))
        assertTrue("238 has 146 neutrons", text.contains("146"))
        assertTrue("should state the difference", text.contains("difference of 3"))
    }

    @Test fun comparesIsotopesAcrossDifferentElements() {
        val (plan, text) = answer("carbon-14 vs nitrogen-14")
        assertEquals(Intent.ISOTOPE_COMPARISON, plan.intent)
        assertTrue(text.contains("Carbon-14"))
        assertTrue(text.contains("Nitrogen-14"))
        // Different elements: proton counts differ and must both be shown.
        assertTrue(text.contains("6"))
        assertTrue(text.contains("7"))
    }

    @Test fun acceptsSymbolNotation() {
        val (plan, _) = answer("U-235 vs U-238")
        assertEquals(Intent.ISOTOPE_COMPARISON, plan.intent)
    }

    @Test fun reportsHalfLifeAndDecayWhenListed() {
        val (_, text) = answer("uranium-235 vs uranium-238")
        assertTrue("half-life row expected", text.contains("Half-life"))
    }

    @Test fun admitsWhenANuclideIsNotInTheTable() {
        // A physically valid but unlisted nuclide must say so rather than invent a half-life.
        val (plan, text) = answer("carbon-12 vs carbon-99")
        assertEquals(Intent.ISOTOPE_COMPARISON, plan.intent)
        assertTrue(text.contains("not listed"))
    }

    @Test fun rejectsImpossibleNuclides() {
        // Mass number below the proton count cannot exist, so this must not be claimed.
        val plan = planner.plan("carbon-2 vs carbon-3", DialogueState())
        assertNotEquals(Intent.ISOTOPE_COMPARISON, plan.intent)
    }

    @Test fun singleNuclideStillGoesToTheNeutronCount() {
        val (plan, _) = answer("how many neutrons in uranium-238")
        assertEquals(Intent.NUCLIDE_COUNT, plan.intent)
    }
}
