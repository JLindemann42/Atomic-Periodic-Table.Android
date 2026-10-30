package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.retrieval.TextMatching

/**
 * "Which element is similar to gold" — the elements that share its group.
 *
 * In `must_defer.tsv`, so the engine declines it by design: similarity is a judgement about where an
 * element sits, not a field. Matched on group *number* rather than series: for gold that gives
 * copper, silver and roentgenium — its column — where the series would give every transition metal.
 */
object ElementSimilarity {

    private val SIMILARITY_WORDS = listOf(
        "similar", "like", "related", "equivalent", "analogy",
        "liknar", "liknande", "ahnlich", "verwandt",
        "similaire", "proche", "pareil", "parecido",
        "somigliante", "simile", "simili", "semelhante", "igual",
        "समान", "जैसा", "मिलता-जुलता", "ek jaisa", "muqabla",
        "相似", "类似", "xiangsi", "leishi", "soortgelyk", "katulad", "pareho"
    )

    /** Whether this asks what an element resembles. */
    fun asks(normalizedQuery: String?): Boolean =
        !normalizedQuery.isNullOrBlank() && SIMILARITY_WORDS.any { TextMatching.containsWord(normalizedQuery, it) }

    /** The answer for [target], or the "which element?" message when there is none in play. */
    fun answer(
        target: ElementRecord?,
        all: List<ElementRecord>,
        strings: StringProvider,
        nameOf: (ElementRecord) -> String
    ): String {
        if (target == null) return strings.get(R.string.ai_similarity_no_element)
        val name = nameOf(target)
        val series = strings.get(target.series.labelRes)

        // No group number at all: the lanthanides and actinides, outside the numbered columns.
        val group = target.groupNumber ?: return strings.get(R.string.ai_similarity_generic, name, series)
        val shared = all
            .filter { it.groupNumber == group && it.key != target.key }
            .sortedBy { it.atomicNumber }
            .map(nameOf)
        if (shared.isEmpty()) return strings.get(R.string.ai_similarity_generic, name, series)

        return strings.get(R.string.ai_similarity_result_header, name, series) +
                shared.joinToString(", ") +
                strings.get(R.string.ai_similarity_result_footer)
    }
}
