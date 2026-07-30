package com.jlindemann.science.ai.corpus

import com.jlindemann.science.ai.core.TestStrings
import com.jlindemann.science.ai.data.TestAssets
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails — rather than skips — when the test fixtures are unreachable.
 *
 * Every other asset-backed test in the suite opens with `assumeTrue(TestAssets.available())`, which
 * means a wrong working directory turns the entire agent suite green while asserting nothing. This
 * is the one test that makes that state visible. If it fails, no other agent test result is
 * meaningful.
 */
class CorpusSmokeTest {

    @Test
    fun realElementAssetsAreReachable() {
        assertTrue(
            "Element assets not found. Unit tests must run with the module or repo root as the " +
                    "working directory; every asset-backed agent test is silently skipping.",
            TestAssets.available()
        )
    }

    @Test
    fun realStringsAreReachable() {
        assertTrue(
            "values/strings.xml not found; composed-answer assertions are silently skipping.",
            TestStrings.available
        )
    }

    @Test
    fun corpusIsReachable() {
        assertTrue(
            "Question corpus not found on the classpath or under app/src/test/resources/ai/corpus.",
            QuestionCorpus.available
        )
    }

    @Test
    fun everyCorpusFileParsesAndIsNonEmpty() {
        val empty = QuestionCorpus.FILES.filter { QuestionCorpus.cases(it).isEmpty() }
        assertTrue("corpus files missing or empty: $empty", empty.isEmpty())
    }

    @Test
    fun theAgentStackBuildsForEveryShippedLanguage() {
        val failed = CorpusHarness.languages.filter { lang ->
            runCatching { CorpusHarness.stackFor(lang) }.isFailure
        }
        assertTrue("agent stack failed to build for: $failed", failed.isEmpty())
    }
}
