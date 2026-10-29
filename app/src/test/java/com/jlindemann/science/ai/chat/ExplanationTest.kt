package com.jlindemann.science.ai.chat

import com.jlindemann.science.ai.cards.ChatCardKind
import com.jlindemann.science.ai.core.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Reviewed explanations for why questions, each shown with the data it explains (roadmap 2.4). */
class ExplanationTest {

    @Before
    fun setUp() {
        assumeTrue(ChatFixture.available)
    }

    private fun explains(query: String, key: String, card: ChatCardKind?, language: String = "en") {
        val reply = ChatFixture.chat(language = language).ask(query)
        assertEquals(query, Intent.EXPLANATION, reply.plan?.intent)
        assertEquals(query, key, reply.plan?.literal)
        assertEquals(query, card, reply.card?.kind)
    }

    @Test
    fun aTrendIsExplainedByItsFieldAndItsDirection() {
        explains("why does atomic radius increase down a group", "radius-down-group", ChatCardKind.TREND_LINE)
        explains("why does radius decrease left to right, despite adding electrons", "radius-across-period", ChatCardKind.TREND_LINE)
        explains("why does ionization energy increase across a period", "ionization-across-period", ChatCardKind.TREND_LINE)
    }

    @Test
    fun aConceptIsExplainedByItsOwnWords() {
        explains("why are noble gases unreactive", "noble-gas-inert", ChatCardKind.TREND_LINE)
        explains("why is fluorine the most electronegative element", "fluorine-electronegativity", ChatCardKind.TREND_LINE)
        explains("why is mercury a liquid", "mercury-liquid", ChatCardKind.TREND_LINE)
        explains("why is there a big jump in sodium's second ionization energy", "ionization-jumps", ChatCardKind.IONIZATION_SERIES)
        explains("why does the density peak in the middle of the table", "density-table", ChatCardKind.HEAT_MAP)
    }

    @Test
    fun theAnswerIsTheExplanation() {
        val text = ChatFixture.chat().ask("why does atomic radius increase down a group").text
        assertTrue(text, text.startsWith("Each step down a group adds a whole new electron shell"))
    }

    @Test
    fun aProFieldsCardStaysPro() {
        val query = "why does electron affinity increase across a period"
        assertEquals(ChatCardKind.TREND_LINE, ChatFixture.chat().ask(query).card?.kind)
        val free = ChatFixture.chat(entitlements = com.jlindemann.science.ai.core.Entitlements(isPro = false, isProPlus = false)).ask(query)
        assertEquals(Intent.EXPLANATION, free.plan?.intent)
        assertEquals(null, free.card)
    }

    @Test
    fun aDefinitionIsNotAWhy() {
        assertNotEquals(Intent.EXPLANATION, ChatFixture.chat().ask("what is electronegativity").plan?.intent)
    }
}
