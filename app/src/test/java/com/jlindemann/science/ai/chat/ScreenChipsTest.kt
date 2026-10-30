package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** A chat opened from a screen knows where it came from (roadmap 2.9). */
class ScreenChipsTest {

    @Before
    fun setUp() {
        assumeTrue(ChatFixture.available)
    }

    private val chips get() = ChatFixture.service.screenChips

    @Test
    fun everyScreenHasEnglishChips() {
        val screens = Screens::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.get(null) as String }
        assertTrue(screens.toString(), screens.size >= 17)
        for (screen in screens) assertEquals(screen, 2, chips.forScreen(screen, "en").size)
    }

    @Test
    fun aScreensChipsComeFirst() {
        val chat = ChatFixture.chat()
        val general = chat.suggestions()
        chat.openedFrom(Screens.IDEAL_GAS)
        val opened = chat.suggestions()
        assertEquals(chips.forScreen(Screens.IDEAL_GAS, "en"), opened.take(2))
        assertTrue(opened.size == 3 && opened[2] in general)
        // A new thread is still opened from the same screen.
        chat.reset()
        assertEquals(opened, chat.suggestions())
    }

    @Test
    fun anElementScreenFocusesItsElement() {
        val chat = ChatFixture.chat()
        chat.openedFrom(Screens.NUCLIDES, "carbon")
        val reply = chat.ask("what is its density")
        assertTrue(reply.text, reply.text.contains("Carbon"))
    }

    @Test
    fun aLanguageWithoutChipsGetsTheGeneralOnes() {
        val chat = ChatFixture.chat()
        val general = chat.suggestions()
        chat.openedFrom("no_such_screen")
        assertEquals(general, chat.suggestions())
    }

    /**
     * The promise the follow-up chips keep, for these too: in every language, every chip a screen
     * offers is a question the assistant answers. Prints the chips that fail, all at once.
     */
    @Test
    fun everyChipIsAnsweredInItsLanguage() {
        val failures = ArrayList<String>()
        for ((screen, language) in chips.keys.sortedBy { it.toString() }) {
            val noData = ChatFixture.strings(language).get(R.string.ai_no_data)
            for ((i, chip) in chips.forScreen(screen, language).withIndex()) {
                val chat = ChatFixture.chat(language = language).apply { openedFrom(screen) }
                val answer = chat.ask(chip)
                if (answer.declineReason != null || answer.text == noData) {
                    failures.add("$screen/$language#$i: $chip -> ${answer.declineReason}: ${answer.text.take(80)}")
                }
            }
        }
        failures.forEach(::println)
        assertNull(failures.joinToString("\n").ifEmpty { null })
    }
}
