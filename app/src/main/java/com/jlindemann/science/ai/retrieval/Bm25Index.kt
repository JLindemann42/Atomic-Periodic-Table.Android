package com.jlindemann.science.ai.retrieval

import kotlin.math.ln

/** A document in the retrieval corpus. [id] is prefixed by kind, e.g. `element:gold`. */
data class Document(val id: String, val title: String, val body: String)

/**
 * A scored search result.
 *
 * Whether [score] is a raw BM25 sum or a value normalised against the top hit depends on which
 * search method produced it — see [Bm25Index.searchNormalized], whose scores are ordinal only.
 */
data class SearchHit(val id: String, val title: String, val score: Double)

/**
 * Okapi BM25 over the app's own data.
 *
 * This replaces the previous embedding search, which could never work: its query embedder
 * returned a hash-seeded pseudo-random vector, so cosine scores hovered near zero and the 0.65
 * threshold gating it never fired.
 *
 * `b` is set below the usual 0.75 because the corpus is deliberately heterogeneous — a
 * three-token Poisson row sits beside a two-hundred-token element description — and a lower `b`
 * reduces the length normalisation that would otherwise over-favour the short rows.
 */
class Bm25Index(
    documents: List<Document>,
    private val language: String = "en",
    private val k1: Double = 1.2,
    private val b: Double = 0.6
) {

    private val ids: Array<String> = Array(documents.size) { documents[it].id }
    private val titles: Array<String> = Array(documents.size) { documents[it].title }
    private val lengths = IntArray(documents.size)

    /** term -> (document index -> term frequency). */
    private val postings = HashMap<String, MutableMap<Int, Int>>()
    private var averageLength = 0.0

    val size: Int get() = ids.size

    init {
        var total = 0L
        documents.forEachIndexed { docIndex, doc ->
            val tokens = Tokenizer.tokenize("${doc.title} ${doc.body}", language)
            lengths[docIndex] = tokens.size
            total += tokens.size
            for (token in tokens) {
                val bucket = postings.getOrPut(token) { HashMap() }
                bucket[docIndex] = (bucket[docIndex] ?: 0) + 1
            }
        }
        averageLength = if (documents.isEmpty()) 0.0 else total.toDouble() / documents.size
    }

    /**
     * Search the index.
     *
     * @param query raw query text; tokenized exactly as documents were
     * @param limit maximum hits to return
     * @param prefixFilter when set, restricts results to ids starting with it — a large precision
     *   win when the planner already knows it wants an element rather than a dictionary entry
     */
    fun search(query: String, limit: Int = 5, prefixFilter: String? = null): List<SearchHit> {
        val tokens = Tokenizer.tokenize(query, language)
        if (tokens.isEmpty() || ids.isEmpty()) return emptyList()

        val scores = HashMap<Int, Double>()
        for (token in tokens.distinct()) {
            val bucket = postings[token] ?: continue
            val idf = inverseDocumentFrequency(bucket.size)
            if (idf <= 0.0) continue
            for ((docIndex, frequency) in bucket) {
                if (prefixFilter != null && !ids[docIndex].startsWith(prefixFilter)) continue
                val tf = frequency.toDouble()
                val norm = 1.0 - b + b * (lengths[docIndex] / averageLength.coerceAtLeast(1.0))
                scores.merge(docIndex, idf * tf * (k1 + 1.0) / (tf + k1 * norm), Double::plus)
            }
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { SearchHit(ids[it.key], titles[it.key], it.value) }
    }

    /**
     * Search and normalise scores into 0..1 relative to the top hit.
     *
     * **These scores are ordinal, not absolute.** Dividing by the top hit means the best result
     * always scores exactly 1.0 — whether it was an excellent match or the least bad of a corpus
     * that contains nothing relevant. A threshold applied to this number therefore answers nothing
     * about match quality, which is why raising the planner's dataset threshold from 0.45 to 0.55
     * had no measurable effect: every candidate arrived at exactly the same value.
     *
     * Use [search] if you need magnitudes that can be compared across queries.
     */
    fun searchNormalized(query: String, limit: Int = 5, prefixFilter: String? = null): List<SearchHit> {
        val hits = search(query, limit, prefixFilter)
        val top = hits.firstOrNull()?.score ?: return hits
        if (top <= 0.0) return hits
        return hits.map { it.copy(score = it.score / top) }
    }

    /**
     * Probabilistic IDF with the +1 smoothing that keeps it non-negative.
     * A term in every document scores ~0, which is what makes a stopword list unnecessary.
     */
    private fun inverseDocumentFrequency(documentFrequency: Int): Double =
        ln(1.0 + (size - documentFrequency + 0.5) / (documentFrequency + 0.5))

    /**
     * Whether the query contains a content word the corpus has never seen.
     *
     * Tokenised here rather than by the caller, because the answer is only meaningful against the
     * same vocabulary the postings were built from: [Tokenizer] stems, and a query split any other
     * way produces terms that are "absent" for reasons that have nothing to do with the subject.
     *
     * @param minimumLength below this a token is a function word, which is absent or present for
     *   reasons unrelated to the topic.
     */
    fun mentionsUnknownTerm(query: String, minimumLength: Int = 4): Boolean =
        Tokenizer.tokenize(query, language).any { term ->
            term.length >= minimumLength &&
                    term.none { it.isDigit() } &&
                    documentFrequency(term) == 0
        }

    /**
     * Whether a query and a document title have any content word in common.
     *
     * The companion to [mentionsUnknownTerm], and the half that makes it usable. An unknown word on
     * its own does not mean the query is off-topic — real questions are full of ordinary verbs
     * ("define", "convert", "tell") that never appear in a corpus of element data. What separates
     * "define molar mass" from "write me a poem" is that the first shares words with the row it
     * retrieved and the second shares nothing with the electron mass.
     */
    fun sharesTermWithTitle(query: String, title: String): Boolean {
        val titleTerms = Tokenizer.tokenize(title, language).toHashSet()
        return Tokenizer.tokenize(query, language).any {
            it.length >= MIN_SHARED_TERM && it in titleTerms
        }
    }

    private companion object {
        /** Below this a shared token is a coincidence of grammar rather than of subject. */
        const val MIN_SHARED_TERM = 3
    }

    /** Document frequency of a term, exposed for diagnostics and tests. */
    fun documentFrequency(term: String): Int = postings[term]?.size ?: 0

}
