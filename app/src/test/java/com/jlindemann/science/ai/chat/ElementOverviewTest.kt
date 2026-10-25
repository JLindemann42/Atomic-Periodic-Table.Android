package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.Entitlements
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** "Gold", "tell me about gold", "element 79". Ported from the Windows `ElementOverviewTest`. */
class ElementOverviewTest {

    @Before fun setUp() = assumeTrue(ChatFixture.available)

    private fun s(id: Int, vararg args: Any) = ChatFixture.strings().get(id, *args)

    private fun overview(language: String): String =
        ChatFixture.chat(language = language).ask(ChatFixture.service.localized(language)!!.name("gold")!!).text

    @Test fun namingAnElementIsAnswered() {
        for (query in listOf("gold", "tell me about gold", "tell me about iron", "element 79")) {
            val text = ChatFixture.chat().ask(query).text
            assertNotEquals(query, ChatFixture.noData, text)
        }
    }

    @Test fun anAtomicNumberIdentifiesItsElement() {
        val text = ChatFixture.chat().ask("element 79").text
        assertTrue(text, text.contains("79") && text.contains("Au"))
    }

    @Test fun aRealQuestionIsNotTurnedIntoAnOverview() {
        for (query in listOf("what is the density of gold", "compare gold and silver")) {
            assertEquals(query, ReplySource.ENGINE, ChatFixture.chat().ask(query).source)
        }
    }

    @Test fun theOverviewSetsTheFocus() {
        val chat = ChatFixture.chat()
        chat.ask("gold")
        assertTrue(chat.ask("what is its density").text.contains("19.3"))
    }

    @Test fun aSwedishOverviewIsAnswered() {
        val text = ChatFixture.chat(language = "sv").ask("berätta om guld").text
        assertNotEquals(ChatFixture.strings("sv").get(R.string.ai_no_data), text)
        assertTrue(text, text.contains("Au"))
    }

    /** The record is parsed from English; the phase has to come from the language's own file. */
    @Test fun thePhaseIsInTheReadersLanguage() {
        for ((language, phase) in listOf("sv" to "Fast", "de" to "Fest", "fil" to "Solido")) {
            val text = overview(language)
            assertTrue("$language: $text", text.contains(phase))
            assertFalse(language, (text + "\n").contains("**: solid\n"))
        }
    }

    @Test fun noUntranslatedSentenceIsQuoted() {
        for (language in listOf("sv", "hi")) assertFalse(language, overview(language).contains("In a pure form"))
    }

    @Test fun englishKeepsItsDescription() = assertTrue(overview("en").contains("bright, slightly"))

    @Test fun everythingAboutAnElementGetsEverySection() {
        val text = ChatFixture.chat().ask("tell me everything about gold").text
        assertTrue(text.contains(s(R.string.ai_rich_overview_safety, "Gold")))
        assertTrue(text.contains(s(R.string.ai_rich_overview_advanced, "Gold")))
        assertTrue(text.contains(s(R.string.ai_rich_overview_isotopes, "Gold")))
        // The tip is for an ordinary overview; a full one has already said everything.
        assertFalse(text.contains(s(R.string.ai_pro_tip_hint)))
    }

    @Test fun anOrdinaryOverviewEndsWithTheTip() =
        assertTrue(ChatFixture.chat().ask("gold").text.contains(s(R.string.ai_pro_tip_hint)))

    @Test fun aPaidFieldFollowsTheEntitlement() {
        val label = s(R.string.electron_affinity_colon).trimEnd(':')
        val free = Entitlements(isPro = false, isProPlus = false)
        assertFalse(ChatFixture.chat(7, entitlements = free).ask("tell me everything about gold").text.contains(label))
        assertTrue(ChatFixture.chat(7).ask("tell me everything about gold").text.contains(label))
    }

    /** Au-197, not the first slots, which are gone in fractions of a second. */
    @Test fun theNotableIsotopesAreTheStableOnes() {
        val text = ChatFixture.chat().ask("tell me everything about gold").text
        assertTrue(text, text.contains("Au-197") || text.contains("Gold-197"))
        assertFalse(text, text.contains("Au-170") || text.contains("Gold-170"))
    }

    @Test fun withNoStableIsotopeTheLongestLivedAreNamed() {
        val text = ChatFixture.chat().ask("tell me everything about uranium").text
        val isotopes = text.substring(text.indexOf(s(R.string.ai_rich_overview_isotopes, "Uranium")))
        assertTrue(isotopes, isotopes.substringAfter("**").startsWith("Uranium-238"))
    }

    @Test fun safetyIsLabelledInTheReadersLanguage() {
        val text = ChatFixture.chat(language = "sv").ask("berätta allt om guld").text
        val health = ChatFixture.strings("sv").get(R.string.ai_field_nfpa_health)
        assertTrue(text, text.contains(health.replaceFirstChar { it.uppercase() }))
        assertFalse(text, text.contains("**Health"))
    }

    @Test fun sentencesAreSplitWhereTheyEnd() {
        val latin = ElementNarrative.translatedSentences(
            "Its half-lives range between 159,200 years and 4.5 billion years. It is grey.",
            "Its half-lives range between 159,200 years and 4.5 billion years. It is grey.", "en"
        )
        assertEquals(2, latin.size)
        assertTrue(latin[0].endsWith("4.5 billion years."))

        val chinese = ElementNarrative.translatedSentences("金是元素。它是金属。", "Gold.", "zh")
        assertEquals(2, chinese.size)
        assertFalse(overview("zh").contains("。."))
    }

    @Test fun chineseLabelsHaveOneColon() = assertFalse(overview("zh").contains("：**:"))
}
