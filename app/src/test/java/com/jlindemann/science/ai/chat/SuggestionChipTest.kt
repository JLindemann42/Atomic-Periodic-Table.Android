package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Every chip the app offers, in every language, has to be answered — the app suggesting a question
 * it then declines is the worst decline there is. Ported from the Windows `SuggestedQuestionsTest`
 * (porting notes, D48), which found ten of them declined.
 */
class SuggestionChipTest {

    @Before fun setUp() = assumeTrue(ChatFixture.available)

    private val languages = listOf("en", "sv", "de", "fr", "es", "it", "pt", "hi", "ur", "zh", "af", "fil")

    @Test fun everySuggestedQuestionIsAnswered() {
        val declined = mutableListOf<String>()
        var asked = 0
        for (language in languages) {
            val strings = ChatFixture.strings(language)
            // A real element, localized, because that is what the chip is rendered with.
            val name = ChatFixture.service.localized(language)?.name("gold") ?: "Gold"
            val chips = strings.array(R.array.ai_suggest_general) + strings.array(R.array.ai_suggest_tools) +
                    strings.array(R.array.ai_suggest_element).map {
                        runCatching { String.format(Locale.ROOT, it, name) }.getOrDefault(it)
                    }
            for (chip in chips) {
                asked++
                val reply = ChatFixture.chat(5, language).ask(chip)
                if (reply.text.isBlank() || reply.text == strings.get(R.string.ai_no_data)) declined.add("[$language] $chip")
            }
        }
        assertTrue("expected chips in every language, asked $asked", asked >= languages.size * 10)
        assertTrue("${declined.size} of $asked chips declined:\n  " + declined.joinToString("\n  "), declined.isEmpty())
    }

    /** The router offered chips in Swedish, German and English only. */
    @Test fun everyLanguageOffersItsOwnChips() {
        val english = ChatFixture.chat(language = "en").suggestions()
        for (language in languages - "en") {
            val chips = ChatFixture.chat(language = language).suggestions()
            assertEquals(language, 3, chips.size)
            assertNotEquals(language, english, chips)
        }
    }

    /**
     * With an element in play, the chips about it name it in the reader's language. Not every chip
     * names it since roadmap 2.1: "Vilket grundämne har högst smältpunkt?" follows the same answer.
     */
    @Test fun elementChipsNameTheElementInTheReadersLanguage() {
        val chat = ChatFixture.chat(language = "sv")
        chat.ask("vad är guldets densitet")
        val chips = chat.ask("och dess smältpunkt").suggestions
        assertTrue(chips.toString(), chips.any { it.contains("Guld") } && chips.none { it.contains("Gold") })
    }
}
