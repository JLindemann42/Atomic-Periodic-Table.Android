package com.jlindemann.science.ai.retrieval

import kotlin.math.ln

/** A document in the retrieval corpus. [id] is prefixed by kind, e.g. `element:gold`. */
data class Document(val id: String, val title: String, val body: String)

/** A scored search result. [score] is the raw BM25 score; compare only within one result set. */
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
     * Search and normalise scores into 0..1 relative to the top hit, so callers can apply a
     * threshold without depending on absolute BM25 magnitudes.
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

    /** Document frequency of a term, exposed for diagnostics and tests. */
    fun documentFrequency(term: String): Int = postings[term]?.size ?: 0
}
