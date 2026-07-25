package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.DatasetRow
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.Quantity

/** Where an answer's information came from, so it can be shown and linked. */
data class Citation(
    val label: String,
    val source: String,
    val deepLink: com.jlindemann.science.ai.data.DeepLinkTarget,
    val args: Map<String, String> = emptyMap()
)

/** One element paired with the value that was asked about. */
data class ValuedElement(val element: ElementRecord, val quantity: Quantity?, val display: String)

/**
 * What running a [QueryPlan] produced.
 *
 * [NoData] is as important as the success cases. When a field is resolved but the element has no
 * value for it, the engine reports that explicitly instead of printing the `"---"` sentinel or
 * falling through to an unrelated keyword branch, which is what the previous agent did.
 */
sealed class ExecutionResult {

    abstract val citations: List<Citation>

    /** A single field of a single element. */
    data class Property(
        val element: ElementRecord,
        val fieldId: String,
        val quantity: Quantity?,
        val display: String,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** Several elements compared across one or more fields. */
    data class Comparison(
        val elements: List<ElementRecord>,
        val fieldIds: List<String>,
        val values: Map<String, List<ValuedElement>>,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * A ranked or filtered set of elements.
     *
     * @property matched how many elements satisfied the filters
     * @property missing how many matched but had no value for the sorted field
     */
    data class ElementList(
        val results: List<ValuedElement>,
        val fieldId: String?,
        val matched: Int,
        val missing: Int,
        val descending: Boolean,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** A statistic computed over a set of elements. */
    data class Aggregate(
        val aggregation: Aggregation,
        val fieldId: String,
        val value: Double,
        val unit: String?,
        val contributors: List<ValuedElement>,
        val missing: Int,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * An element's isotopes.
     *
     * @property shown the isotopes rendered, longest-lived first
     * @property total how many the element has in total, so a truncated list can say so
     */
    data class Isotopes(
        val element: ElementRecord,
        val shown: List<com.jlindemann.science.ai.data.Isotope>,
        val total: Int,
        val stableCount: Int,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** An element's NFPA 704 hazard ratings. */
    data class Safety(
        val element: ElementRecord,
        val nfpa: com.jlindemann.science.ai.data.Nfpa,
        val radioactive: Boolean,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** A row from one of the app's tables. */
    data class Dataset(
        val row: DatasetRow,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * The question was understood but the app has no value for it.
     *
     * @property coverage how many of the 118 elements do have this field, so the answer can say
     *   how sparse it is rather than implying the data should have been there
     */
    data class NoData(
        val fieldId: String,
        val element: ElementRecord?,
        val coverage: Int,
        override val citations: List<Citation> = emptyList()
    ) : ExecutionResult()

    /** The filters were understood but nothing satisfied them. */
    data class Empty(
        val describedFilters: List<String>,
        override val citations: List<Citation> = emptyList()
    ) : ExecutionResult()
}
