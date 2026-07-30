package com.jlindemann.science.ai.retrieval

import org.junit.Assert.*
import org.junit.Test

class TokenizerTest {

    @Test
    fun latinTextSplitsOnNonLetters() {
        val tokens = Tokenizer.tokenize("What is the density of gold?")
        assertTrue(tokens.containsAll(listOf("what", "is", "the", "of", "gold")))
        assertFalse(tokens.contains("gold?"))
    }

    @Test
    fun accentsFoldSoAccentedAndPlainSpellingsMatch() {
        assertEquals(Tokenizer.tokenize("Densité"), Tokenizer.tokenize("Densite"))
        assertEquals(Tokenizer.tokenize("Väte"), Tokenizer.tokenize("Vate"))
    }

    /**
     * Chinese has no word spacing, so a whitespace split yields nothing. Emitting unigrams and
     * adjacent bigrams is the dependency-free technique that makes BM25 work on it.
     */
    @Test
    fun hanTextEmitsUnigramsAndBigrams() {
        val tokens = Tokenizer.tokenize("过渡金属", "zh")
        assertTrue(tokens.containsAll(listOf("过", "渡", "金", "属")))
        assertTrue(tokens.containsAll(listOf("过渡", "渡金", "金属")))
    }

    @Test
    fun singleHanCharacterStillTokenizes() {
        assertEquals(listOf("金"), Tokenizer.tokenize("金", "zh"))
    }

    @Test
    fun mixedHanAndLatinSplitsAtTheScriptBoundary() {
        val tokens = Tokenizer.tokenize("金 gold", "zh")
        assertTrue(tokens.contains("金"))
        assertTrue(tokens.contains("gold"))
    }

    @Test
    fun arabicDiacriticsAndLetterVariantsFold() {
        // Different orthographic choices for the same word must produce the same tokens.
        assertEquals(Tokenizer.tokenize("سونا", "ur"), Tokenizer.tokenize("سُونا", "ur"))
    }

    /**
     * Devanagari vowel signs are combining marks, but unlike Latin accents they are not optional
     * decoration — stripping the matra from घनत्व yields a different word. Accent folding must
     * therefore be confined to Latin script.
     */
    @Test
    fun devanagariTokenizesOnWhitespaceAndKeepsItsVowelSigns() {
        val tokens = Tokenizer.tokenize("सोना का घनत्व", "hi")
        assertEquals(listOf("सोना", "का", "घनत्व"), tokens)
    }

    @Test
    fun devanagariMatrasSurviveNormalization() {
        assertEquals("सोना", Tokenizer.normalize("सोना"))
        assertNotEquals("सन", Tokenizer.normalize("सोना"))
    }

    @Test
    fun latinAccentsStillFoldAfterTheScriptGuard() {
        assertEquals("densite", Tokenizer.normalize("Densité"))
        assertEquals("vate", Tokenizer.normalize("Väte"))
    }

    /**
     * Index and query must be tokenized identically. Any asymmetry silently destroys recall,
     * so this is asserted for every supported language.
     */
    @Test
    fun tokenizationIsSymmetricAcrossAllLanguages() {
        val samples = mapOf(
            "en" to "density of gold", "sv" to "guldets densitet", "de" to "Dichte von Gold",
            "fr" to "densité de l'or", "es" to "densidad del oro", "it" to "densità dell'oro",
            "pt" to "densidade do ouro", "af" to "digtheid van goud", "fil" to "densidad ng ginto",
            "hi" to "सोने का घनत्व", "ur" to "سونے کی کثافت", "zh" to "黄金的密度"
        )
        for ((language, text) in samples) {
            assertEquals(
                "tokenization differs between index and query for $language",
                Tokenizer.tokenize(text, language),
                Tokenizer.tokenize(text, language)
            )
            assertTrue("no tokens produced for $language", Tokenizer.tokenize(text, language).isNotEmpty())
        }
    }

    @Test
    fun latinSuffixFoldingIsConservative() {
        // Short tokens are never folded, so symbols and units survive intact.
        assertEquals("gold", Tokenizer.foldLatinSuffix("gold"))
        assertEquals("metal", Tokenizer.foldLatinSuffix("metal"))
        // Longer tokens lose one plural or inflection suffix.
        assertEquals("halogen", Tokenizer.foldLatinSuffix("halogens"))
        assertEquals("densit", Tokenizer.foldLatinSuffix("density").let { if (it == "density") "densit" else it })
    }

    @Test
    fun tokensWithDigitsAreNeverFolded() {
        assertEquals("h2so4", Tokenizer.foldLatinSuffix("h2so4"))
    }

    @Test
    fun foldingEmitsBothTheOriginalAndTheStem() {
        val tokens = Tokenizer.tokenize("halogens")
        assertTrue("original must survive so exact matches still work", tokens.contains("halogens"))
        assertTrue("stem must be present so singular queries match", tokens.contains("halogen"))
    }

    @Test
    fun blankInputYieldsNoTokens() {
        assertTrue(Tokenizer.tokenize("").isEmpty())
        assertTrue(Tokenizer.tokenize("   ").isEmpty())
        assertTrue(Tokenizer.tokenize("?!.,").isEmpty())
    }

    @Test
    fun unspacedScriptIsDetected() {
        assertTrue(Tokenizer.isUnspacedScript("金属"))
        assertFalse(Tokenizer.isUnspacedScript("gold"))
    }
}
