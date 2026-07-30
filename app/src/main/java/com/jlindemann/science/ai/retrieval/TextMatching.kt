package com.jlindemann.science.ai.retrieval

import java.text.Normalizer

/**
 * Shared string-matching helpers.
 *
 * These were private methods on `AIAgentManager`. The behaviour is preserved exactly, because
 * every intent predicate in the legacy router depends on it and `AIAgentManagerTest` asserts it
 * through reflection. `AIAgentManager` keeps one-line shims delegating here so that reflection
 * contract still holds.
 */
object TextMatching {

    /**
     * Lowercase, and strip combining marks **only where they are diacritics on Latin letters**, so
     * `Väte` and `vate` compare equal.
     *
     * The script test is not a nicety. In Latin script a combining mark is an accent, and dropping
     * it makes two spellings of the same word agree. In Devanagari it is a vowel — dropping it
     * leaves a bare consonant skeleton, and distinct words collapse onto each other. Hindi गold
     * `सोना` became `सन` and mercury `पारा` became `पर`, so the word for "atomic" (`परमाणु` →
     * `परमण`) began with mercury's skeleton and every Hindi question about atomic number resolved
     * two elements: mercury and whatever was actually being asked about.
     */
    fun normalizeForLookup(text: String): String {
        // German ß is a *letter*, not an accented s, so NFD leaves it alone and "außerdem" never
        // equalled the "ausserdem" the lexicon spells. Folding it here is the standard German
        // equivalence and is what the rest of the pipeline already assumes: `ClauseSplitter`
        // documents "außerdem folds to ausserdem", and the discourse marker it names went
        // unrecognised because it did not.
        val folded = text.lowercase().replace("ß", "ss")
        val nfd = Normalizer.normalize(folded, Normalizer.Form.NFD)
        val out = StringBuilder(nfd.length)
        var base: Char? = null
        for (ch in nfd) {
            if (isCombiningMark(ch)) {
                // Keep the mark unless it is decorating a Latin letter.
                if (base == null || !isLatinLetter(base)) out.append(ch)
            } else {
                base = ch
                out.append(ch)
            }
        }
        // Recompose. Stripping marks requires the decomposed form, but returning it is a trap for
        // every non-Latin script: Urdu "آئسوٹوپ" decomposes from seven characters to nine, so it no
        // longer equalled the lexicon entry spelled the way a user types it, and the whole isotope
        // vocabulary matched nothing. Latin has already lost its marks by this point, so
        // recomposition cannot undo the work above.
        return Normalizer.normalize(out, Normalizer.Form.NFC)
    }

    private fun isCombiningMark(ch: Char): Boolean = when (Character.getType(ch).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }

    private fun isLatinLetter(ch: Char): Boolean =
        ch.isLetter() && Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.LATIN

