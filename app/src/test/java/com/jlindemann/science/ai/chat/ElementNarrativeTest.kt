package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.chat.ElementNarrative.Topic
import com.jlindemann.science.ai.retrieval.TextMatching
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Usage, biological role and etymology, from the description. Ported from the Windows test. */
class ElementNarrativeTest {

    private fun detect(query: String) = ElementNarrative.detect(TextMatching.normalizeForLookup(query))

    @Test fun theTopicIsRecognised() {
        for ((query, expected) in listOf(
            "what is gold used for" to Topic.USAGE,
            "what is titanium used for" to Topic.USAGE,
            "what is the biological role of iron" to Topic.BIOLOGICAL,
            "is zinc important for the body" to Topic.BIOLOGICAL,
            "where does the name gold come from" to Topic.ETYMOLOGY,
            "why is it called tungsten" to Topic.ETYMOLOGY,
            // The app's own chip, a heading rather than a question.
            "Uses of gold" to Topic.USAGE
        )) assertEquals(query, expected, detect(query))
    }

    @Test fun ordinaryQuestionsAreNotClaimed() {
        for (query in listOf(
            "what is the density of gold", "gold", "balance Fe + O2 -> Fe2O3", "",
            "what happens when sodium reacts with water", "which element is similar to gold"
        )) assertNull(query, detect(query))
    }

    @Test fun theseAreAnswered() {
        assumeTrue(ChatFixture.available)
        for (query in listOf(
            "what is gold used for", "what is the biological role of iron", "where does the name gold come from"
        )) assertNotEquals(query, ChatFixture.noData, ChatFixture.chat().ask(query).text)
    }

    @Test fun theAnswerNamesTheElement() {
        assumeTrue(ChatFixture.available)
        assertTrue(ChatFixture.chat().ask("what is gold used for").text.contains("Gold", ignoreCase = true))
    }

    @Test fun theFocusedElementAnswersAFollowUp() {
        assumeTrue(ChatFixture.available)
        val chat = ChatFixture.chat()
        chat.ask("what is the density of gold")
        val text = chat.ask("what is it used for").text
        assertNotEquals(ChatFixture.noData, text)
        assertTrue(text, text.contains("Gold", ignoreCase = true))
    }

    @Test fun theEtymologyFallbackNeverLeavesABlank() {
        assumeTrue(ChatFixture.available)
        val text = ChatFixture.chat().ask("where does the name gold come from").text
        assertFalse(text, text.contains("by  in"))
        assertFalse(text, text.contains(" ."))
    }

    @Test fun theBiologicalFallbackDoesNotEndOnAColon() {
        assumeTrue(ChatFixture.available)
        val text = ChatFixture.chat().ask("what is the biological role of gold").text
        assertFalse(text, text.trimEnd().endsWith(':'))
    }

    @Test fun withNoElementItFallsThrough() {
        assumeTrue(ChatFixture.available)
        assertEquals(ChatFixture.noData, ChatFixture.chat().ask("what is it used for").text)
    }

    @Test fun aSwedishQuestionIsAnswered() {
        assumeTrue(ChatFixture.available)
        assertNotEquals(ChatFixture.strings("sv").get(R.string.ai_no_data),
            ChatFixture.chat(language = "sv").ask("vilken biologisk roll har järn").text)
    }
}
