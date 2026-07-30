package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.*
import com.jlindemann.science.ai.nlu.ClauseSplitter
import com.jlindemann.science.ai.retrieval.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Questions that ask two things at once.
 *
 * "What is the density of gold and how does it compare to lead" used to answer only the
 * comparison, silently dropping the first half. The difficulty is not finding the conjunction —
 * it is knowing when *not* to split on it, since "and" also joins two elements, two range bounds
 * and two aspects in questions that already work.
 */
class CompoundQuestionTest {

    private lateinit var engine: AiEngine
    private lateinit var store: KnowledgeStore

    @Before fun setUp() {
        assumeTrue(TestAssets.available()); assumeTrue(TestStrings.available)
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        val ds = DatasetIndex.build()
        val al = EntityResolver.buildAliases(mapOf("en" to store.elements.associate {
            it.key to Triple(it.key, it.symbol, it.atomicNumber.toString()) }))
        val er = EntityResolver(al, "en")
        val idx = Bm25Index(HybridRetriever.buildCorpus(store, ds, null))
        engine = AiEngine(store, ds, null, TestStrings(), er, HybridRetriever(store, ds, idx, er, null))
    }

    private fun answer(q: String) = engine.answer(q, DialogueState())?.text ?: ""

    // ---- Both halves get answered -------------------------------------------------------------

    @Test fun answersBothClauses() {
        val text = answer("what is the density of gold and how does it compare to lead")
        assertTrue("first clause missing", text.contains("Gold's Density"))
        assertTrue("second clause missing", text.contains("Lead"))
    }

    /** The second clause refers back with a pronoun, resolved by the same slot inheritance
     *  that makes a follow-up turn work. */
    @Test fun theSecondClauseInheritsTheSubject() {
        val text = answer("what is the melting point of iron and what is its density")
        assertTrue(text.contains("Melting Point"))
        assertTrue("'its' should resolve to iron", text.contains("Iron's Density"))
    }

    @Test fun clausesMayNameDifferentElements() {
        val text = answer("what is the atomic number of gold and what is the symbol for silver")
        assertTrue(text.contains("79"))
        assertTrue(text.contains("Ag"))
    }

    @Test fun oneMergedSourcesSection() {
        val text = answer("what is the melting point of iron and what is its density")
        assertEquals("sources should appear once, not per clause",
            1, Regex("### Sources").findAll(text).count())
    }

    // ---- Questions that must not be split -------------------------------------------------------

    @Test fun conjunctionJoiningTwoElementsIsNotASplit() {
        assertNull(ClauseSplitter.split("compare gold and silver"))
        val text = answer("compare gold and silver")
        assertTrue("should still be one comparison", text.contains("Comparing"))
    }

    @Test fun rangeBoundsAreNotSplit() {
        assertNull(ClauseSplitter.split("which elements melt between 1000 and 2000 kelvin"))
    }

    @Test fun listedAspectsAreNotSplit() {
        assertNull(ClauseSplitter.split(
            "compare lithium sodium and potassium in terms of density and atomic mass"
        ))
    }

    @Test fun aTrailingElementNameIsNotAClause() {
        // "…and silver" is a continuation, not a question of its own.
        assertNull(ClauseSplitter.split("tell me about gold and silver"))
    }

    /**
     * A protecting word only guards the conjunction it reaches across. "Compare" at the start
     * keeps a query whole; the same word in the tail is a second question.
     */
    @Test fun protectionAppliesToTheHeadOnly() {
        assertNull(ClauseSplitter.split("compare gold and silver"))
        assertNotNull(ClauseSplitter.split("what is the density of gold and how does it compare to lead"))
    }

    @Test fun tooShortToBeTwoQuestions() {
        assertNull(ClauseSplitter.split("gold and lead"))
        assertNull(ClauseSplitter.split("and"))
    }

    // ---- All or nothing ----------------------------------------------------------------------------

    /**
     * If either clause cannot be planned the whole attempt is abandoned, so the query falls
     * through and is handled whole. A half-answered compound would be worse than what it replaced.
     */
    @Test fun anUnplannableClauseFallsBackToWholeQuery() {
        val text = answer("what is the density of gold and what is the meaning of life")
        assertFalse("should not answer half of it", text.contains("meaning"))
    }

    @Test fun conversationStateSurvivesAFailedSplit() {
        val state = DialogueState()
        state.focusElement = "gold"
        engine.answer("what is the density of gold and what is the meaning of life", state)
        assertEquals("a failed attempt must not corrupt the conversation", "gold", state.focusElement)
    }

    @Test fun stateAdvancesAfterASuccessfulCompound() {
        val state = DialogueState()
        engine.answer("what is the atomic number of gold and what is the symbol for silver", state)
        assertEquals("focus should follow the last clause", "silver", state.focusElement)
    }
}
