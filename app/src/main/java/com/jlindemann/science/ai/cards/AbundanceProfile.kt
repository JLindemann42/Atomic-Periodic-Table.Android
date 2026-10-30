package com.jlindemann.science.ai.cards

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.FieldSpec
import com.jlindemann.science.ai.data.FieldValue
import com.jlindemann.science.ai.data.UnitConverter
import kotlin.math.floor
import kotlin.math.log10

/**
 * One reservoir's share of an element.
 *
 * @property logMgPerKg log₁₀ of the value, or null when the source recorded only a trace. Named
 *   for the unit most reservoirs use; see [AbundanceGroup] for why it is not the only one.
 * @property trace true when the source said the element is present but too small to quantify.
 *   Modelled explicitly rather than folded into null, because "present but unquantified" is real
 *   information and a reducer that only handled numbers would silently drop those bars.
 * @property unitKey the canonical unit token, used to group bars that are actually comparable
 * @property display the value as the source authored it. The unit is *not* repeated here: it heads
 *   the bar's [AbundanceGroup], and a 280 dp card has no width to say it twice.
 */
data class AbundanceBar(
    val fieldId: String,
    val label: String,
    val logMgPerKg: Double?,
    val trace: Boolean,
    val display: String,
    val unitKey: String? = null
)

/**
 * The bars that share a unit, and so can be compared with each other.
 *
 * The nine reservoirs are not measured in one unit: the crust, soils, rocks and meteorites are
 * mg/kg, sea water is µg/l, the sun and solar system are atom counts against silicon, and the
 * human-body figures are authored as either mg/kg or a percentage. Drawing all of them as one run
 * of bars — which this card used to do — claims a comparison the data does not support, so the
 * bars are grouped and each group states its unit.
 */
data class AbundanceGroup(
    val unitKey: String?,
    val unitLabel: String,
    val bars: List<AbundanceBar>
)

/**
 * An element's abundance across the nine reservoirs the app records.
 *
 * @property bars every bar, flattened, in [AbundanceReducer.ORDER]
 * @property groups the same bars partitioned by unit, which is what the card draws
 * @property floorLog where the bars start. A log axis has no zero, so bars must be drawn from a
 *   real, labelled floor — otherwise their lengths mean nothing at all.
 */
data class AbundanceProfile(
    val symbol: String,
    val bars: List<AbundanceBar>,
    val groups: List<AbundanceGroup>,
    val minLog: Double,
    val maxLog: Double,
    val floorLog: Double
)

object AbundanceReducer {

    /** Fewer bars than this and the chart adds nothing to the sentence. */
    const val MIN_BARS = 3

    /**
     * The nine reservoirs in a fixed order, roughly Earth outwards then biology.
     *
     * Fixed rather than sorted by value so the same reservoir sits in the same place for every
     * element, which is what lets two cards be compared by eye. Grouping by unit reorders within
     * this list but never sorts by magnitude, so that property survives.
     */
    val ORDER = listOf(
        "abundance_earth_crust",
        "abundance_crustal_rocks",
        "abundance_earth_soils",
        "abundance_urban_soils",
        "abundance_sea_water",
        "abundance_meteorites",
        "abundance_sun",
        "abundance_solar_system",
        "abundance_human_body"
    )

    fun of(element: ElementRecord, strings: StringProvider): AbundanceProfile? {
        val ordered = ORDER.mapNotNull { fieldId ->
            val spec = FieldRegistry.byId[fieldId] ?: return@mapNotNull null
            when (val value = element.value(fieldId)) {
                is FieldValue.Num -> {
                    // log10(0) is -Infinity and would blow the axis out to nothing.
                    val amount = value.quantity.mid
                    if (amount <= 0) null
                    else AbundanceBar(
                        fieldId = fieldId,
                        label = label(spec, strings),
                        logMgPerKg = log10(amount),
                        trace = false,
                        display = value.quantity.display,
                        unitKey = UnitConverter.canonical(value.quantity.unit)
                    )
                }
                is FieldValue.Trace ->
                    AbundanceBar(
                        fieldId = fieldId,
                        label = label(spec, strings),
                        logMgPerKg = null,
                        trace = true,
                        // Was `value.toString()` on an object with no override, which drew the
                        // class name and a hash the moment anything rendered a trace bar's text.
                        display = strings.get(R.string.ai_value_trace),
                        unitKey = UnitConverter.canonical(spec.canonicalUnit)
                    )
                else -> null
            }
        }
        if (ordered.size < MIN_BARS) return null

        val logs = ordered.mapNotNull { it.logMgPerKg }
        if (logs.isEmpty()) return null

        // Grouped by unit, groups in order of first appearance so mg/kg — the unit most
        // reservoirs share — leads, and the outliers follow in the usual reservoir order.
        // `groupBy` preserves encounter order for both the keys and the values within a key, so
        // reservoirs stay in ORDER inside each group.
        val groups = ordered.groupBy { it.unitKey }.map { (unitKey, bars) ->
            AbundanceGroup(unitKey, unitLabel(bars.first(), unitKey, strings), bars)
        }

        val minLog = logs.min()
        val maxLog = logs.max()
        return AbundanceProfile(
            symbol = element.symbol,
            // Flat and strictly in ORDER. The card draws `groups`; `bars` is the canonical
            // sequence for anything that wants the reservoirs without the unit partition.
            bars = ordered,
            groups = groups,
            minLog = minLog,
            maxLog = maxLog,
            floorLog = floor(minLog) - 0.5
        )
    }

    /**
     * A group's unit as a reader should see it.
     *
     * Prefers the field's declared label — "atoms per 10⁶ atoms of silicon" reads far better than
     * the `Si=10^6` token that groups it — but only when the group's unit is the one that field
     * declares. A human-body figure authored as "65% (by mass)" groups under `%`, and labelling
     * that group with the field's mg/kg would be worse than showing no unit at all.
     */
    private fun unitLabel(first: AbundanceBar, unitKey: String?, strings: StringProvider): String {
        val spec = FieldRegistry.byId[first.fieldId]
        if (spec != null && UnitConverter.canonical(spec.canonicalUnit) == unitKey) {
            val declared = spec.unitLabelRes
                ?.let { runCatching { strings.get(it) }.getOrNull() }?.trim()
            if (!declared.isNullOrEmpty() && !declared.startsWith("str:")) return declared
        }
        return unitKey.orEmpty()
    }

    private fun label(spec: FieldSpec, strings: StringProvider): String {
        val text = runCatching { strings.get(spec.sentenceLabel()) }.getOrNull()
        return if (text == null || text.startsWith("str:")) {
            spec.id.removePrefix("abundance_").replace('_', ' ')
        } else {
            text.replace(":", "").trim()
        }
    }
}
