package com.jlindemann.science.ai.retrieval

import org.junit.Assert.*
import org.junit.Test

/**
 * Word-boundary matching, and the portability rule that goes with it.
 *
 * This matching used to be a regex. A plain `\b` is ASCII-only, so accented letters read as
 * boundaries and element symbols matched inside ordinary words. The inline `(?U)` flag fixes
 * that on the JVM but Android's ICU engine rejects the pattern outright and the app crashed —
 * while these very tests passed, because they run on the JVM.
 *
 * The lesson is in [noRegexOnTheMatchingPath]: this path must not compile a regex at all, so
 * there is nothing left that can behave differently between the two engines.
 */
class WordBoundaryTest {

    @Test
    fun matchesWholeWordsOnly() {
        assertTrue(TextMatching.containsWord("what is the density of gold", "gold"))
        assertTrue(TextMatching.containsWord("gold", "gold"))
        assertTrue(TextMatching.containsWord("gold, silver", "gold"))
        assertFalse(TextMatching.containsWord("goldfish are orange", "gold"))
        assertFalse(TextMatching.containsWord("marigold", "gold"))
    }

    /** The bug that started this: accented letters are letters, not boundaries. */
    @Test
    fun accentedLettersAreNotWordBoundaries() {
        assertFalse("'sm' must not match inside smältpunkten", TextMatching.containsWord("vad är smältpunkten för guld", "sm"))
        assertFalse("'f' must not match inside för", TextMatching.containsWord("vad är smältpunkten för guld", "f"))
        assertTrue(TextMatching.containsWord("vad är smältpunkten för guld", "guld"))
        assertFalse(TextMatching.containsWord("die dichte von gold", "ich"))
        assertFalse(TextMatching.containsWord("quelle est la densité", "si"))
    }

    @Test
    fun nonLatinScriptsMatchAsWholeWords() {
        assertTrue(TextMatching.containsWord("सोने का घनत्व", "घनत्व"))
        assertTrue(TextMatching.containsWord("سونے کی کثافت", "کثافت"))
        assertFalse(TextMatching.containsWord("सोने का घनत्व", "घन"))
    }

    @Test
    fun punctuationAndDigitsDelimitCorrectly() {
        assertTrue(TextMatching.containsWord("gold's density", "gold"))
        assertTrue(TextMatching.containsWord("(gold)", "gold"))
        assertFalse(TextMatching.containsWord("gold2", "gold"))
        assertFalse(TextMatching.containsWord("2gold", "gold"))
        assertFalse(TextMatching.containsWord("gold_bar", "gold"))
    }

    @Test
    fun edgeCasesAreSafe() {
        assertFalse(TextMatching.containsWord("", "gold"))
        assertFalse(TextMatching.containsWord("gold", ""))
        assertFalse(TextMatching.containsWord("go", "gold"))
        assertTrue(TextMatching.containsWord("gold gold", "gold"))
    }

    /**
     * Regex-special characters must be matched literally. When this was a regex it needed
     * `Regex.escape`; a plain scan cannot misinterpret them at all.
     */
    @Test
    fun regexMetacharactersAreLiteral() {
        assertTrue(TextMatching.containsWord("the value is c++ today", "c++"))
        assertTrue(TextMatching.containsWord("a (b) c", "(b)"))
        assertFalse(TextMatching.containsWord("gold", "g.ld"))
    }

    /**
     * Guards the actual crash.
     *
     * `(?U)\b\Qaktinium\E\b` compiles on the JVM and throws PatternSyntaxException on Android,
     * so no JVM test could catch it by executing the matcher. Asserting the source contains no
     * regex construction on this path is what makes the difference visible here.
     */
    @Test
    fun noRegexOnTheMatchingPath() {
        val source = listOf(
            java.io.File("src/main/java/com/jlindemann/science/ai/retrieval/TextMatching.kt"),
            java.io.File("app/src/main/java/com/jlindemann/science/ai/retrieval/TextMatching.kt")
        ).firstOrNull { it.isFile } ?: return

        val body = source.readText()
            .lines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .joinToString("\n")

        val start = body.indexOf("fun containsWord")
        assertTrue("containsWord should exist", start >= 0)
        val scan = body.substring(start)
        assertFalse(
            "containsWord must not build a regex: Android's ICU engine and the JVM disagree on " +
                    "inline flags, and only the device would fail",
            scan.contains("toRegex()") || scan.contains("Regex(")
        )
        assertFalse("the (?U) inline flag crashes on Android", body.contains("(?U)"))
    }
}
