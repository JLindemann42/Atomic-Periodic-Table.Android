package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * "Tell me more" — the next unsaid fact about the element in focus. The point is that repeating
 * "more" keeps teaching, so most of these assert across a run of turns. Ported from the Windows
 * `ElementMoreInfoTest`.
 */
class ElementMoreInfoTest {

    @Before fun setUp() = assumeTrue(ChatFixture.available)

    private fun chat(seed: Int) = ChatFixture.chat(seed)

    /** Twenty-two properties, and every one must survive the JSON-key lookup. */
    @Test fun everyCandidatePropertyResolvesToAField() {
        assertEquals(22, ElementMoreInfo.candidateFieldIds.size)
        assertEquals(22, ElementMoreInfo.candidateFieldIds.distinct().size)
    }

    /** Each is a field some element has — which `element_thermal_conductivity` never was. */
    @Test fun everyCandidatePropertyIsPopulatedForSomeElement() {
        val all = ChatFixture.service.store.elements
        val dead = ElementMoreInfo.candidateFieldIds.filter { id -> all.all { it.value(id).isMissing } }
        assertEquals(emptyList<String>(), dead)
    }

    @Test fun tellMeMoreIsAnswered() {
        for (query in listOf("tell me more", "what else", "anything else", "keep going")) {
            val chat = chat(1)
            chat.ask("gold")
            val reply = chat.ask(query)
            assertNotEquals(query, ChatFixture.noData, reply.text)
            assertTrue(query, reply.text.isNotBlank())
        }
    }

    @Test fun itDoesNotRepeatItself() {
        val chat = chat(2)
        chat.ask("gold")
        val seen = (1..6).map { chat.ask("tell me more").text }
        assertEquals(seen.size, seen.distinct().size)
    }

    /** The overview already states the atomic mass, so "more" must not offer it as news. */
    @Test fun theOverviewsOwnFactsAreNotOfferedAsNews() {
        for (seed in 0 until 8) {
            val chat = chat(seed)
            assertTrue(chat.ask("tell me about gold").text.contains("Au"))
            assertTrue("atomic_mass" in chat.state.sharedFieldIds)
        }
    }

    @Test fun aMeltingPointIsFollowedByABoilingPointOrDensity() {
        val chat = chat(4)
        chat.ask("what is the melting point of gold")
        assertTrue("melting_point" in chat.state.sharedFieldIds)
        chat.ask("tell me more")
        val next = chat.state.sharedFieldIds.last()
        assertTrue("got $next", next == "boiling_point" || next == "density")
    }

    /** With nothing in focus there is nothing to say more about. */
    @Test fun withNoElementInFocusItDeclines() {
        assertEquals(ChatFixture.noData, chat(5).ask("tell me more").text)
    }

    @Test fun eventuallyItSaysItHasCoveredEverything() {
        val chat = chat(6)
        chat.ask("gold")
        var last = ""
        repeat(40) { last = chat.ask("tell me more").text }
        assertTrue(last, last.contains(ChatFixture.strings().get(R.string.ai_covered_all, "Gold")))
    }

    /** "More" on its own is the ordinary comparative, and the engine still owns it. */
    @Test fun aComparativeQuestionIsStillAQuestion() {
        val chat = chat(7)
        chat.ask("gold")
        val reply = chat.ask("which has more protons, gold or iron")
        assertEquals(ReplySource.ENGINE, reply.source)
        assertTrue(reply.text.contains("79"))
    }

    @Test fun anAffirmativeContinuesTheTour() {
        for (word in listOf("yes", "sure", "ok")) {
            val chat = chat(9)
            chat.ask("gold")
            chat.ask("tell me more")
            val before = chat.state.sharedFieldIds.size
            assertNotEquals(word, ChatFixture.noData, chat.ask(word).text)
            assertEquals(word, before + 1, chat.state.sharedFieldIds.size)
        }
    }

    /** A bare "yes" with nothing said yet has nothing to continue. */
    @Test fun anAffirmativeOutOfNowhereIsNotATour() {
        assertEquals(ChatFixture.noData, chat(10).ask("yes").text)
    }

    /** "More" is one edit from the French "bore", "sure" from the Swedish "syre". */
    @Test fun aContinuationIsNotMistakenForAnElement() {
        for (word in listOf("more", "sure")) {
            val chat = chat(11)
            chat.ask("what is the melting point of iron")
            chat.ask(word)
            assertEquals(word, "iron", chat.state.focusElement)
        }
    }

    @Test fun aBareContinuationDoesNotReplayTheLastAnswer() {
        val chat = chat(12)
        val first = chat.ask("what is the melting point of iron").text
        assertNotEquals(first, chat.ask("more").text)
        assertEquals("iron", chat.state.focusElement)
    }

    @Test fun theTourIsPerElement() {
        val chat = chat(8)
        chat.ask("gold")
        chat.ask("tell me more")
        chat.ask("iron")
        assertTrue(chat.state.sharedFieldIds.none { chat.state.hasShared("gold", it) })
    }

    /** Gold has 118 neutrons and one stable isotope; the sentence used to say "Isotopes is 118". */
    @Test fun neutronsAreCalledNeutronsInASentence() {
        val answer = com.jlindemann.science.ai.core.AiEngine(
            ChatFixture.service.store, ChatFixture.service.datasets, ChatFixture.service.localized("en"),
            ChatFixture.strings(), ChatFixture.service.resolver("en"), null
        ).fieldAnswerFor("gold", "common_neutrons")!!.text
        assertFalse(answer, answer.contains("Isotopes is"))
        assertTrue(answer, answer.contains("118"))
    }
}
