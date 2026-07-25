package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.Block
import com.jlindemann.science.ai.data.ElementKey
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.Quantity
import com.jlindemann.science.ai.data.SeriesId

/** What the user is asking for. */
enum class Intent {
    /** One field of one element, optionally in a requested unit. */
    PROPERTY_LOOKUP,

    /**
     * A whole family of fields for one element — "the thermal properties of gold".
     * Only fields the element actually has a value for are returned.
     */
    CATEGORY_LOOKUP,

    /** Two or more elements compared on one or more fields. */
    COMPARISON,

    /** The extreme of a field, optionally within a subset: "densest nonmetal". */
    SUPERLATIVE,

    /** Every element matching a set of conditions: "transition metals melting above 2000 °C". */
    FILTER_LIST,

    /** A statistic over a subset: "average electronegativity of the halogens". */
    AGGREGATE,

    /** A row from one of the app's tables: a constant, equation, mineral, dictionary term. */
    DATASET_LOOKUP,

    /** Nothing recognised. The caller should fall back. */
    UNKNOWN
}

/** Comparison operators available in a filter. */
enum class Op { GT, GTE, LT, LTE, EQ, BETWEEN }

/** Statistics available over a filtered set. */
enum class Aggregation { NONE, MIN, MAX, AVG, SUM, COUNT, MEDIAN, RANGE }

/** Something the query referred to. */
sealed class EntityRef {
    data class Element(val key: ElementKey) : EntityRef()
    data class DatasetRow(val dataset: String, val id: String) : EntityRef()
}

/** A condition an element must satisfy. */
sealed class Filter {

    data class FieldCompare(val fieldId: String, val op: Op, val value: Quantity, val high: Double? = null) : Filter()
    data class InSeries(val series: Set<SeriesId>) : Filter()
    data class InBlock(val block: Block) : Filter()
    data class InPhase(val phase: String) : Filter()
    data class IsRadioactive(val radioactive: Boolean) : Filter()
    data class InPeriod(val period: Int) : Filter()
    data class InGroup(val group: Int) : Filter()
    data class IsMetal(val metal: Boolean) : Filter()

    /**
     * Whether an element satisfies this condition.
     *
     * An element with no value for the compared field never matches, rather than defaulting to
     * zero — otherwise "elements denser than 5" would silently include everything unmeasured.
     */
    fun matches(element: ElementRecord, store: KnowledgeStore): Boolean = when (this) {
        is FieldCompare -> {
            val actual = store.quantityIn(element, fieldId, value.unit)
            if (actual == null) false else when (op) {
                Op.GT -> actual.mid > value.value
                Op.GTE -> actual.mid >= value.value
                Op.LT -> actual.mid < value.value
                Op.LTE -> actual.mid <= value.value
                Op.EQ -> kotlin.math.abs(actual.mid - value.value) < 1e-6
                Op.BETWEEN -> high != null && actual.mid >= value.value && actual.mid <= high
            }
        }
        is InSeries -> element.series in series
        is InBlock -> element.block == block
        is InPhase -> element.phase == phase
        is IsRadioactive -> element.radioactive == radioactive
        is InPeriod -> element.period == period
        is InGroup -> element.groupNumber == group
        is IsMetal -> element.series.isMetal == metal
    }
}

/**
 * A natural-language question turned into something executable.
 *
 * This is what makes multi-hop answers possible: "which transition metals melt above 2000 °C"
 * becomes an [InSeries] filter plus a [Filter.FieldCompare], which the executor runs across all
 * 118 elements. The previous agent could only match a query against a fixed keyword branch, so
 * it had no way to express a question of that shape at all.
 *
 * @property confidence 0..1; below the planner's threshold the caller should not use this plan
 * @property evidence human-readable reasons the planner chose this, for debugging and tests
 */
data class QueryPlan(
    val intent: Intent,
    val entities: List<EntityRef> = emptyList(),
    val fieldIds: List<String> = emptyList(),
    val fieldOrdinal: Int? = null,
    val filters: List<Filter> = emptyList(),
    val aggregation: Aggregation = Aggregation.NONE,
    val sortField: String? = null,
    val sortDescending: Boolean = true,
    val limit: Int = 1,
    val targetUnit: String? = null,
    val confidence: Double = 0.0,
    val rawQuery: String = "",
    val evidence: List<String> = emptyList()
) {
    val primaryField: String? get() = fieldIds.firstOrNull()

    val elementKeys: List<ElementKey>
        get() = entities.filterIsInstance<EntityRef.Element>().map { it.key }

    /** Elements matching every filter, in atomic-number order. */
    fun candidates(store: KnowledgeStore): List<ElementRecord> =
        store.elements.filter { element -> filters.all { it.matches(element, store) } }
}
