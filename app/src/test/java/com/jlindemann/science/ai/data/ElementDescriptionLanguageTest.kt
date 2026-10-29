package com.jlindemann.science.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Every shipped description is in its own language (roadmap 1.10).
 *
 * Narrative answers and the overview are cut from the description sentence by sentence, and an
 * English sentence in another language's description is dropped or, with nothing else left, answered
 * in English. Afrikaans, Filipino and Swedish were left part-English - "Det är one of the least
 * reactive chemical elements" - and French, Hindi, Italian and Portuguese kept a few English rows;
 * 277 descriptions in all. The rule is the shared data's `tools/element_descriptions.py`: four
 * English function words make an English clause, not counting the ones that are words of the
 * language itself.
 */
class ElementDescriptionLanguageTest {

    // (?U): Java's \b is ASCII by default, and would count the "it" in Portuguese "itérbio".
    private val englishFunctionWords = Regex(
        "(?U)\\b(the|and|which|with|has|have|is|are|was|were|been|of|from|that|its|it|only|also|" +
                "most|used|found|other|than|but|such|these|those|there|their|when|while|into|about)\\b"
    )

    /** Afrikaans writes "Goud is 'n chemiese element", "dit was" and "of" (or). */
    private val ownWords = mapOf("af" to setOf("is", "was", "of"))

    @Test
    fun noDescriptionIsPartEnglish() {
        assumeTrue(TestAssets.available())
        val partEnglish = TestAssets.availableLanguages().filter { it != "en" }.flatMap { language ->
            val own = ownWords[language].orEmpty()
            TestAssets.elementTable(language).rows.mapNotNull { (key, row) ->
                val description = (row["description"] as? String).orEmpty().lowercase()
                val english = englishFunctionWords.findAll(description).count { it.value !in own }
                "$language/$key".takeIf { english >= 4 }
            }
        }
        assertEquals(emptyList<String>(), partEnglish)
    }

    @Test
    fun theSymbolIsNotTranslated() {
        assumeTrue(TestAssets.available())
        // Nobelium's symbol is the English word "no", and three locales translated it.
        for (language in TestAssets.availableLanguages()) {
            val description = TestAssets.elementTable(language).rows["nobelium"]?.get("description") as? String
            assumeTrue(description != null)
            assertEquals(language, true, Regex("(?<![A-Za-z])No(?![a-z])").containsMatchIn(description!!))
        }
    }
}
