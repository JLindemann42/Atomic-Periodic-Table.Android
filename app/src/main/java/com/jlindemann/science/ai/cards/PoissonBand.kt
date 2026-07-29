package com.jlindemann.science.ai.cards

import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.model.Poisson
import com.jlindemann.science.model.PoissonModel

/** The range one class of material occupies on the Poisson axis. */
data class PoissonSpan(val label: String, val low: Double, val high: Double, val kind: String)

/**
 * An element's Poisson ratio placed against the materials the app already tabulates.
 *
 * A bare "0.29" tells almost nobody anything. Against rock at 0.1–0.4 and the incompressible limit
 * at 0.5 it becomes a statement about how the material behaves, which is why this card is a band
 * rather than a number.
 *
 * @property rank where the element sits among those with a recorded ratio
 */
data class PoissonBand(
    val symbol: String,
    val elementLow: Double,
    val elementHigh: Double,
    val elementDisplay: String,
    val references: List<PoissonSpan>,
    val axisMin: Double,
    val axisMax: Double,
    val rank: Int?,
    val rankedOutOf: Int
)

object PoissonBandReducer {

    /** The thermodynamic upper bound for an isotropic material. Worth drawing and annotating. */
    const val INCOMPRESSIBLE_LIMIT = 0.5

    /**
     * Build the band.
     *
     * `PoissonModel.getList` takes no Context, so the whole 52-row rock/soil/mineral reference table
     * is plain Kotlin and this reducer is unit-testable with no Android at all.
     */
    fun of(element: ElementRecord, store: KnowledgeStore): PoissonBand? {
        val quantity = element.quantity("poisson_ratio") ?: return null

        val rows = ArrayList<Poisson>().also { PoissonModel.getList(it) }
        val references = rows.groupBy { it.type }
            .map { (kind, entries) ->
                PoissonSpan(
                    label = kind.replaceFirstChar { it.uppercase() },
                    low = entries.minOf { it.start },
                    high = entries.maxOf { it.end },
                    kind = kind
                )
            }
            .sortedBy { it.low }

        val ranked = store.elements.mapNotNull { it.quantity("poisson_ratio")?.mid }.sortedDescending()
        val rank = ranked.indexOfFirst { it <= quantity.mid }.takeIf { it >= 0 }?.plus(1)

        val low = quantity.value
        val high = quantity.high ?: quantity.value
        // The axis has to hold every reference span as well as the element, and always the
        // incompressible limit — the annotation at 0.5 is the pedagogical payoff.
        val axisMin = minOf(references.minOfOrNull { it.low } ?: 0.0, low) - 0.05
        val axisMax = maxOf(references.maxOfOrNull { it.high } ?: 0.0, high, INCOMPRESSIBLE_LIMIT) + 0.05

        return PoissonBand(
            symbol = element.symbol,
            elementLow = low,
            elementHigh = high,
            elementDisplay = quantity.display,
            references = references,
            axisMin = axisMin,
            axisMax = axisMax,
            rank = rank,
            rankedOutOf = ranked.size
        )
    }
}
