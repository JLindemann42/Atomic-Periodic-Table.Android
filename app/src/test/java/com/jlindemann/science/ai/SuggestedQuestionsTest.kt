package com.jlindemann.science.ai

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The suggestion chips are the one piece of agent text that is formatted by hand.
 *
 * `Resources.getStringArray` does not substitute arguments, so the caller runs `String.format` over
 * every item. That puts two failure modes in a translator's hands which no other agent string has:
 * an item that drops its `%1$s` shows an element chip with no element in it, and an item carrying a
 * literal, unescaped `%` throws `UnknownFormatConversionException` at the moment the chat opens.
 */
class SuggestedQuestionsTest {

    /** The twelve folders that carry the agent strings; region variants resolve upward. */
    private val locales = listOf(
        "values", "values-sv-rSE", "values-de", "values-es", "values-fr", "values-it-rIT",
        "values-pt-rBR", "values-af", "values-b+fil", "values-hi", "values-ur", "values-zh-rCN"
    )

    private val resDir: File by lazy {
        listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    }

    /** `%1`, `%2`… not followed by a conversion. Android renders these verbatim. */
    private val bareIndex = Regex("%\\d(?!\\$)")

    /** The element-name argument every chip in `ai_suggest_element` must carry exactly once. */
    private val elementPlaceholder = Regex("%1\\\$s")

    private val arrayBlock =
        Regex("""<string-array name="([^"]+)"\s*>(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
    private val item = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)

    private fun items(folder: String, name: String): List<String> {
        val file = File(resDir, "$folder/strings.xml")
        if (!file.isFile) return emptyList()
        val block = arrayBlock.findAll(file.readText(Charsets.UTF_8))
            .firstOrNull { it.groupValues[1] == name } ?: return emptyList()
        return item.findAll(block.groupValues[2]).map { it.groupValues[1].trim() }.toList()
    }

    @Test
    fun everyLocaleOffersEnoughChipsToFillTheRow() {
        assumeTrue("res/ not reachable", resDir.isDirectory)
        val problems = mutableListOf<String>()
        for (folder in locales) {
            for (name in listOf("ai_suggest_element", "ai_suggest_trends", "ai_suggest_general", "ai_suggest_tools")) {
                val found = items(folder, name)
                // Three chips are shown, so anything under four leaves the row without a choice.
                if (found.size < 4) problems.add("$folder/$name has ${found.size} items")
            }
        }
        assertTrue("Suggestion arrays too short: $problems", problems.isEmpty())
    }

    @Test
    fun everyElementChipCarriesExactlyOneElementPlaceholder() {
        assumeTrue("res/ not reachable", resDir.isDirectory)
        val problems = mutableListOf<String>()
        for (folder in locales) {
            for (text in items(folder, "ai_suggest_element")) {
                val count = elementPlaceholder.findAll(text).count()
                if (count != 1) problems.add("$folder: \"$text\" has $count placeholders")
                if (bareIndex.containsMatchIn(text)) problems.add("$folder: \"$text\" has a bare %n")
            }
        }
        assertTrue("Element suggestion chips are malformed: $problems", problems.isEmpty())
    }

    /**
     * The chips are formatted at the moment the panel opens, so a bad `%` is a crash on launch
     * rather than a wrong sentence somewhere down a conversation.
     */
    @Test
    fun everyChipSurvivesFormatting() {
        assumeTrue("res/ not reachable", resDir.isDirectory)
        val problems = mutableListOf<String>()
        for (folder in locales) {
            for (name in listOf("ai_suggest_element", "ai_suggest_trends", "ai_suggest_general", "ai_suggest_tools")) {
                for (text in items(folder, name)) {
                    runCatching { String.format(Locale.ROOT, text, "Gold") }
                        .onFailure { problems.add("$folder/$name: \"$text\" -> ${it::class.simpleName}") }
                }
            }
        }
        assertTrue("Suggestion chips throw when formatted: $problems", problems.isEmpty())
    }
}
