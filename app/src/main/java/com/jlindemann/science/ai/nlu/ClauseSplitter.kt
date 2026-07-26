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

    /** Conjunctions that can join two independent questions. */
    private val CONJUNCTIONS = listOf(
        " and ", " and, ", " also ", " plus ",
        " och ", " samt ", " und ", " sowie ", " y ", " e ", " et ", " en ",
        " और ", " اور ", " 和 ", " 并且 "
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
        "कैसे", "क्या", "कौन", "کیسے", "کیا", "如何", "什么", "哪个"
    )

    /** Constructs where a conjunction is part of the phrase and must not be split. */
    private val PROTECTED = listOf("between", "from", "compare", "difference between", "vs", "versus")

    /** Shortest a clause can be and still carry a question. */
    private const val MIN_CLAUSE = 8

    /**
     * Split into two clauses, or return null when the query is a single question.
     *
     * @return the clauses in order, or null when the query should be planned whole
     */
    fun split(query: String): List<String>? {
        val normalized = TextMatching.normalizeForLookup(query)

        for (conjunction in CONJUNCTIONS) {
            val at = normalized.indexOf(conjunction)
            if (at < MIN_CLAUSE) continue

            // Work on the original text so the clauses keep their capitalisation, which the
            // formula parser and element resolver both rely on.
            val head = query.substring(0, at).trim()
            val tail = query.substring(at + conjunction.length).trim()
            if (head.length < MIN_CLAUSE || tail.length < MIN_CLAUSE) continue

            // A protecting construct only matters when it reaches across the conjunction, which
            // means it opened in the head: "compare gold and silver" and "between 1000 and 2000"
            // must stay whole. The same word in the tail is a new clause of its own — "…and how
            // does it compare to lead" is a second question, not a protected phrase.
            val headNormalized = TextMatching.normalizeForLookup(head)
            if (PROTECTED.any { TextMatching.containsWord(headNormalized, it) }) continue

            val tailNormalized = TextMatching.normalizeForLookup(tail)
            val opensAQuestion = CLAUSE_OPENERS.any {
                tailNormalized == it || tailNormalized.startsWith("$it ")
            }
            if (!opensAQuestion) continue

            return listOf(head, tail)
        }
        return null
    }
}
