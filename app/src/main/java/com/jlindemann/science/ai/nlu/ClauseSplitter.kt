package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.retrieval.TextMatching

/**
 * Splits a question that asks two things at once.
 *
 * "What is the density of gold and how does it compare to lead" is two questions sharing a
 * subject. Answering only the second, which is what happened before, silently drops half of
 * what was asked.
 *
 * The difficulty is not finding "and" — it is knowing when *not* to split on it. The word joins
 * two clauses in the example above, but joins two elements in "compare gold and silver", two
 * bounds in "between 1000 and 2000 kelvin", and two aspects in "reactivity, density and
 * applications". Splitting any of those would break a question that currently works, so the
 * rule is deliberately narrow: the tail must read as a question in its own right.
 */
object ClauseSplitter {

    /**
     * Conjunctions that can join two independent questions.
     *
     * Sentence boundaries are included because two questions are often just two sentences —
     * "Tell me about hydrogen. Furthermore, what is the density of iron" was answered as a
     * two-element density comparison because nothing here matched it at all.
     */
    private val CONJUNCTIONS = listOf(
        ". ", "? ", "! ",
        " and ", " and, ", " also ", " plus ",
        " och ", " samt ", " und ", " sowie ", " y ", " e ", " et ", " en ",
        " और ", " اور ", " 和 ", " 并且 ",
        // Filipino joins two questions with "at". Chinese joins them with 以及, which — unlike the
        // bare 和 — never joins two nouns, so it is safe without spaces in a script that has none.
        " at ", "以及", "还有"
    )

    /**
     * Openers that make a clause a question rather than a continuation.
     * "…and how does it compare" splits; "…and silver" does not.
     */
    private val CLAUSE_OPENERS = listOf(
        "how", "what", "which", "why", "when", "where", "who", "is", "are", "does", "do",
        "can", "tell me", "show me", "list", "give me",
        "hur", "vad", "vilka", "vilket", "varfor", "ar", "visa", "berätta",
        "wie", "was", "welche", "warum", "ist", "sind", "zeige",
        "como", "que", "cual", "por que", "es", "muestra",
        "comment", "quel", "quelle", "pourquoi", "est", "montre",
        "come", "cosa", "quale", "perche", "quanto",
        "hoe", "wat", "watter", "paano", "ano", "alin",
        "कैसे", "क्या", "कौन", "کیسے", "کیا", "如何", "什么", "哪个",
        // The elided Italian and Portuguese interrogative. Only "quale" was listed, and every
        // second clause is written "qual è …" / "qual é …".
        "qual",
        // Hindi writes its possessive as one word, so a second clause opens with the pronoun and
        // puts the interrogative at the end: "…और इसका गलनांक क्या है". Chinese does the same with
        // 它的. Neither could be reached by a leading question word or by a property linker.
        "इसका", "इसकी", "इनका", "इनकी", "उसका", "उसकी",
        "它的", "它们的"
    )

    /** Constructs where a conjunction is part of the phrase and must not be split. */
    private val PROTECTED = listOf("between", "from", "compare", "difference between", "vs", "versus")

    /** Shortest a clause can be and still carry a question. */
    private const val MIN_CLAUSE = 8

    /**
     * The same, for Chinese.
     *
     * Measured in characters, and a Han character carries roughly what a whole word does — 它的熔点
     * 是多少 is a complete question in seven characters. The Latin floor rejected every Chinese
     * second clause before it was ever examined.
     */
    private const val MIN_CLAUSE_HAN = 4

    private fun minClause(text: String): Int = if (isHan(text)) MIN_CLAUSE_HAN else MIN_CLAUSE

    private fun isHan(text: String): Boolean =
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    /**
     * Words a second clause can open with that carry no meaning of their own.
     *
     * Stripped before judging the tail, so the marker neither hides the question behind it nor gets
     * mistaken for part of it. Discourse markers first, then the bare conjunctions that survive a
     * sentence-boundary split ("…of gold? and what about silver").
     */
    private val LEADING_CONNECTIVES: List<String> =
        Lexicon.DISCOURSE_MARKERS + listOf("and", "also", "plus", "och", "und", "y", "et", "e")

    /**
     * Linkers that tie a property to an element, in the languages the app ships.
     *
     * Their presence is what makes a tail with no question word still a question: "densitet av väte"
     * and "the density of iron" are complete asks. Matching a linker is a far cheaper test than
     * running the field and element resolvers, and it keeps this object pure — it is called from
     * `AiEngine` and from tests as `ClauseSplitter.split(query)`, with no resolvers to hand.
     */
    private val PROPERTY_LINKERS = listOf(
        "of", "for", "in",
        "av", "för", "for", "hos",
        "von", "des", "der", "vom",
        "de", "del", "de la", "du", "des",
        "di", "dell", "do", "da",
        "ng", "ka", "ki", "का", "की", "کا", "کی", "的"
    )

