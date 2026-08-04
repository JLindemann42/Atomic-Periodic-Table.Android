package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.*
import com.jlindemann.science.ai.retrieval.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * [AiEngine.lastPlan], which is what the chat panel reports as the answer's intent.
 *
 * Nothing in the app reads this except analytics, so a refactor could stop setting it and every
 * other test would still pass — the data would simply go quiet, months before anyone noticed the
 * `intent` parameter had become permanently absent. This is the tripwire for that.
 */
class AiEngineTelemetryTest {

    private lateinit var engine: AiEngine

    @Before fun setUp() {
        assumeTrue(TestAssets.available()); assumeTrue(TestStrings.available)
        KnowledgeStore.clear()
        val store = KnowledgeStore.build(TestAssets.elementTable("en"))
        val ds = DatasetIndex.build()
        val al = EntityResolver.buildAliases(mapOf("en" to store.elements.associate {
            it.key to Triple(it.key, it.symbol, it.atomicNumber.toString()) }))
        val er = EntityResolver(al, "en")
        val idx = Bm25Index(HybridRetriever.buildCorpus(store, ds, null))
        engine = AiEngine(store, ds, null, TestStrings(), er, HybridRetriever(store, ds, idx, er, null))
    }

    @Test fun recordsThePlanBehindAnAnswer() {
        val answer = engine.answer("what is the density of gold", DialogueState())
        assertNotNull("expected the engine to claim this query", answer)

        val plan = engine.lastPlan
        assertNotNull("an answered query must leave its plan behind", plan)
        assertEquals(Intent.PROPERTY_LOOKUP, plan!!.intent)
        assertTrue("confidence should be a usable number", plan.confidence > 0.0)
    }

    @Test fun recordsThePlanForACompoundAnswer() {
        val answer = engine.answer(
            "what is the density of gold and how does it compare to lead",
            DialogueState()
        )
        assumeTrue("compound splitting did not apply here", answer != null)
        assertNotNull("a compound answer still reports its final clause's plan", engine.lastPlan)
    }

    @Test fun leavesNoPlanWhenItDefers() {
        // Deliberately not a chemistry question, so the planner cannot reach its threshold.
        val answer = engine.answer("please tell me a story about my weekend", DialogueState())
        assumeTrue("the engine unexpectedly claimed this query", answer == null)
        assertNull("a deferral must not report a plan from an earlier turn", engine.lastPlan)
    }

    @Test fun aDeferralClearsThePlanFromThePreviousTurn() {
        assertNotNull(engine.answer("what is the density of gold", DialogueState()))
        assertNotNull(engine.lastPlan)

        val state = DialogueState()
        assumeTrue(engine.answer("please tell me a story about my weekend", state) == null)
        assertNull("the previous turn's plan must not leak into this one", engine.lastPlan)
    }

    @Test fun answerForRecordsItsSynthesisedPlan() {
        assertNotNull(engine.answerFor(Intent.ISOTOPES, "gold"))
        assertEquals(Intent.ISOTOPES, engine.lastPlan?.intent)
    }
}
