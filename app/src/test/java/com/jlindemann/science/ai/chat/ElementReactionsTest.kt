package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** What an element reacts with, by where it sits. Ported from the Windows `ElementReactionsTest`. */
class ElementReactionsTest {

    @Before fun setUp() = assumeTrue(ChatFixture.available)

    private fun ask(query: String) = ChatFixture.chat().ask(query).text
    private fun s(id: Int, vararg args: Any) = ChatFixture.strings().get(id, *args)

    @Test fun theseAreAnswered() {
        for (query in listOf(
            "what happens when sodium reacts with water",
            "what happens if I mix sodium and chlorine",
            "does helium react with anything"
        )) {
            assertNotEquals(query, ChatFixture.noData, ask(query))
        }
    }

    @Test fun anAlkaliMetalInWaterReactsViolently() =
        assertEquals(s(R.string.ai_reaction_alkali_water, "Sodium"), ask("what happens when sodium reacts with water"))

    @Test fun magnesiumGetsItsOwnAnswer() =
        assertEquals(s(R.string.ai_reaction_magnesium_water, "Magnesium"), ask("what happens when magnesium reacts with water"))

    /** Chlorine is classified a reactive nonmetal, so the halogen test is on group 17. */
    @Test fun anAlkaliMetalAndAHalogenMakeASalt() =
        assertEquals(s(R.string.ai_reaction_alkali_halogen, "Sodium", "Chlorine"), ask("what happens if I mix sodium and chlorine"))

    @Test fun aNobleGasSettlesThePair() {
        for (query in listOf("what happens when helium reacts with sodium", "what happens when sodium reacts with helium")) {
            assertEquals(query, s(R.string.ai_reaction_noble_gas, "Helium"), ask(query))
        }
    }

    @Test fun theFocusedElementAnswersABareQuestion() {
        val chat = ChatFixture.chat()
        chat.ask("what is the density of sodium")
        val text = chat.ask("what does it react with").text
        assertNotEquals(ChatFixture.noData, text)
        assertTrue(text, text.contains("Sodium", ignoreCase = true))
    }

    @Test fun withNoElementItFallsThrough() = assertEquals(ChatFixture.noData, ask("what reacts with water"))
}
