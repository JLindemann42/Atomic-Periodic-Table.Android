package com.jlindemann.science.ai.cards

import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore

/** One element's value on a trend line, in atomic-number order. */
data class TrendPoint(val atomicNumber: Int, val symbol: String, val value: Double)

/** A field across a set of elements, for the line card (roadmap 2.3). */
data class TrendSeries(val fieldId: String, val points: List<TrendPoint>, val suggestedScale: ChartScale)

/**
 * Builds a [TrendSeries] from the card's own arguments - the field and the elements, in order - read
 * back out of the store, so the card can only draw what the answer above it states.
 */
object TrendSeriesReducer {

    /** Fewer points than this and the line says nothing the sentence does not. */
    const val MIN_POINTS = 3

    const val FIELD_ARG = "field"
    const val KEYS_ARG = "keys"

    fun of(fieldId: String, keys: List<String>, store: KnowledgeStore): TrendSeries? {
        if (FieldRegistry.byId[fieldId] == null) return null
        val points = keys.mapNotNull { key ->
            val element = store.element(key) ?: return@mapNotNull null
            val value = store.quantityIn(element, fieldId, null)?.mid ?: return@mapNotNull null
            TrendPoint(element.atomicNumber, element.symbol, value)
        }.sortedBy { it.atomicNumber }
        if (points.size < MIN_POINTS) return null
        return TrendSeries(fieldId, points, DecadeLabels.suggestedScale(points.map { it.value }))
    }

    fun of(card: ChatCard, store: KnowledgeStore): TrendSeries? {
        val fieldId = card.args[FIELD_ARG] ?: return null
        val keys = card.args[KEYS_ARG]?.split(',')?.filter { it.isNotBlank() } ?: return null
        return of(fieldId, keys, store)
    }
}

/**
 * One cell of the mini periodic table: where it sits, and its value as a share of the field's span,
 * or null for an element with no value, which is drawn empty rather than as the lowest colour.
 *
 * @property row 1 to 7 for the periods, 8 and 9 for the lanthanides and actinides set below
 * @property column 1 to 18 by group; the f-block rows use 3 to 17
 */
data class HeatCell(val symbol: String, val row: Int, val column: Int, val value: Double?, val fraction: Double?)

/** A field over the whole table, for the heat-map card (roadmap 2.3). */
data class HeatMap(
    val fieldId: String,
    val cells: List<HeatCell>,
    val min: Double,
    val max: Double,
    val scale: ChartScale
)

object HeatMapReducer {

    /** Fewer values than this and a map of the table is mostly empty squares. */
    const val MIN_VALUES = 10

    const val FIELD_ARG = "field"

    fun of(fieldId: String, store: KnowledgeStore): HeatMap? {
        if (FieldRegistry.byId[fieldId] == null) return null
        val values = store.elements.associate { it.key to store.quantityIn(it, fieldId, null)?.mid }
        val present = values.values.filterNotNull()
        if (present.size < MIN_VALUES) return null
        // A field spanning decades - abundance, half-life - would show one bright cell and a hundred
        // dark ones on a linear scale; the same rule the line chart uses decides.
        val scale = DecadeLabels.suggestedScale(present)
        fun scaled(v: Double) = if (scale == ChartScale.LOG10) kotlin.math.log10(kotlin.math.max(v, MIN_POSITIVE)) else v
        val usable = if (scale == ChartScale.LOG10) present.filter { it > 0 } else present
        val low = scaled(usable.min())
        val high = scaled(usable.max())
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        val cells = store.elements.mapNotNull { element ->
            val (row, column) = position(element.atomicNumber, element.period, element.groupNumber) ?: return@mapNotNull null
            val value = values[element.key]
            val fraction = value?.takeIf { scale == ChartScale.LINEAR || it > 0 }
                ?.let { ((scaled(it) - low) / span).coerceIn(0.0, 1.0) }
            HeatCell(element.symbol, row, column, value, fraction)
        }
        return HeatMap(fieldId, cells, usable.min(), usable.max(), scale)
    }

    fun of(card: ChatCard, store: KnowledgeStore): HeatMap? = card.args[FIELD_ARG]?.let { of(it, store) }

    /**
     * Where an element sits in the drawn table. The lanthanides and actinides go on two rows of their
     * own below it, as every printed table puts them, so the main body stays eighteen columns wide.
     */
    fun position(atomicNumber: Int, period: Int, group: Int?): Pair<Int, Int>? = when (atomicNumber) {
        in LANTHANIDES -> 8 to 3 + (atomicNumber - LANTHANIDES.first)
        in ACTINIDES -> 9 to 3 + (atomicNumber - ACTINIDES.first)
        else -> group?.let { period to it }
    }

    private val LANTHANIDES = 57..71
    private val ACTINIDES = 89..103
    private const val MIN_POSITIVE = 1e-30
}
