package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.Entitlements
import com.jlindemann.science.ai.data.FieldKind
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.ReactivityScale
import com.jlindemann.science.ai.data.SeriesId
import com.jlindemann.science.ai.data.Tier
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The conversation as a whole: the order of the handlers, language, memory and entitlements.
 * Ported from the Windows `ChatOrchestratorTest`, `ReactivityScaleTest` and `ContextBleedTest`.
 */
class ChatOrchestratorTest {

    @Before fun setUp() = assumeTrue(ChatFixture.available)

    private fun chat(language: String = "en", seed: Int? = null) = ChatFixture.chat(seed, language)
    private fun s(id: Int, vararg args: Any) = ChatFixture.strings().get(id, *args)

    // ---- The order is the contract ----------------------------------------------------------

    @Test fun aGroundedQuestionIsAnsweredByTheEngine() {
        val reply = chat().ask("what is the density of gold")
        assertEquals(ReplySource.ENGINE, reply.source)
        assertNotNull(reply.plan)
        assertTrue(reply.actions.isNotEmpty())
    }

    @Test fun aGreetingIsAnsweredWithoutTouchingTheData() {
        for (message in listOf("hello there", "hej", "hallo")) {
            val reply = chat().ask(message)
            assertEquals(message, ReplySource.PERSONALITY, reply.source)
            assertTrue(message, reply.actions.isEmpty())
            assertNull(message, reply.plan)
        }
    }

    @Test fun aQuestionAboutTheAppIsAnswered() {
        for (message in listOf("what can you do", "do you have flashcards", "tell me about the unit converter",
            "is there a reaction balancer")) {
            val reply = chat().ask(message)
            assertEquals(message, ReplySource.PERSONALITY, reply.source)
            assertNotEquals(message, ChatFixture.noData, reply.text)
        }
    }

    @Test fun anUnanswerableQuestionDeclinesRatherThanImprovising() {
        val reply = chat().ask("write me a poem about the sea")
        assertEquals(ReplySource.DECLINED, reply.source)
        assertTrue(reply.text.isNotBlank())
    }

    @Test fun everyUngroundedReplyStillOffersSomewhereToGo() {
        for (message in listOf("hello", "write me a poem", "")) {
            assertTrue("'$message'", chat().ask(message).suggestions.isNotEmpty())
        }
    }

    // ---- Remembering the user -----------------------------------------------------------------

    @Test fun aFirstGreetingIsThePlainOne() = assertEquals(s(R.string.ai_greeting), chat().ask("hello").text)

    @Test fun aReturningUserIsGreetedByTheirElement() {
        val chat = chat()
        chat.ask("what is the density of gold")
        chat.ask("what is the melting point of gold")
        val greeting = chat.ask("hello").text
        assertNotEquals(s(R.string.ai_greeting), greeting)
        assertTrue(greeting, greeting.contains("Gold"))
    }

    /** The router interpolated the key and greeted a Swede about "gold" mid-sentence. */
    @Test fun theRememberedElementIsNamedInTheReadersLanguage() {
        val chat = chat("sv")
        chat.ask("vad är densiteten för guld")
        val greeting = chat.ask("hej").text
        assertTrue(greeting, greeting.contains("Guld"))
        assertFalse(greeting, greeting.contains("gold", ignoreCase = true))
    }

    // ---- Facts -------------------------------------------------------------------------------

    @Test fun aFactRequestGetsAShippedFact() {
        val facts = ChatFixture.strings().array(R.array.ai_facts)
        for (message in listOf("tell me a fun fact", "did you know anything interesting")) {
            val reply = chat(seed = 7).ask(message)
            assertEquals(message, ReplySource.PERSONALITY, reply.source)
            assertTrue(reply.text, reply.text in facts)
        }
    }

    @Test fun aSwedishFactRequestIsAnsweredInSwedish() {
        val text = chat("sv", 3).ask("visste du nagot intressant").text
        assertTrue(text, text in ChatFixture.strings("sv").array(R.array.ai_facts))
        assertFalse(text, text in ChatFixture.strings("en").array(R.array.ai_facts))
    }

    @Test fun aGroundedQuestionIsNotDerailedByAFactKeyword() =
        assertNotEquals(ReplySource.PERSONALITY,
            chat().ask("what is the interesting property of the melting point of gold").source)

    // ---- Reactivity, the capability no field covers ------------------------------------------

    @Test fun aReactivityComparisonIsAnsweredByTheHeuristic() {
        val reply = chat().ask("which is more reactive, sodium or potassium")
        assertEquals(ReplySource.REACTIVITY, reply.source)
        assertTrue(reply.text, reply.text.contains("otassium"))
    }

    /** Two are needed to order them; one is not a comparison. */
    @Test fun aReactivityQuestionNamingOneElementIsNotAnswered() =
        assertNotEquals(ReplySource.REACTIVITY, chat().ask("is sodium reactive").source)

