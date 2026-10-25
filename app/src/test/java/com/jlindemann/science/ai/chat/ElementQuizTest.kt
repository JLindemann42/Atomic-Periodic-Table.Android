package com.jlindemann.science.ai.chat

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** "Quiz me", and the verdict on the next reply. Ported from the Windows `ElementQuizTest`. */
class ElementQuizTest {

    @Before fun setUp() = assumeTrue(ChatFixture.available)

    @Test fun askingForAQuizIsAnswered() {
        for (query in listOf("quiz", "quiz me", "give me a quiz question", "quiz mich")) {
            val reply = ChatFixture.chat(1).ask(query)
            assertNotEquals(query, ChatFixture.noData, reply.text)
            assertTrue(query, reply.text.isNotBlank())
        }
    }

    @Test fun theQuestionIsOneOfTheThreeShapes() {
        for (seed in 0 until 12) {
            val text = ChatFixture.chat(seed).ask("quiz me").text
            val isQuestion = listOf("symbol", "atomic number", "discovered").any { text.contains(it, ignoreCase = true) }
            assertTrue("seed $seed produced: $text", isQuestion)
        }
    }

    /**
     * The history shape is reachable. Reading the discoverer by its JSON key,
     * `element_discovered_name`, which is not a field id, filters every candidate out (D53).
     */
    @Test fun theHistoryQuestionIsAskedForSomeSeed() {
        val asked = (0 until 40).any { ChatFixture.chat(it).ask("quiz me").text.contains("discovered", ignoreCase = true) }
        assertTrue(asked)
    }

    @Test fun theRightAnswerIsAccepted() {
        val chat = ChatFixture.chat(3)
        chat.ask("quiz me")
        val expected = chat.state.pendingQuizAnswer!!
        assertTrue(chat.ask(expected).text.contains("Correct", ignoreCase = true))
        assertNull(chat.state.pendingQuizAnswer)
    }

    @Test fun aWrongAnswerIsRejected() {
        val chat = ChatFixture.chat(5)
        chat.ask("quiz me")
        val expected = chat.state.pendingQuizAnswer!!
        val reply = chat.ask(if (expected == "gold") "iron" else "gold")
        assertFalse(reply.text.contains("Correct", ignoreCase = true))
        assertNull(chat.state.pendingQuizAnswer)
    }

    @Test fun anElementNameIsJudgedRatherThanLookedUp() {
        val chat = ChatFixture.chat(7)
        chat.ask("quiz me")
        val text = chat.ask("gold").text
        assertTrue(text, text.contains("Correct", ignoreCase = true) || text.contains("Not quite", ignoreCase = true))
    }

    @Test fun askingAgainStartsANewQuestion() {
        val chat = ChatFixture.chat(9)
        chat.ask("quiz me")
        assertFalse(chat.ask("quiz me again").text.contains("Correct", ignoreCase = true))
        assertNotNull(chat.state.pendingQuizAnswer)
    }

    @Test fun withNoQuestionOpenAnElementNameIsStillALookup() {
        val text = ChatFixture.chat(11).ask("gold").text
        assertFalse(text.contains("Correct", ignoreCase = true))
        assertTrue(text.contains("Au"))
    }

    /** With a question open, "tell me more" was judged: "Not quite! The answer was Vanadium". */
    @Test fun anUnrelatedRequestIsNotJudgedAsAGuess() {
        for (query in listOf("tell me more", "what can you do")) {
            val chat = ChatFixture.chat(13)
            chat.ask("quiz me")
            val text = chat.ask(query).text
            assertFalse(query, text.contains("Not quite", ignoreCase = true))
            assertFalse(query, text.contains("Correct", ignoreCase = true))
            // And the question is still waiting, not spent.
            assertNotNull(query, chat.state.pendingQuizAnswer)
        }
    }

    /**
     * No sentinel and no empty value. Not "** in **", which every filled question contains
     * ("by **X** in **1878**"); that check only ever passed while the question was never asked.
     */
    @Test fun aHistoryQuestionNeverHasBlanks() {
        for (seed in 0 until 25) {
            val text = ChatFixture.chat(seed).ask("quiz me").text
            if (text.contains("discovered", ignoreCase = true)) {
                assertFalse(text, text.contains("---"))
                assertFalse(text, text.contains("****"))
                assertFalse(text, text.contains("** **"))
            }
        }
    }
}
