package com.jlindemann.science.ai.retrieval

import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.ElementKey
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedView

/** What a retrieval hit refers to. */
sealed class RetrievedRef {
    data class Element(val key: ElementKey) : RetrievedRef()
    data class Dataset(val dataset: String, val id: String) : RetrievedRef()
}

/** A fused retrieval result. [score] combines structured, lexical and fuzzy evidence. */
data class RetrievedHit(
    val ref: RetrievedRef,
    val title: String,
    val score: Double,
    val structured: Double = 0.0,
    val lexical: Double = 0.0
)

/** Fusion weights, isolated so they can be tuned from an eval set without touching logic. */
object RetrievalWeights {
    const val STRUCTURED = 1.0
    const val LEXICAL = 0.55
    const val FUZZY = 0.25

    /** Below this, a lexical-only hit is not trustworthy enough to answer from. */
    const val MIN_ANSWERABLE = 0.30
}

/**
 * Combines exact entity resolution with BM25 over the app's own content.
 *
 * The corpus is built at runtime from live data rather than shipped as a file. That removes the
 * 42 MB of precomputed embeddings the previous design carried, guarantees the text can never
 * drift from what the app displays, and keeps every language correctly separated — the shipped
 * corpus had all twelve languages concatenated into the file served as English.
 */
class HybridRetriever(
    private val store: KnowledgeStore,
    private val datasets: DatasetIndex,
    private val index: Bm25Index,
    private val entities: EntityResolver,
    private val localized: LocalizedView?
) {

    /** Search across elements and datasets, best first. */
    fun search(query: String, limit: Int = 5): List<RetrievedHit> {
        val fused = LinkedHashMap<String, RetrievedHit>()

        for (match in entities.resolveAll(query, limit)) {
            val title = localized?.name(match.key) ?: match.key
            fused["element:${match.key}"] = RetrievedHit(
                RetrievedRef.Element(match.key), title,
                RetrievalWeights.STRUCTURED * match.score, structured = match.score
            )
        }

        for (hit in index.searchNormalized(query, limit * 2)) {
            val ref = refOf(hit.id) ?: continue
            val existing = fused[hit.id]
            val lexical = RetrievalWeights.LEXICAL * hit.score
            fused[hit.id] = if (existing != null) {
                existing.copy(score = existing.score + lexical, lexical = hit.score)
            } else {
                RetrievedHit(ref, hit.title, lexical, lexical = hit.score)
            }
        }

        return fused.values.sortedByDescending { it.score }.take(limit)
    }

    /**
     * The single best hit, or null when nothing clears [RetrievalWeights.MIN_ANSWERABLE].
     * Returning null is deliberate: a weak lexical match is worse than admitting no answer.
     */
    fun best(query: String): RetrievedHit? =
        search(query, 1).firstOrNull()?.takeIf { it.score >= RetrievalWeights.MIN_ANSWERABLE }

    private fun refOf(documentId: String): RetrievedRef? {
        val separator = documentId.indexOf(':')
        if (separator < 0) return null
        val kind = documentId.substring(0, separator)
        val id = documentId.substring(separator + 1)
        return if (kind == "element") RetrievedRef.Element(id) else RetrievedRef.Dataset(kind, id)
    }

    companion object {

        /**
         * Build the searchable corpus from live app data.
         *
         * Roughly 780 documents: 118 elements plus every dataset row. This is the same coverage
         * the shipped `passages.jsonl` had, generated at runtime instead — every per-language
         * passages file was byte-identical to the `description` field already in the element
         * JSON, so shipping it was pure duplication.
         */
        fun buildCorpus(
            store: KnowledgeStore,
            datasets: DatasetIndex,
            localized: LocalizedView?
        ): List<Document> {
            val documents = ArrayList<Document>(store.size + datasets.rows.size)

            for (element in store.elements) {
                val view = localized?.elements?.get(element.key)
                val name = view?.name ?: element.key
                documents.add(
                    Document(
                        id = "element:${element.key}",
                        title = name,
                        body = buildString {
                            append(name).append(' ').append(element.key).append(' ')
                            append(element.symbol).append(' ').append(element.atomicNumber).append(' ')
                            view?.let {
                                append(it.description).append(' ')
                                append(it.seriesName).append(' ')
                                append(it.appearance).append(' ')
                                append(it.phase).append(' ')
                                append(it.electricalType).append(' ')
                                append(it.magneticType).append(' ')
                            }
                            append(element.series.name.replace('_', ' ')).append(' ')
                            append(element.block.name).append(" block ")
                            (element.value("discovered_by") as? com.jlindemann.science.ai.data.FieldValue.Text)
                                ?.let { append(it.raw).append(' ') }
                            (element.value("crystal_structure") as? com.jlindemann.science.ai.data.FieldValue.Enum)
                                ?.let { append(it.localized) }
                        }
                    )
                )
            }

            for (row in datasets.rows) {
                documents.add(
                    Document(
                        id = "${row.dataset}:${row.id}",
                        title = row.title,
                        body = row.body
                    )
                )
            }

            return documents
        }
    }
}
