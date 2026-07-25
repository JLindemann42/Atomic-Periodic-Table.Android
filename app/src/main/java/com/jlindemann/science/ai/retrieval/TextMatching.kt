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
            val regex = "\\b${Regex.escape(keyword)}\\b".toRegex()
            if (regex.containsMatchIn(lowerQuery)) return true
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
            val regex = "\\b${Regex.escape(token)}\\b".toRegex()
            regex.containsMatchIn(rawQuery) || regex.containsMatchIn(normalizedQuery)
        } else {
            rawQuery.contains(token) || normalizedQuery.contains(token)
        }
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
