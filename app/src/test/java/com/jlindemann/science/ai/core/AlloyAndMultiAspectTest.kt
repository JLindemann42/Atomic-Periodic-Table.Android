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

class AlloyAndMultiAspectTest {

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
        val r = executor.execute(plan)
        return plan to (r?.let { composer.compose(it, plan).text } ?: "")
    }

    // ---- Alloys ---------------------------------------------------------------------------

    @Test fun explainsTheDifferenceBetweenAnElementAndItsAlloy() {
        val (plan, text) = answer("what is the difference between iron and steel")
        assertEquals(Intent.DATASET_LOOKUP, plan.intent)
        assertTrue("should answer from the steel entry", text.contains("Steel"))
        assertTrue("should explain the carbon content", text.contains("carbon", ignoreCase = true))
    }

    @Test fun answersAlloyLookups() {
        for ((q, expected) in listOf(
            "what is brass" to "Brass",
            "what is bronze made of" to "Bronze",
            "tell me about stainless steel" to "Stainless steel"
        )) {
            val (plan, text) = answer(q)
            assertEquals("'$q' should resolve an alloy", Intent.DATASET_LOOKUP, plan.intent)
            assertTrue("'$q' should mention $expected", text.contains(expected, ignoreCase = true))
        }
    }

    /** The longest alloy name must win, or "stainless steel" answers as plain steel. */
    @Test fun longerAlloyNamesTakePrecedence() {
        val (_, text) = answer("what is stainless steel")
        assertTrue(text.contains("chromium", ignoreCase = true))
    }

    @Test fun alloysDoNotHijackOrdinaryElementQuestions() {
        val (plan, _) = answer("what is the density of iron")
        assertEquals(Intent.PROPERTY_LOOKUP, plan.intent)
    }

    // ---- Multi-aspect comparison ------------------------------------------------------------

    @Test fun comparesSeveralElementsOnSeveralNamedAspects() {
        val (plan, text) = answer(
            "compare lithium sodium and potassium in terms of reactivity density and atomic mass"
        )
        assertEquals(Intent.COMPARISON, plan.intent)
        assertEquals(3, plan.elementKeys.size)
        assertTrue("density should be compared", plan.fieldIds.contains("density"))
        assertTrue("atomic mass should be compared", plan.fieldIds.contains("atomic_mass"))
        assertTrue(text.contains("Lithium") && text.contains("Sodium") && text.contains("Potassium"))
    }

    /**
     * Reactivity and applications are not stored fields. The answer must say so rather than
     * quietly answering only the part it could.
     */
    @Test fun namesTheAspectsItCannotCompare() {
        val (_, text) = answer(
            "compare lithium sodium and potassium in terms of reactivity density and applications"
        )
        assertTrue("must disclose what it could not compare", text.contains("can't compare"))
        assertTrue(text.contains("reactivity") || text.contains("applications"))
    }

    @Test fun plainComparisonsAreUnaffected() {
        val (plan, text) = answer("compare gold and silver")
        assertEquals(Intent.COMPARISON, plan.intent)
        assertFalse("nothing was excluded, so nothing should be disclaimed",
            text.contains("can't compare"))
    }
}
