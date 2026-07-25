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

    /** Lowercase and strip combining marks, so `Väte` and `vate` compare equal. */
    fun normalizeForLookup(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{M}"), "")
    }

    /** Split on anything that is not a letter or digit. */
    fun splitQueryTokens(query: String): List<String> =
        query.split(Regex("[^\\p{L}0-9]+"))
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
     * Whether a token appears in the query as a whole word.
     * ASCII-like tokens use word boundaries; other scripts fall back to substring containment,
     * because `\b` is meaningless in an unspaced script.
     */
    fun containsToken(rawQuery: String, normalizedQuery: String, token: String): Boolean {
        if (token.isBlank()) return false
        val asciiLike = token.all { it.code < 128 && (it.isLetterOrDigit() || it == ' ' || it == '-') }
        return if (asciiLike) {
            containsWord(rawQuery, token) || containsWord(normalizedQuery, token)
        } else {
            rawQuery.contains(token) || normalizedQuery.contains(token)
        }
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
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            val before = haystack.getOrNull(index - 1)
            val after = haystack.getOrNull(index + needle.length)
            if (!isWordChar(before) && !isWordChar(after)) return true
            index = haystack.indexOf(needle, index + 1)
        }
        return false
    }

    /** Unicode-aware: a letter or digit in any script, plus the underscore. */
    private fun isWordChar(c: Char?): Boolean = c != null && (c.isLetterOrDigit() || c == '_')

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
