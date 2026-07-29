package com.jlindemann.science.ai.cards

import com.jlindemann.science.ai.compose.AnswerComposer
import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.Entitlements
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.core.TestStrings
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.FieldResolver
import com.jlindemann.science.ai.nlu.QueryPlanner
import com.jlindemann.science.ai.retrieval.Bm25Index
import com.jlindemann.science.ai.retrieval.EntityResolver
import com.jlindemann.science.ai.retrieval.HybridRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * That every card the app can draw is actually reachable by asking for it.
 *
 * [CardFoundationsTest] covers the reducers — what a card would say once one is chosen — and every
 * one of them passed while four of the eight kinds could not appear in a single answer. Nothing sat
 * between the question and the picture: the emission spectrum had no query that produced it, and the
 * two PRO kinds were dropped with the withheld value they were meant to advertise.
 *
 * So this asserts the path end to end, from a sentence a user might type to the kind of card that
 * comes back — and does it for a free viewer as well as a subscriber, because that is where it broke.
 */
class CardReachabilityTest {

    private lateinit var store: KnowledgeStore
    private lateinit var datasets: DatasetIndex
    private lateinit var planner: QueryPlanner
    private lateinit var strings: TestStrings

    @Before fun setUp() {
        assumeTrue("real assets not reachable", TestAssets.available())
        assumeTrue("real strings not reachable", TestStrings.available)
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        datasets = DatasetIndex.build()
        strings = TestStrings()
        val aliases = EntityResolver.buildAliases(
            mapOf("en" to store.elements.associate {
                it.key to Triple(it.key, it.symbol, it.atomicNumber.toString())
            })
        )
        val entities = EntityResolver(aliases, "en")
        val index = Bm25Index(HybridRetriever.buildCorpus(store, datasets, null))
        planner = QueryPlanner(
            store, datasets, entities, FieldResolver(strings),
            HybridRetriever(store, datasets, index, entities, null)
        )
    }

    /** The card a question comes back with, for a viewer with the given entitlements. */
    private fun cardFor(
        query: String,
        entitlements: Entitlements = Entitlements.FULL,
        policy: ChatCardPolicy = ChatCardPolicy.DEFAULT
    ): ChatCard? {
        val plan = planner.plan(query, DialogueState())
        if (plan.intent == Intent.UNKNOWN) return null
        val result = QueryExecutor(store, datasets, null, strings, entitlements).execute(plan) ?: return null
        return AnswerComposer(store, null, strings, policy).compose(result, plan).card
    }

    private fun answerFor(query: String, policy: ChatCardPolicy = ChatCardPolicy.DEFAULT): String {
        val plan = planner.plan(query, DialogueState())
        val result = QueryExecutor(store, datasets, null, strings).execute(plan) ?: return ""
        return AnswerComposer(store, null, strings, policy).compose(result, plan).text
    }

    /**
     * One question per kind. A kind with no row here is a card nothing can ask for.
     *
     * Kept as a map rather than a list of assertions so a newly added kind fails the exhaustiveness
     * check below instead of being quietly untested.
     */
    private val questionPerKind = mapOf(
        ChatCardKind.ELECTRON_SHELL to "electron configuration of gold",
        ChatCardKind.CRYSTAL_STRUCTURE to "crystal structure of iron",
        ChatCardKind.EMISSION_SPECTRUM to "emission spectrum of sodium",
        ChatCardKind.POISSON_BAND to "what is the poisson ratio of gold",
        ChatCardKind.NFPA_DIAMOND to "is sodium dangerous",
        ChatCardKind.IONIZATION_SERIES to "ionization energy of sodium",
        ChatCardKind.ISOTOPE_DECAY to "isotopes of carbon",
        ChatCardKind.ABUNDANCE to "how abundant is gold in the earth's crust"
    )

    @Test fun everyCardKindIsReachableByAsking() {
        assertEquals(
            "a kind with no question cannot be tested, and probably cannot be shown",
            ChatCardKind.values().toSet(), questionPerKind.keys
        )
        for ((kind, question) in questionPerKind) {
            assertEquals("'$question' should produce a $kind card", kind, cardFor(question)?.kind)
        }
    }

