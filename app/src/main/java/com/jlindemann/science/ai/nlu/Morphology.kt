package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.retrieval.TextMatching

/**
 * Words built from other words, written as the words they are made of (roadmap 1.5).
 *
 * Swedish, German and Afrikaans write a property and what is asked about it as one word:
 * "smältpunktsskillnaden" is the melting-point difference, "Durchschnittsdichte" the average
 * density. Afrikaans makes a superlative with -ste and Filipino with pinaka-, so "hardste" and
 * "pinakamatigas" are the superlative and its property in one word too. Every matcher downstream
 * looks for whole words, so each of these lost the property, or the superlative, or both.
 *
 * The planner runs [expand] on the query first. Three rules, each taking a word apart only into
 * parts it knows, and leaving alone any word that is already known with its property:
 *
 *  - **Compounds.** A word that is no known word on its own is split in two when the first part is
 *    a field's name or a statistic, the rest is a field's name, a statistic or a [Lexicon.COMPOUND_HEADS]
 *    word with a short ending, and at most a linking `s` stands between them: "smältpunkts|skillnaden",
 *    "Durchschnitts|dichte", "medel|tätheten".
 *  - **-ste.** "ligste" is written as the superlative [Lexicon.SUPERLATIVE_STEMS] knows its stem by,
 *    "lightest"; a stem that is a field's name becomes "most" and the stem.
 *  - **pinaka-.** The same for the prefix, after the hyphen and the linker are dropped: "pinakamababang"
 *    is the listed "pinakamababa", "pinakaelectronegative" is "most electronegative".
 *
 * A rewritten word is written folded, which is how every matcher reads it anyway. Words with digits
 * are never touched, so the formulas and quantities the executor re-reads stay as they were written.
 */
class Morphology(private val fields: FieldResolver) {

    /** [query] with every compound and affixed superlative written as its parts. */
    fun expand(query: String): String = TOKEN.replace(query) { m -> rewrite(m.value) ?: m.value }

    private fun rewrite(token: String): String? {
        val start = token.indexOfFirst { it.isLetter() }
        val end = token.indexOfLast { it.isLetter() }
        if (start < 0) return null
        val core = token.substring(start, end + 1)
        if (core.any { !it.isLetter() && it != '-' }) return null
        val folded = TextMatching.normalizeForLookup(core)
        val rewritten = pinaka(folded) ?: superlativeSuffix(folded) ?: decompound(folded) ?: return null
        return token.substring(0, start) + rewritten + token.substring(end + 1)
    }

    private fun pinaka(word: String): String? {
        if (!word.startsWith(PINAKA) || fields.isFieldWord(word)) return null
        val body = word.removePrefix(PINAKA).removePrefix("-")
        if (body.isEmpty()) return null
        // The linker joins an adjective to the noun after it: -ng after a vowel ("mababang"), -g after
        // an n ("magaang").
        val stems = listOfNotNull(
            body,
            body.removeSuffix("ng").takeIf { body.endsWith("ng") },
            body.removeSuffix("g").takeIf { body.endsWith("ng") }
        )
        for (stem in stems) {
            val whole = PINAKA + stem
            if (whole != word && isListed(whole)) return whole
            Lexicon.SUPERLATIVE_STEMS[stem]?.let { return it }
            if (fields.isFieldWord(stem)) return "$MOST $stem"
        }
        return null
    }

    private fun superlativeSuffix(word: String): String? {
        if (!word.endsWith(STE) || word.length <= STE.length + 2 || fields.isFieldWord(word)) return null
        val stem = word.dropLast(STE.length)
        // A table stem wins even over a listed form: "ligste" is listed as least, but with no
        // property. A field's name as the stem only when the whole is not listed, so German
        // "dichteste" stays as it is.
        Lexicon.SUPERLATIVE_STEMS[stem]?.let { return it }
        return if (stem.length >= MIN_PART && word !in MARKERS && fields.isFieldWord(stem)) "$MOST $stem" else null
    }

    private fun decompound(word: String): String? {
        if (word.length < MIN_COMPOUND || !word.all(Char::isLetter) || isWord(word)) return null
        // The longest first part first, so "smältpunkts|skillnaden" is not tried as "smält|...".
        for (split in word.length - MIN_PART downTo MIN_PART) {
            val first = word.substring(0, split)
            if (!isModifier(first)) continue
            for (link in LINKS) {
                if (!word.startsWith(link, split)) continue
                val rest = word.substring(split + link.length)
                if (rest.length >= MIN_PART && isWord(rest)) return "$first $rest"
            }
        }
        return null
    }

    /** A word that may open a compound: a field's name or a statistic, exactly. */
    private fun isModifier(part: String): Boolean = fields.isFieldWord(part) || part in STATISTICS

    /** A word that may close one: any known word, with a short ending. */
    private fun isWord(part: String): Boolean =
        fields.knowsWord(part) || CLOSERS.any { part.startsWith(it) && part.length - it.length <= MAX_ENDING }

    private fun isListed(word: String): Boolean = word in MARKERS || fields.isFieldWord(word)

    private companion object {
        const val PINAKA = "pinaka"
        const val STE = "ste"

        /** The canonical "most", which [Lexicon.MOST] knows in every language. */
        const val MOST = "most"

        /** Shortest word that may be a compound, and shortest part of one. */
        const val MIN_COMPOUND = 8
        const val MIN_PART = 4

        /** Longest grammatical ending on the last part: "skillnad" + "en". */
        const val MAX_ENDING = 3

        /** What may stand between the parts: nothing, or the Germanic linking s. */
        val LINKS = listOf("", "s")

        val TOKEN = Regex("""\S+""")

        val STATISTICS: Set<String> =
            (Lexicon.AVERAGE + Lexicon.MEDIAN + Lexicon.SUM).filterTo(HashSet()) { ' ' !in it && it.length >= MIN_PART }

        val CLOSERS: List<String> = (STATISTICS + Lexicon.COMPOUND_HEADS).sortedByDescending { it.length }

        val MARKERS: Set<String> = (Lexicon.MOST + Lexicon.LEAST).toHashSet()
    }
}
