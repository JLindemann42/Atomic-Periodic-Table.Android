package com.jlindemann.science.ai.retrieval

import java.text.Normalizer

/**
 * Splits text into index terms for all twelve supported languages.
 *
 * The same entry point is used for indexing and for querying. That symmetry is not optional: any
 * transformation applied to one side and not the other silently destroys recall.
 *
 * Notable per-script handling:
 *  - **Han (zh)** has no spaces, so a whitespace split yields nothing useful. Emitting every
 *    character *and* every adjacent bigram is the standard dictionary-free CJK retrieval
 *    technique. BM25's IDF then does the discrimination for free: 金 ("gold") appears inside
 *    金属 ("metal") all over the corpus and sinks toward zero weight, while 金属 itself stays
 *    informative.
 *  - **Arabic script (ur)** is written with optional diacritics and several visually identical
 *    letter variants; normalising them is what makes a typed query match authored text.
 *  - **Latin** gets one light, language-agnostic suffix fold applied to both sides, so it can
 *    only ever help recall even where it is linguistically wrong.
 *
 * There is deliberately no stopword list. BM25 already discounts terms that appear in most
 * documents, which is more robust than a hand-maintained list in twelve languages.
 */
object Tokenizer {

    /** Suffixes folded off longer Latin-script tokens. Applied identically at index and query time. */
    private val LATIN_SUFFIXES = listOf("es", "en", "er", "s", "e")

    /** Arabic-script letters that vary by orthography but should match each other. */
    private val ARABIC_FOLD = mapOf(
        'ي' to 'ی', 'ك' to 'ک', 'ه' to 'ہ',
        'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا'
    )

    /** Arabic diacritics and the zero-width non-joiner, all stripped. */
    private fun isArabicDiacritic(c: Char): Boolean =
        (c in 'ً'..'ْ') || c == 'ٰ' || c == '‌' || c == 'ـ'

    /**
     * Tokenize a string into index terms.
     *
     * @param text the raw text
     * @param language ISO code; only affects Latin folding, since scripts are detected per character
     */
    fun tokenize(text: String, language: String = "en"): List<String> {
        if (text.isBlank()) return emptyList()
        val tokens = ArrayList<String>(text.length / 3 + 8)
        val buffer = StringBuilder()
        val hanRun = StringBuilder()

        fun flushLatin() {
            if (buffer.isEmpty()) return
            emitAlphabetic(buffer.toString(), language, tokens)
            buffer.setLength(0)
        }

        fun flushHan() {
            if (hanRun.isEmpty()) return
            emitHan(hanRun.toString(), tokens)
            hanRun.setLength(0)
        }

        for (raw in normalize(text)) {
            when {
                isHan(raw) -> {
                    flushLatin()
                    hanRun.append(raw)
                }
                // Combining marks belong to the token they follow. Devanagari vowel signs are
                // marks rather than letters, so treating them as delimiters would split घनत्व
                // into three fragments.
                raw.isLetterOrDigit() || isCombiningMark(raw) -> {
                    flushHan()
                    buffer.append(raw)
                }
                // Keep intra-token punctuation that carries meaning in formulas and units.
                raw == '/' || raw == '·' || raw == '^' -> {
                    flushHan()
                    flushLatin()
                }
                else -> {
                    flushHan()
                    flushLatin()
                }
            }
        }
        flushHan()
        flushLatin()
        return tokens
    }

    /**
     * Han text yields both unigrams and adjacent bigrams.
     * `过渡金属` becomes 过, 渡, 金, 属, 过渡, 渡金, 金属.
     */
    private fun emitHan(run: String, into: MutableList<String>) {
        for (c in run) into.add(c.toString())
        for (i in 0 until run.length - 1) into.add(run.substring(i, i + 2))
    }

    private fun emitAlphabetic(token: String, language: String, into: MutableList<String>) {
        if (token.isEmpty()) return
        val cleaned = if (isArabicScript(token)) foldArabic(token) else token
        if (cleaned.isEmpty()) return
        into.add(cleaned)
        val folded = foldLatinSuffix(cleaned, language)
        if (folded != cleaned) into.add(folded)
    }

    /**
     * NFKC, lowercase, and fold accents.
     *
     * Combining marks are dropped **only after Latin base letters**, so `é` folds to `e` while
     * Devanagari vowel signs survive: a matra is not optional decoration, and stripping it turns
     * घनत्व into a different word. Arabic harakat are dropped regardless, because they genuinely
     * are optional in Urdu orthography and users rarely type them.
     */
    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        val decomposed = Normalizer.normalize(nfkc, Normalizer.Form.NFD)
        var lastBaseWasLatin = false
        return buildString(decomposed.length) {
            for (c in decomposed) {
                if (isArabicDiacritic(c)) continue
                if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) {
                    if (lastBaseWasLatin) continue
                    append(c)
                    continue
                }
                lastBaseWasLatin = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN
                append(c)
            }
        }
    }

    /**
     * Fold one plural or inflection suffix off tokens longer than five characters.
     * Applied to index and query alike, so it never costs recall.
     */
    fun foldLatinSuffix(token: String, language: String = "en"): String {
        if (token.length <= 5) return token
        if (token.any { it.isDigit() }) return token
        for (suffix in LATIN_SUFFIXES) {
            if (token.length - suffix.length >= 4 && token.endsWith(suffix)) {
                return token.dropLast(suffix.length)
            }
        }
        return token
    }

    private fun foldArabic(token: String): String = buildString(token.length) {
        for (c in token) {
            if (isArabicDiacritic(c)) continue
            append(ARABIC_FOLD[c] ?: c)
        }
    }

    fun isHan(c: Char): Boolean =
        Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

    /** Non-spacing, spacing-combining and enclosing marks all attach to a preceding base letter. */
    private fun isCombiningMark(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }

    private fun isArabicScript(token: String): Boolean =
        token.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }

    /** True when the text is written in a script with no word spacing. */
    fun isUnspacedScript(text: String): Boolean = text.any { isHan(it) }
}