    /**
     * A withheld answer keeps its card.
     *
     * The Poisson ratio and the NFPA ratings are both PRO, so for a free viewer the executor returns
     * `Locked` — and dropping the card there left those two kinds unreachable by any question at all,
     * which is the opposite of what the paywall wants: the card is bound and then blurred by
     * `ProCardGate`, so the upsell has something to point at.
     */
    @Test fun aLockedAnswerStillCarriesTheCardItsUnlockedFormWouldHave() {
        val free = Entitlements(isPro = false, isProPlus = false)
        assertEquals(ChatCardKind.POISSON_BAND, cardFor("what is the poisson ratio of gold", free)?.kind)
        assertEquals(ChatCardKind.NFPA_DIAMOND, cardFor("is sodium dangerous", free)?.kind)
        // And the free fields are unaffected either way.
        assertEquals(ChatCardKind.ABUNDANCE, cardFor("abundance of oxygen", free)?.kind)
    }

    /** A gated field with no card of its own must not borrow its family's. */
    @Test fun aLockedFieldWithNoCardGetsNone() {
        val free = Entitlements(isPro = false, isProPlus = false)
        assertNull(cardFor("what is the young's modulus of tungsten", free))
    }

    // ---- Emission spectrum -------------------------------------------------------------------

    /**
     * The spectrum is asked for in several ways, and none of them is a field.
     *
     * "What does the emission spectrum of iron look like" used to resolve `appearance` and answer
     * with the colour of the metal.
     */
    @Test fun theSpectrumIsRecognisedHoweverItIsAskedFor() {
        for (query in listOf(
            "emission spectrum of sodium",
            "what does the emission spectrum of iron look like",
            "spectral lines of hydrogen",
            "show me the spectrum of neon"
        )) {
            val plan = planner.plan(query, DialogueState())
            assertEquals("'$query'", Intent.EMISSION_SPECTRUM, plan.intent)
            assertEquals("'$query'", ChatCardKind.EMISSION_SPECTRUM, cardFor(query)?.kind)
        }
    }

    /** The answer must not claim to name lines the app has no data for. */
    @Test fun theSpectrumAnswerSaysItIsAnImage() {
        val text = answerFor("emission spectrum of sodium")
        assertTrue("should name the element: $text", text.contains("Sodium"))
        assertTrue("should say it is stored as an image: $text", text.contains("image"))
    }

    /**
     * Offline, the one card that needs the network is dropped — and the text says so instead of
     * describing a picture that will never load.
     */
    @Test fun offlineTheSpectrumCardIsWithheldAndSaidSo() {
        val offline = ChatCardPolicy(allowNetworkCards = false)
        assertNull(cardFor("emission spectrum of sodium", policy = offline))
        assertTrue(answerFor("emission spectrum of sodium", offline).contains("online"))
        // Nothing else depends on the network, so nothing else changes.
        assertNotNull(cardFor("isotopes of carbon", policy = offline))
    }

    /** The spectrum answer links to the table that holds the same image. */
    @Test fun theSpectrumAnswerCitesTheEmissionTable() {
        val plan = planner.plan("emission spectrum of sodium", DialogueState())
        val result = QueryExecutor(store, datasets, null, strings).execute(plan)
        val citation = (result as com.jlindemann.science.ai.core.ExecutionResult.EmissionSpectrum)
            .citations.single()
        assertEquals(com.jlindemann.science.ai.data.DeepLinkTarget.EMISSION, citation.deepLink)
    }

    /** A card is only ever attached to an element the answer is actually about. */
    @Test fun theCardNamesTheElementTheAnswerIsAbout() {
        assertEquals("sodium", cardFor("emission spectrum of sodium")?.elementKey)
        assertEquals("gold", cardFor("how abundant is gold in the earth's crust")?.elementKey)
    }

    /** A comparison of two elements gets no single-element card. */
    @Test fun aTwoElementAnswerGetsNoCard() {
        val plan = QueryPlan(
            intent = Intent.COMPARISON,
            entities = listOf(
                com.jlindemann.science.ai.core.EntityRef.Element("gold"),
                com.jlindemann.science.ai.core.EntityRef.Element("iron")
            ),
            fieldIds = listOf("crystal_structure"),
            confidence = 1.0
        )
        val result = QueryExecutor(store, datasets, null, strings).execute(plan)
        assertNotNull(result)
        assertNull(CardSelector.select(result!!, plan, store, strings))
    }
}
