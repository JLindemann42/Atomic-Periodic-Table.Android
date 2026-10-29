package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Follow-up chips that fit the answer just given (roadmap 2.1). */
class FollowUpsTest {

    @Before
    fun setUp() {
        assumeTrue(ChatFixture.available)
    }

    private fun gold(language: String) = ChatFixture.service.localized(language)?.name("gold") ?: "Gold"

    /**
     * Questions every language has a chip for already, so each can be asked in its own words: a
     * property lookup, a ranking, a list and a calculation.
     */
    private fun seeds(language: String): List<String> {
        val strings = ChatFixture.strings(language)
        return listOf(
            String.format(strings.array(R.array.ai_suggest_element)[1], gold(language)),
            strings.array(R.array.ai_suggest_general)[3],
            strings.array(R.array.ai_suggest_general)[2],
            strings.array(R.array.ai_suggest_tools)[0]
        )
    }

    @Test
    fun afterAPropertyTheChipsAreAboutThatProperty() {
        val reply = ChatFixture.chat().ask("what is the density of gold")
        assertEquals(
            listOf("Density of Gold and Silver", "Which element has the highest density?", "Density of Gold in kg/m³"),
            reply.suggestions
        )
    }

    @Test
    fun aRankingInvitesItsOtherEnd() {
        val reply = ChatFixture.chat().ask("which element has the highest melting point")
        assertEquals("Which element has the lowest melting point?", reply.suggestions.first())
    }

    @Test
    fun aListInvitesACount() {
        val reply = ChatFixture.chat().ask("list the noble gases")
        assertEquals("How many are there?", reply.suggestions.first())
        val count = ChatFixture.chat().run { ask("list the noble gases"); ask(reply.suggestions.first()) }
        assertEquals(Intent.AGGREGATE, count.plan?.intent)
    }

    @Test
    fun aChipNeverRepeatsTheQuestion() {
        val reply = ChatFixture.chat().ask("atomic mass of gold")
        assertTrue(reply.suggestions.toString(), reply.suggestions.none { it == "Atomic mass of Gold" })
    }

    /**
     * The invariant that keeps the templates honest: in every language, every chip offered after an
     * answer is a question the assistant answers. Prints what each language is offered.
     */
    @Test
    fun everyChipIsAnsweredInEveryLanguage() {
        for (language in TestAssets.availableLanguages()) {
            val noData = ChatFixture.strings(language).get(R.string.ai_no_data)
            for (seed in seeds(language)) {
                val chips = ChatFixture.chat(language = language).ask(seed).suggestions
                println("[follow-ups] $language: $seed -> $chips")
                for (chip in chips) {
                    val answer = ChatFixture.chat(language = language).run { ask(seed); ask(chip) }
                    assertNull("$language: $seed -> $chip: ${answer.text}", answer.declineReason)
                    assertTrue("$language: $seed -> $chip", answer.text != noData)
                }
            }
        }
    }
}
