package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Which elements resemble one — its group. Ported from the Windows `ElementSimilarityTest`. */
class ElementSimilarityTest {

    @Before fun setUp() = assumeTrue(ChatFixture.available)

    @Test fun theseAreAnswered() {
        for ((query, language) in listOf(
            "which element is similar to carbon" to "en",
            "which element is similar to gold" to "en",
            "vilket grundämne liknar kol" to "sv"
        )) {
            val reply = ChatFixture.chat(language = language).ask(query)
            assertNotEquals(query, ChatFixture.strings(language).get(R.string.ai_no_data), reply.text)
        }
    }

    /** Gold's column — copper, silver — not every transition metal. */
    @Test fun theAnswerIsTheGroupNotTheWholeSeries() {
        val text = ChatFixture.chat().ask("which element is similar to gold").text
        assertTrue(text, text.contains("Copper", ignoreCase = true))
        assertTrue(text, text.contains("Silver", ignoreCase = true))
        assertFalse(text, text.contains("Iron", ignoreCase = true))
    }

    @Test fun theElementIsNotItsOwnNeighbour() {
        val listed = ChatFixture.chat().ask("which element is similar to gold").text.substringAfterLast(':')
        assertFalse(listed, listed.contains("Gold,", ignoreCase = true))
    }

    @Test fun theFocusedElementAnswersABareQuestion() {
        val chat = ChatFixture.chat()
        chat.ask("what is the density of gold")
        assertTrue(chat.ask("which elements are similar").text.contains("Copper", ignoreCase = true))
    }

    @Test fun withNoElementItAsksWhichOne() =
        assertEquals(ChatFixture.strings().get(R.string.ai_similarity_no_element),
            ChatFixture.chat().ask("which elements are similar").text)

    @Test fun anElementOutsideTheNumberedGroupsFallsBackToItsSeries() {
        val text = ChatFixture.chat().ask("which element is similar to neodymium").text
        assertNotEquals(ChatFixture.noData, text)
        assertTrue(text, text.contains("Neodymium", ignoreCase = true))
    }
}