    /**
     * Split on anything that is not a letter, a combining mark or a digit.
     *
     * `\p{M}` is load-bearing for Indic scripts. A Devanagari vowel sign is a mark, not a letter,
     * so splitting on non-letters tore `सोने` into `स` and `न` — every Hindi word arrived at the
     * matchers as a handful of bare consonants.
     */
    fun splitQueryTokens(query: String): List<String> =
        query.split(Regex("[^\\p{L}\\p{M}0-9]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * Whether a query mentions a keyword, first by word boundary and then by edit distance.
     *
     * The fuzzy pass only considers query words longer than three characters, and scales the
     * tolerance with keyword length, so short symbols are never fuzzily matched.
     */
    fun hasKeyword(query: String, keywords: List<String>): Boolean {
        val lowerQuery = query.lowercase()

        for (keyword in keywords) {
            if (containsWord(lowerQuery, keyword)) return true
        }

        val words = lowerQuery.split(Regex("[^\\p{L}0-9]+")).filter { it.length > 3 }
        for (word in words) {
            for (keyword in keywords) {
                val threshold = when {
                    keyword.length > 8 -> 2
                    keyword.length > 3 -> 1
                    else -> 0
                }
                for (part in keyword.split(" ")) {
                    if (part.length > 3 && levenshtein(word, part) <= threshold) return true
                }
            }
        }
        return false
    }

    /**
     * Whether a token appears in the query as a whole word, in either spelling.
     *
     * The two spellings matter because normalisation folds Latin diacritics but leaves every other
     * script alone, so a token may be written one way and stored the other.
     *
     * Boundary handling is delegated wholesale to [containsWord], which knows that Han needs
     * containment and everything else needs boundaries. The earlier rule keyed off the token being
     * *ASCII-like* and fell back to bare containment for anything else, which quietly covered three
     * scripts that do write spaces: Portuguese "cúrio" (curium) is a substring of "mercúrio", Hindi
     * "रेनियम" (rhenium) of "यूरेनियम", Urdu "رینیم" of "یورینیم". Every query naming one of those
     * elements resolved two, and a plain lookup was answered as a comparison.
     */
    fun containsToken(rawQuery: String, normalizedQuery: String, token: String): Boolean {
        if (token.isBlank()) return false
        return containsWord(rawQuery, token) || containsWord(normalizedQuery, token)
    }

    /**
     * Whether [needle] occurs in [haystack] delimited by non-word characters on both sides.
     *
     * Deliberately not a regex. `\b` is defined over `[a-zA-Z0-9_]` unless the Unicode character
     * class flag is set, so a plain `\b` treats every accented letter as a boundary — `\bsm\b`
     * matches inside the Swedish "smältpunkten" and `\bf\b` inside "för", which made samarium
     * and fluorine appear in a query that named neither.
     *
     * The obvious fix, the inline `(?U)` flag, works on the JVM but **crashes on Android**: the
     * platform uses ICU's regex engine, which rejects it outright. Unit tests run against the
     * JVM and so passed while the app died. Scanning by hand is portable, allocation-free and
     * correct for every script, which removes the class of problem rather than the instance.
     */
    fun containsWord(haystack: String, needle: String): Boolean {
        if (needle.isEmpty() || needle.length > haystack.length) return false
        // Han writes no spaces, so every neighbouring character is a "word character" and the
        // boundary test can only ever succeed on a query that *is* the needle. That is why the
        // whole Chinese lexicon was unreachable in a sentence: 谁 in 谁发现了氦 has 发 after it, so
        // "who" was never recognised, and the same for 最, 族 and every degree word.
        if (isHanText(needle)) return haystack.contains(needle)
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            val before = haystack.getOrNull(index - 1)
            val after = haystack.getOrNull(index + needle.length)
            if (!isWordChar(before) && !isWordChar(after)) return true
            index = haystack.indexOf(needle, index + 1)
        }
        return false
    }

    /**
     * Unicode-aware: a letter, digit or combining mark in any script, plus the underscore.
     *
     * The mark is load-bearing, and for the same reason `\p{M}` is in [splitQueryTokens]: an Indic
     * vowel sign is not a letter, so treating it as a boundary made every matra a word break.
     * Hindi रेनियम (rhenium) sits inside यूरेनियम (uranium) preceded by ू — a mark — so rhenium
     * was found in every Hindi question about uranium, and the lookup became a comparison.
     */
    private fun isWordChar(c: Char?): Boolean =
        c != null && (c.isLetterOrDigit() || c == '_' || isCombiningMark(c))

    /** True when every letter of [text] is Han, so word boundaries do not apply to it. */
    private fun isHanText(text: String): Boolean {
        var sawHan = false
        for (ch in text) {
            if (!ch.isLetter()) continue
            if (Character.UnicodeScript.of(ch.code) != Character.UnicodeScript.HAN) return false
            sawHan = true
        }
        return sawHan
    }

    /** Levenshtein edit distance. */
    fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, minOf(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost))
            }
        }
        return dp[s1.length][s2.length]
    }

    /** Similarity in 0..1 derived from edit distance, for ranking fuzzy candidates. */
    fun similarity(a: String, b: String): Double {
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }
}
