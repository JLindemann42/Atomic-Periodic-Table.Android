package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** A calculation's working, step by step (roadmap 2.2). */
class StepsTest {

    @Before
    fun setUp() {
        assumeTrue(ChatFixture.available)
    }

    private fun contains(text: String, vararg lines: String) {
        for (line in lines) assertTrue("missing \"$line\" in:\n$text", text.contains(line))
    }

    @Test
    fun aBalanceShowsTheEquationsItSolves() {
        val text = ChatFixture.chat().ask("balance Fe + O2 -> Fe2O3 step by step").text
        contains(
            text, "### Step by step", "a Fe + b O2 → c Fe2O3", "Fe: a = 2c", "O: 2b = 3c",
            "a = 4, b = 3, c = 2", "**Fe** — 4 on the left, 4 on the right"
        )
    }

    @Test
    fun theStepsReplaceTheShortWorkingRatherThanRepeatIt() {
        val brief = ChatFixture.chat().ask("balance Fe + O2 -> Fe2O3").text
        val full = ChatFixture.chat().ask("balance Fe + O2 -> Fe2O3 step by step").text
        assertFalse(brief.contains("### Step by step"))
        // The atom check moves into the steps, as their last one, rather than appearing twice.
        assertTrue(brief.contains("### Atom check"))
        assertFalse(full.contains("### Atom check"))
        assertEquals(2, Regex("on the left, ").findAll(full).count())
    }

    @Test
    fun aSolutionAfterwardsShowsEachFormulaWithItsNumbers() {
        val chat = ChatFixture.chat()
        val answer = chat.ask("How many grams of NaCl for 250 mL of 0.5 mol/L?")
        assertTrue(answer.suggestions.toString(), "Show the steps" in answer.suggestions)
        val text = chat.ask("show the steps").text
        contains(
            text, "V = 250 mL = 0.25 L", "n = c × V = 0.5 mol/L × 0.25 L = 0.125 mol",
            "m = n × M = 0.125 mol × 58.44 g/mol = 7.305 g"
        )
    }

    @Test
    fun aDecayShowsTheHalfLivesAndTheFraction() {
        val chat = ChatFixture.chat()
        chat.ask("How much of 100 g of carbon-14 is left after 11460 years?")
        contains(chat.ask("show the steps").text, "t½ =", "n = t ÷ t½ = 11460 yr ÷", "N = N₀ × (½)ⁿ = 100 g ×")
    }

    @Test
    fun molesShowAvogadro() {
        contains(
            ChatFixture.chat().ask("how many atoms are in 2.5 moles of iron, step by step").text,
            "N = n × N_A = 2.5 mol × 6.022 × 10²³ /mol = 1.506 × 10²⁴"
        )
    }

    @Test
    fun aMolarMassShowsEachElementsShare() {
        contains(ChatFixture.chat().ask("molar mass of H2O step by step").text, "H: 2 × 1.008 g/mol = 2.016 g/mol", "M(H2O) =")
    }

    @Test
    fun theStepsAreNotOfferedTwice() {
        val chat = ChatFixture.chat()
        chat.ask("balance Fe + O2 -> Fe2O3")
        assertFalse("Show the steps" in chat.ask("show the steps").suggestions)
    }

    /** Each language's own balancing chip, then its own steps chip, in its own words. */
    @Test
    fun everyLanguageShowsTheSteps() {
        for (language in TestAssets.availableLanguages()) {
            val strings = ChatFixture.strings(language)
            val chat = ChatFixture.chat(language = language)
            chat.ask(strings.array(R.array.ai_suggest_tools)[0])
            val text = chat.ask(strings.get(R.string.ai_follow_steps)).text
            assertTrue("$language:\n$text", text.contains("### " + strings.get(R.string.ai_steps_header)))
            assertTrue("$language:\n$text", text.contains("a Fe + b O2 → c Fe2O3"))
        }
    }
}