    /**
     * Split into two clauses, or return null when the query is a single question.
     *
     * @return the clauses in order, or null when the query should be planned whole
     */
    fun split(query: String): List<String>? {
        val normalized = TextMatching.normalizeForLookup(query)

        val floor = minClause(query)

        for ((conjunction, at) in occurrences(normalized, floor)) {
            // Work on the original text so the clauses keep their capitalisation, which the
            // formula parser and element resolver both rely on.
            val head = query.substring(0, at).trim()
            val tail = query.substring(at + conjunction.length).trim()
            if (head.length < floor || tail.length < floor) continue

            // A protecting construct only matters when it reaches across the conjunction, which
            // means it opened in the head: "compare gold and silver" and "between 1000 and 2000"
            // must stay whole. The same word in the tail is a new clause of its own — "…and how
            // does it compare to lead" is a second question, not a protected phrase.
            val headNormalized = TextMatching.normalizeForLookup(head)
            if (PROTECTED.any { TextMatching.containsWord(headNormalized, it) }) continue

            var tailNormalized = TextMatching.normalizeForLookup(tail)

            // A discourse marker introduces a second question without being one: "…och sen densitet
            // av väte", "…Furthermore, what is the density of iron". Strip it and judge what follows,
            // so the marker neither hides the question nor becomes part of it.
            // Dropped by token rather than by character offset: normalisation can change a string's
            // length ("außerdem" folds to "ausserdem"), so slicing the original text at an offset
            // measured on the normalised text cuts in the wrong place.
            var tailText = tail
            var strippedMarker = false
            val firstWordEnd = tailText.indexOfFirst { it == ' ' || it == ',' }.let {
                if (it < 0) tailText.length else it
            }
            val firstWord = TextMatching.normalizeForLookup(tailText.take(firstWordEnd)).trim(',')
            if (firstWord in LEADING_CONNECTIVES) {
                tailText = tailText.substring(firstWordEnd).trimStart(' ', ',')
                tailNormalized = TextMatching.normalizeForLookup(tailText)
                strippedMarker = true
            }
            if (tailText.isBlank()) continue

            // Chinese writes no space after the opener, so requiring one meant 它的熔点是多少 —
            // a complete second question — never counted as opening one.
            val opensAQuestion = CLAUSE_OPENERS.any {
                tailNormalized == it ||
                        tailNormalized.startsWith("$it ") ||
                        (isHan(it) && tailNormalized.startsWith(it))
            }
            // A tail that names both a property and an element is a question whatever it opens with:
            // "densitet av väte" and "the density of iron" are complete asks with no question word.
            // Checked only after the protected-construct guard above, so "compare gold and silver"
            // is never reached here.
            // A stripped marker is itself strong evidence of a second question, so the tail after one
            // is held to a lower bar: "…och dessutom dess densitet" leaves only "dess densitet",
            // which is two words and carries no linker, but is unambiguously a further ask.
            if (!opensAQuestion && !readsAsAsk(tailNormalized, lenient = strippedMarker)) continue

            return listOf(head, tailText)
        }
        return null
    }

    /**
     * Every place a conjunction could split the query, earliest first.
     *
     * *Every* place, not the first per conjunction. Italian and Portuguese normalise their copula
     * "è"/"é" to the same letter as their conjunction "e", so "qual è il numero atomico del ferro
     * **e** qual è il suo punto di fusione" contains " e " twice — and the first one is four
     * characters in, below the minimum clause length. Taking only the first occurrence discarded
     * the conjunction entirely and the compound question was answered as one.
     */
    private fun occurrences(normalized: String, floor: Int): List<Pair<String, Int>> {
        val found = ArrayList<Pair<String, Int>>()
        for (conjunction in CONJUNCTIONS) {
            var from = 0
            while (true) {
                val at = normalized.indexOf(conjunction, from)
                if (at < 0) break
                if (at >= floor) found.add(conjunction to at)
                from = at + 1
            }
        }
        return found.sortedBy { it.second }
    }

    /**
     * Whether a clause with no question word still reads as an ask.
     *
     * Requires a property linker and something on both sides of it, which is the shape of
     * "densitet av väte" and "the density of iron". A bare continuation — "…and silver" — has no
     * linker and stays joined, which is what the protected-construct rules depend on.
     */
    private fun readsAsAsk(tailNormalized: String, lenient: Boolean = false): Boolean {
        val tokens = TextMatching.splitQueryTokens(tailNormalized)
        if (lenient) return tokens.size >= 2
        if (tokens.size < 3) return false
        val linkerAt = tokens.indexOfFirst { it in PROPERTY_LINKERS }
        return linkerAt > 0 && linkerAt < tokens.size - 1
    }
}
