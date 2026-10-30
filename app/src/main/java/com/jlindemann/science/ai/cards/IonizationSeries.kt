package com.jlindemann.science.ai.cards

import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldRegistry

/** One ionization step: which electron is being removed, and what it costs. */
data class IonizationStep(val order: Int, val electronVolts: Double, val display: String)

/**
 * An element's successive ionization energies.
 *
 * @property shellClosureAfter orders after which the next step jumps by at least [JUMP_FACTOR]×.
 *   These are where a closed shell is being broken into, and they are the whole teaching point of
 *   the series — sodium's second ionization costs nearly ten times its first because the first
 *   emptied its outer shell.
 * @property suggestedScale chosen from the data, not from a hardcoded preference
 */
data class IonizationSeries(
    val symbol: String,
    val steps: List<IonizationStep>,
    val shellClosureAfter: Set<Int>,
    val suggestedScale: ChartScale
)

object IonizationSeriesReducer {

    /** How much larger the next step must be to count as breaking into a closed shell. */
    const val JUMP_FACTOR = 2.0

    /** Fewer steps than this and a chart says nothing a sentence does not. */
    const val MIN_STEPS = 3

    /**
     * Read an element's ionization bank into a plottable series.
     *
     * `ionization_energy` is a banked field: thirty JSON keys addressed through one spec. Iron has
     * 26 recorded steps, hydrogen has one.
     */
    fun of(element: ElementRecord): IonizationSeries? {
        val spec = FieldRegistry.byId["ionization_energy"] ?: return null
        val range = spec.ordinalRange ?: return null

        val steps = ArrayList<IonizationStep>(range.count())
        for (order in range) {
            val value = element.values["ionization_energy#$order"]
                ?: element.values["ionization_energy"].takeIf { order == range.first }
            val quantity = value?.asQuantity() ?: continue
            if (quantity.mid <= 0) continue
            steps.add(IonizationStep(order, quantity.mid, quantity.display))
        }
        if (steps.size < MIN_STEPS) return null

        // A jump is recorded against the step *before* it, because that is the shell that just
        // emptied — "after removing 1 electron, the next one costs 10× more".
        val closures = LinkedHashSet<Int>()
        for (index in 0 until steps.size - 1) {
            if (steps[index + 1].electronVolts >= steps[index].electronVolts * JUMP_FACTOR) {
                closures.add(steps[index].order)
            }
        }

        return IonizationSeries(
            symbol = element.symbol,
            steps = steps,
            shellClosureAfter = closures,
            // Log compresses exactly the jumps the chart exists to show, so it is only used when a
            // linear axis would flatten the early steps onto the baseline.
            suggestedScale = DecadeLabels.suggestedScale(steps.map { it.electronVolts })
        )
    }
}
