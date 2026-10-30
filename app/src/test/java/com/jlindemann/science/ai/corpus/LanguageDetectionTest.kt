package com.jlindemann.science.ai.corpus

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The language detector (roadmap 1.4) against every row of the corpus, each of which declares the
 * language it is asked in.
 *
 * `language.tsv` is the gate for what detection must do. This is the check on what it must not:
 * the other files are thousands of real questions, short follow-ups and formulas among them, and
 * the cheapest way to break a thread is to answer one of its turns in another language.
 */
class LanguageDetectionTest {

    @Before
    fun setUp() {
        assumeTrue(CorpusHarness.available && QuestionCorpus.available)
        assumeTrue("no language model", CorpusHarness.languageDetector != null)
    }

    private val sessions: List<List<CorpusCase>> by lazy {
        (QuestionCorpus.FILES - "language").flatMap { QuestionCorpus.sessions(it) }
    }

    private val rows: List<CorpusCase> by lazy { sessions.flatten() }

    /** Asked in the language already in play, no row may switch away from it. */
    @Test
    fun noRowIsSwitchedAwayFromItsOwnLanguage() {
        val switched = rows.mapNotNull { case ->
            val detected = CorpusHarness.detectLanguage(case.query, case.lang)
            if (detected == case.lang) null else "${case.where} '${case.query}' ${case.lang} -> $detected"
        }
        assertTrue("${switched.size} rows switched language:\n  ${switched.joinToString("\n  ")}", switched.isEmpty())
    }

    /**
     * Replayed as conversations that open in English, the app's default, most rows must be
     * recognised: the first turn of each session starts from English, and each later one from the
     * language the turn before was asked in. A scoreboard with a floor rather than a gate on every
     * row, because a one-word greeting or a bare element name is rightly not enough to switch on.
     */
    @Test
    fun mostRowsAreRecognisedInConversationsOpenedInEnglish() {
        var recognised = 0
        for (session in sessions) {
            var active = "en"
            for (case in session) {
                if (CorpusHarness.detectLanguage(case.query, active) == case.lang) recognised++
                active = case.lang
            }
        }
        val share = recognised.toDouble() / rows.size
        println("[language] recognised: $recognised/${rows.size} (%.1f%%)".format(java.util.Locale.ROOT, 100 * share))
        assertTrue("only $recognised of ${rows.size} rows recognised", share >= MIN_RECOGNISED)
    }

    private companion object {
        /** Element names alone recognised 68% of the dev split; the trigram model, 97%. */
        const val MIN_RECOGNISED = 0.95
    }
}
