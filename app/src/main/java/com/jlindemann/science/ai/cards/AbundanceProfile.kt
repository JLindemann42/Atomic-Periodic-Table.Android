package com.jlindemann.science.ai.cards

import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.FieldValue
import kotlin.math.floor
import kotlin.math.log10

/**
 * One reservoir's share of an element.
 *
 * @property logMgPerKg log₁₀ of the concentration, or null when the source recorded only a trace
 * @property trace true when the source said the element is present but too small to quantify.
 *   Modelled explicitly rather than folded into null, because "present but unquantified" is real
 *   information and a reducer that only handled numbers would silently drop those bars.
 */
data class AbundanceBar(
    val fieldId: String,
    val label: String,
    val logMgPerKg: Double?,
    val trace: Boolean,
    val display: String
)

/**
 * An element's abundance across the nine reservoirs the app records.
 *
 * @property floorLog where the bars start. A log axis has no zero, so bars must be drawn from a
 *   real, labelled floor — otherwise their lengths mean nothing at all.
 */
data class AbundanceProfile(
    val symbol: String,
    val bars: List<AbundanceBar>,
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
     * element, which is what lets two cards be compared by eye.
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
        val bars = ORDER.mapNotNull { fieldId ->
            val spec = FieldRegistry.byId[fieldId] ?: return@mapNotNull null
            when (val value = element.value(fieldId)) {
                is FieldValue.Num -> {
                    // log10(0) is -Infinity and would blow the axis out to nothing.
                    val amount = value.quantity.mid
                    if (amount <= 0) null
                    else AbundanceBar(fieldId, label(spec, strings), log10(amount), false, value.quantity.display)
                }
                is FieldValue.Trace ->
                    AbundanceBar(fieldId, label(spec, strings), null, true, value.toString())
                else -> null
            }
        }
        if (bars.size < MIN_BARS) return null

        val logs = bars.mapNotNull { it.logMgPerKg }
        if (logs.isEmpty()) return null
        val minLog = logs.min()
        val maxLog = logs.max()
        return AbundanceProfile(
            symbol = element.symbol,
            bars = bars,
            minLog = minLog,
            maxLog = maxLog,
            floorLog = floor(minLog) - 0.5
        )
    }

    private fun label(spec: com.jlindemann.science.ai.data.FieldSpec, strings: StringProvider): String {
        val text = runCatching { strings.get(spec.sentenceLabel()) }.getOrNull()
        return if (text == null || text.startsWith("str:")) {
            spec.id.removePrefix("abundance_").replace('_', ' ')
        } else {
            text.replace(":", "").trim()
        }
    }
}