    @Test fun theReactivityScaleOrdersTheGroups() {
        val store = ChatFixture.service.store
        fun e(key: String) = store.element(key)!!
        for (other in store.elements.filter { it.key != "fluorine" }) {
            assertSame(other.key, e("fluorine"), ReactivityScale.moreReactive(e("fluorine"), other))
        }
        assertSame(e("potassium"), ReactivityScale.moreReactive(e("sodium"), e("potassium")))
        assertSame(e("chlorine"), ReactivityScale.moreReactive(e("chlorine"), e("iodine")))
        assertEquals(0, ReactivityScale.of(e("neon")))
        assertNull(ReactivityScale.moreReactive(e("neon"), e("argon")))
        assertEquals(ReactivityScale.of(SeriesId.ALKALI_METAL, 55, "caesium"), ReactivityScale.of(SeriesId.ALKALI_METAL, 55, "cesium"))
    }

    // ---- Language ------------------------------------------------------------------------------

    @Test fun theLanguageFollowsTheMessageRatherThanTheSession() {
        val chat = chat()
        chat.ask("what is the density of gold")
        assertEquals("en", chat.language)
        val swedish = chat.ask("vad ar densiteten for guld")
        assertEquals("sv", chat.language)
        assertTrue(swedish.text, swedish.text.contains("Guld"))
    }

    /** "Argon" is spelled the same in Swedish and English, so nothing here is decisive. */
    @Test fun anAmbiguousMessageDoesNotSwitchLanguage() {
        val chat = chat("sv")
        chat.ask("vad ar densiteten for guld")
        chat.ask("argon")
        assertEquals("sv", chat.language)
    }

    /** "Gold" is English, German and Afrikaans, so it is evidence for none of them. */
    @Test fun aSharedNameIsNotEvidenceOfALanguage() {
        val chat = chat()
        chat.ask("electron configuration of gold")
        assertEquals("en", chat.language)
    }

    /**
     * French "or" is gold. Counting it as French evidence answered "which is more reactive, sodium
     * or potassium" in French — found on a device, not by the corpus.
     */
    @Test fun anEnglishOrIsNotFrench() {
        val chat = chat()
        val reply = chat.ask("which is more reactive, sodium or potassium")
        assertEquals("en", chat.language)
        assertTrue(reply.text, reply.text.contains("more reactive"))
    }

    /** The floor is for foreign names only: the language in play keeps its short names. */
    @Test fun aShortNameStillCountsInItsOwnLanguage() =
        assertEquals("fr", ChatFixture.service.resolver("fr").detectLanguage("densité de l'or", "fr"))

    // ---- Entitlements and state -----------------------------------------------------------------

    @Test fun aPurchaseTakesEffectOnTheNextMessage() {
        val gated = FieldRegistry.ALL.first { it.tier == Tier.PRO && it.kind == FieldKind.NUMERIC }.id
        var pro = false
        val chat = ChatOrchestrator(ChatFixture.service, ChatFixture::strings, { Entitlements(pro, pro) })
        val question = "what is the ${gated.replace('_', ' ')} of gold"
        val locked = chat.ask(question).text
        pro = true
        assertNotEquals(locked, chat.ask(question).text)
    }

    @Test fun theThreadRemembersItsSubjectAcrossTurns() {
        val chat = chat()
        chat.ask("what is the density of gold")
        val followUp = chat.ask("and its melting point?")
        assertEquals(ReplySource.ENGINE, followUp.source)
        assertEquals(listOf("gold"), followUp.plan!!.elementKeys)
    }

    @Test fun resetClearsTheSubjectButKeepsTheLanguage() {
        val chat = chat()
        chat.ask("vad ar densiteten for guld")
        chat.reset()
        assertNull(chat.state.focusElement)
        assertEquals("sv", chat.language)
    }

    /** Opened from an element's page, "what is its density" is about that element. */
    @Test fun aFocusedElementAnswersAnAnaphor() {
        val chat = chat()
        chat.focusOn("Iron")
        assertTrue(chat.ask("what is its density").text.contains("Iron"))
    }

    @Test fun suggestionsFollowWhateverTheThreadIsAbout() {
        val chat = chat()
        assertTrue(chat.ask("hello").suggestions.isNotEmpty())
        val warm = chat.ask("what is the density of gold").suggestions
        assertTrue(warm.toString(), warm.any { it.contains("Gold") })
    }

    @Test fun aGenuineEllipsisStillInheritsTheField() {
        for ((followUp, expected) in listOf("and iron?" to "Iron", "what about silver" to "Silver")) {
            val chat = chat()
            chat.ask("what is the density of gold")
            val text = chat.ask(followUp).text
            assertTrue(text, text.contains(expected, ignoreCase = true) && text.contains("Density", ignoreCase = true))
        }
    }
}
