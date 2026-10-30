package com.jlindemann.science.ai.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How the language detector reads a message (roadmap 1.4). Windows' `LanguageDetectorTest` asserts
 * the same features, because both read one model: a trigram extracted differently is a model that
 * means something else.
 */
class LanguageDetectorTest {

    @Test
    fun wordsArePaddedAndLowercasedWithTheirAccents() {
        assertEquals(listOf(" va", "vad", "ad ", " är", "är "), LanguageDetector.features("Vad Är?"))
    }

    @Test
    fun formulasAreNoLanguagesWords() {
        assertEquals(emptyList<String>(), LanguageDetector.features("Ca(OH)2 + NaCl -> H2O"))
        // A lone capital is a word: the pronoun, or a symbol asked about by name.
        assertEquals(listOf(" fe", "fe "), LanguageDetector.features("Fe"))
    }

    @Test
    fun unitNamesCarryNoWeight() {
        assertEquals(listOf(" in", "in "), LanguageDetector.features("300 K in fahrenheit"))
    }

    @Test
    fun aHanCharacterIsAWord() {
        assertEquals(listOf(" 金 ", " 的 ", " 密 ", " 度 "), LanguageDetector.features("金的密度"))
        assertEquals(4, LanguageDetector.wordCount("金的密度"))
        assertEquals(3, LanguageDetector.wordCount("och i 42 fahrenheit"))
    }

    @Test
    fun aSpaceIsWrittenAsAnUnderscoreInTheModel() {
        val detector = LanguageDetector.parse("lang\ttrigram\tcount\nsv\t*\t10\nsv\t_hu\t3\nen\t*\t10\nen\t_ho\t3\n")!!
        assertEquals(listOf("en", "sv"), detector.languages)
        val scores = detector.scores("hur")
        assertEquals(0.0, scores.getValue("sv"), 0.0)
    }

    @Test
    fun noModelIsNoDetector() {
        assertNull(LanguageDetector.parse(null))
        assertNull(LanguageDetector.parse("# nothing\nlang\ttrigram\tcount\n"))
    }

    @Test
    fun oneWordIsJudgedByElementNamesAlone() {
        val detector = LanguageDetector.parse("lang\ttrigram\tcount\nsv\t*\t10\nsv\t_ok\t9\nen\t*\t10\n")!!
        assertEquals("en", detector.detect("ok", "en") { _, active -> active })
    }
}
