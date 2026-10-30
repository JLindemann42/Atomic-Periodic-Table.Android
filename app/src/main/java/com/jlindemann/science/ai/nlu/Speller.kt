package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.retrieval.FuzzyMatch
import com.jlindemann.science.ai.retrieval.TextMatching

/**
 * Corrects a misspelled word to the one vocabulary word within an edit or two (roadmap 1.6).
 *
 * Fuzzy matching used to cover element names only, and only for a query of one or two words, so a
 * typo in a property, a family or an element inside a sentence declined the question: "what is the
 * denisty of gold", "boiling point of nitrogn". The planner asks this only when a question would
 * otherwise be declined for want of a match, and keeps the correction only when the corrected
 * question is answered from the element data - see `QueryPlanner.plan`.
 *
 * The vocabulary is what the planner reads: element names in every language, the words of every
 * field alias, and the operator words of the lexicon, each with what it means. A word is corrected
 * only when
 *  - it is a Latin-script word of four letters or more that the vocabulary does not know, and that
 *    is not a stopword, a "yes, go on" word, a conversational word ([Lexicon.NEVER_CORRECTED]) or
 *    a word that asks for the personality layer;
 *  - the nearest vocabulary words are within one edit, or two for a word of eight letters or more,
 *    counting a transposition as one ("denisty" is one from "density");
 *  - and they all mean the same thing. "plomp" may become "plomb" or "plomo", both lead; a word
 *    between two meanings is left as it is.
 *
 * SymSpell finds its candidates through an index of deletions, which buys speed with memory. This
 * runs only on a decline, over a few thousand words, so it measures every candidate of a plausible
 * length instead.
 *
 * @param senses each vocabulary word, folded, and what it means: an element, a field, an operator
 * @param known further words that are never corrected
 */
class Speller(private val senses: Map<String, Set<String>>, known: Set<String>) {

    private val protected: Set<String> = known + senses.keys

    private val byLength: Map<Int, List<String>> = senses.keys.groupBy { it.length }

    /** [query] with each misspelled word replaced by its correction, or [query] itself. */
    fun correct(query: String): String = WORD.replace(query) { m -> correction(m.value) ?: m.value }

    private fun correction(word: String): String? {
        val folded = TextMatching.normalizeForLookup(word)
        if (folded.length < MIN_LENGTH || !folded.all(::isLatinLetter)) return null
        if (folded in protected || FuzzyMatch.isAffirmative(folded)) return null
        val maxEdits = if (folded.length >= LONG_WORD) 2 else 1

        var best = Int.MAX_VALUE
        val nearest = ArrayList<String>()
        for (length in folded.length - maxEdits..folded.length + maxEdits) {
            for (candidate in byLength[length].orEmpty()) {
                val distance = distance(folded, candidate, maxEdits)
                if (distance > maxEdits || distance > best) continue
                if (distance < best) { best = distance; nearest.clear() }
                nearest.add(candidate)
            }
        }
        if (nearest.isEmpty()) return null
        // One meaning, however many spellings of it are this close.
        val shared = nearest.map { senses.getValue(it) }.reduce { a, b -> a intersect b }
        return if (shared.isEmpty()) null else nearest.min()
    }

    companion object {
        /** Shorter words are left alone: "mas", "oro" and "fer" are each one edit from a dozen words. */
        const val MIN_LENGTH = 4

        /** From this length a word may be two edits from its correction. */
        const val LONG_WORD = 8

        private val WORD = Regex("""\p{L}+""")

        private fun isLatinLetter(c: Char): Boolean =
            c in 'a'..'z' || Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN

        /**
         * Optimal string alignment distance - Levenshtein with a transposition as one edit - or
         * [limit] + 1 as soon as it is certain to exceed it.
         */
        fun distance(a: String, b: String, limit: Int): Int {
            if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
            val rows = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) rows[i][0] = i
            for (j in 0..b.length) rows[0][j] = j
            for (i in 1..a.length) {
                var rowMin = Int.MAX_VALUE
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var d = minOf(rows[i - 1][j] + 1, rows[i][j - 1] + 1, rows[i - 1][j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d = minOf(d, rows[i - 2][j - 2] + 1)
                    rows[i][j] = d
                    rowMin = minOf(rowMin, d)
                }
                if (rowMin > limit) return limit + 1
            }
            return rows[a.length][b.length]
        }

        /**
         * The speller for a planner: element names from [elementNames], the words of every field
         * alias from [fieldWords], and the lexicon's operator words.
         */
        fun build(elementNames: Map<String, String>, fieldWords: Map<String, Set<String>>): Speller {
            val senses = HashMap<String, MutableSet<String>>()
            fun add(word: String, sense: String) {
                if (word.length >= MIN_LENGTH && word.all(::isLatinLetter)) senses.getOrPut(word) { HashSet() }.add(sense)
            }
            fun addPhrases(phrases: Iterable<String>, sense: String) {
                for (phrase in phrases) for (word in phrase.split(' ')) add(word, sense)
            }
            for ((name, key) in elementNames) add(name, "element:$key")
            for ((word, ids) in fieldWords) for (id in ids) add(word, "field:$id")
            addPhrases(Lexicon.MOST, "most")
            addPhrases(Lexicon.LEAST, "least")
            addPhrases(Lexicon.AVERAGE, "average")
            addPhrases(Lexicon.MEDIAN, "median")
            addPhrases(Lexicon.SUM, "sum")
            addPhrases(Lexicon.COUNT, "count")
            addPhrases(Lexicon.METAL_WORDS, "metal")
            for ((word, ids) in Lexicon.SERIES_WORDS) addPhrases(listOf(word), "series:${ids.sorted()}")
            for ((word, phase) in Lexicon.PHASE_WORDS) addPhrases(listOf(word), "phase:$phase")

            val known = HashSet<String>()
            Lexicon.STOPWORDS.values.forEach { list -> list.forEach { known.addAll(it.split(' ')) } }
            Lexicon.DEFER_TO_PERSONALITY.forEach { known.addAll(it.split(' ')) }
            known.addAll(Lexicon.NEVER_CORRECTED)
            // A word that is a stopword somewhere is no misspelling, but it is not a correction to
            // reach either: "more" may be no one's element.
            known.forEach { senses.remove(it) }
            return Speller(senses, known)
        }
    }
}
