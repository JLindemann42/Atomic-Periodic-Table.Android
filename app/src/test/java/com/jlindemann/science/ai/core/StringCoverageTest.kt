package com.jlindemann.science.ai.core

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Every agent string a locale is missing falls back to English — mid-sentence, mid-answer.
 *
 * That is the mechanism behind the half-Swedish answers: 97 of the 319 `ai_*` strings had no
 * Swedish value, so a Swedish question was answered with a Swedish template around English
 * fragments, and the "Open Gold" chip was always English because `ai_open_in_app` was one of them.
 * Nothing caught it: Android's `MissingTranslation` lint is not enforced here (the baseline holds
 * zero entries), and the fallback is silent by design.
 *
 * This is the regression net. It reports the whole gap per locale rather than the first missing key,
 * because the number is the useful part — it is what tells you whether the gap is closing.
 */
class StringCoverageTest {

    /**
     * Locales that ship a full translation and are expected to carry the agent strings.
     *
     * Region variants are deliberately excluded: `values-es-rAR` exists for a handful of regional
     * words, and Android resolves the rest through the language folder. They are asserted separately
     * by [regionVariantsResolveThroughTheirBaseLanguage].
     */
    private val fullLocales = listOf(
        "values-sv-rSE", "values-de", "values-fr", "values-es-rES", "values-it-rIT",
        "values-pt-rBR", "values-af", "values-b+fil", "values-hi", "values-ur-rPK", "values-zh-rCN"
    )

    private val resDir: File by lazy {
        listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    }

    private fun keys(folder: String): Set<String> {
        val file = File(resDir, "$folder/strings.xml")
        if (!file.isFile) return emptySet()
        return NAME.findAll(file.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toSet()
    }

    private fun agentKeys(folder: String): Set<String> = keys(folder).filter { it.startsWith("ai_") }.toSet()

    /**
     * Every agent key a locale can actually resolve, including through its base language.
     *
     * Android walks upward — `values-es-rES` then `values-es` then `values` — so a string held in
     * the base folder is reachable from every region of that language. Modelling that is the point:
     * the Spanish and Urdu agent strings live in `values-es` and `values-ur` precisely so their
     * region folders stop falling through to English.
     */
    private fun reachableAgentKeys(folder: String): Set<String> {
        val base = folder.substringBefore("-r").takeIf { it != folder }
        return agentKeys(folder) + (base?.let { agentKeys(it) } ?: emptySet())
    }

    @Test
    fun everyShippedLocaleTranslatesEveryAgentString() {
        assumeTrue("res/ not reachable", resDir.isDirectory)
        val english = agentKeys("values")
        assertTrue("no ai_* strings found in values/", english.size > 100)

        val report = StringBuilder()
        var worstGap = 0
        for (folder in fullLocales) {
            val missing = (english - reachableAgentKeys(folder)).sorted()
            if (missing.isEmpty()) continue
            worstGap = maxOf(worstGap, missing.size)
            report.append("\n  $folder is missing ${missing.size}/${english.size}: ")
                .append(missing.take(12).joinToString(", "))
                .append(if (missing.size > 12) ", …" else "")
        }
        assertTrue(
            "Agent strings missing from shipped locales; each one falls back to English inside an " +
                    "otherwise translated answer.$report",
            worstGap == 0
        )
    }

    /**
     * A region folder must not be the only place a language's strings live.
     *
     * `values-es-rAR` and `values-es-rMX` hold ~700 strings each while `values-es-rES` holds ~945,
     * and there is no `values-es`, so Android has nowhere to fall back to except English — an
     * Argentinian user was missing 248 agent strings, not 71. The fix is a base language folder.
     */
    @Test
    fun regionVariantsResolveThroughTheirBaseLanguage() {
        assumeTrue("res/ not reachable", resDir.isDirectory)
        val english = agentKeys("values")
        val problems = mutableListOf<String>()
        for ((base, variants) in mapOf(
            "values-es" to listOf("values-es-rAR", "values-es-rMX", "values-es-rES"),
            "values-ur" to listOf("values-ur-rIN", "values-ur-rPK")
        )) {
            val baseKeys = agentKeys(base)
            assertTrue(
                "$base holds no agent strings, so its region folders resolve to English",
                baseKeys.isNotEmpty()
            )
            for (variant in variants) {
                val reachable = baseKeys + agentKeys(variant)
                val missing = english - reachable
                if (missing.isNotEmpty()) {
                    problems.add("$variant resolves ${reachable.size}/${english.size} (via $base)")
                }
            }
        }
        assertTrue(
            "Region variants fall back to English rather than to their own language: $problems",
            problems.isEmpty()
        )
    }

    /** An empty locale folder is dead weight that reads as a supported language. */
    @Test
    fun noLocaleFolderIsEmpty() {
        assumeTrue("res/ not reachable", resDir.isDirectory)
        val empty = resDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            // API-level and night-mode qualifiers legitimately carry no strings.
            .filterNot { it.name.matches(Regex("values-v\\d+")) || it.name == "values-night" }
            .filter { it.listFiles().isNullOrEmpty() }
            .map { it.name }
        assertTrue("empty locale folders: $empty", empty.isEmpty())
    }

    private companion object {
        val NAME = Regex("""<string name="([^"]+)"""")
    }
}
