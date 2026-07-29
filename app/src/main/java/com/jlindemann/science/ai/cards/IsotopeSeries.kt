package com.jlindemann.science.ai.cards

import com.jlindemann.science.ai.data.ElementRecord
import kotlin.math.log10

/** The authored decay modes collapsed to a small, colourable set. */
enum class DecayGroup { ALPHA, BETA_MINUS, BETA_PLUS_EC, ISOMERIC, FISSION, NUCLEON_EMISSION, OTHER, STABLE }

/**
 * One isotope positioned for plotting.
 *
 * @property logSeconds log₁₀ of the half-life in seconds, or null when the isotope is stable or its
 *   half-life could not be parsed. Null is meaningful and must survive to the view: a stable isotope
 *   has *no* finite half-life, and plotting it at the maximum would assert a number the data does
 *   not contain.
 */
data class IsotopePoint(
    val name: String,
    val massNumber: Int,
    val logSeconds: Double?,
    val stable: Boolean,
    val decayGroup: DecayGroup,
    val halfLifeDisplay: String
)

/**
 * An element's isotopes, ordered by mass number so the valley of stability is visible as a peak.
 *
 * @property groupsPresent so a legend lists only what is actually drawn
 */
data class IsotopeSeries(
    val symbol: String,
    val points: List<IsotopePoint>,
    val stableMassNumbers: Set<Int>,
    val minLogSeconds: Double,
    val maxLogSeconds: Double,
    val groupsPresent: Set<DecayGroup>
)

object IsotopeSeriesReducer {

    /** Fewer isotopes than this and a chart is noise. */
    const val MIN_ISOTOPES = 3

    /**
     * Build the series from an element's full isotope list.
     *
     * Reads `element.isotopes` rather than `ExecutionResult.Isotopes.shown`, which is truncated to
     * eight for the text answer. The chart's value is precisely that it can show all of them —
     * tin has ten stable isotopes and the text list never gets near them.
     *
     * `decay_type_N` is present in the shipped JSON and read by nothing outside the agent package,
     * so this surfaces data the app has always carried and never displayed.
     */
    fun of(element: ElementRecord): IsotopeSeries? {
        val points = element.isotopes.mapNotNull { isotope ->
            val mass = isotope.massNumber ?: return@mapNotNull null
            IsotopePoint(
                name = isotope.name,
                massNumber = mass,
                logSeconds = isotope.halfLifeSeconds?.takeIf { it > 0 }?.let { log10(it) },
                stable = isotope.stable,
                decayGroup = if (isotope.stable) DecayGroup.STABLE else group(isotope.decayType),
                halfLifeDisplay = isotope.halfLifeDisplay
            )
        }.sortedBy { it.massNumber }
        if (points.size < MIN_ISOTOPES) return null

        val logs = points.mapNotNull { it.logSeconds }
        return IsotopeSeries(
            symbol = element.symbol,
            points = points,
            stableMassNumbers = points.filter { it.stable }.map { it.massNumber }.toSet(),
            minLogSeconds = logs.minOrNull() ?: 0.0,
            maxLogSeconds = logs.maxOrNull() ?: 0.0,
            groupsPresent = points.map { it.decayGroup }.toSet()
        )
    }

    /**
     * Map an authored `decay_type` spelling onto a colourable group.
     *
     * The data uses a mix of notations — `α`, `A`, `B-`, `B+`, `EC`, `2p`, `SF`, `IT`. Anything
     * unrecognised lands in [DecayGroup.OTHER] rather than being dropped, and a test enumerates
     * every distinct spelling in the real assets so a new one shows up as a change in that set
     * rather than as a silently grey marker.
     */
    fun group(decayType: String?): DecayGroup {
        val value = decayType?.trim()?.lowercase().orEmpty()
        if (value.isEmpty() || value == "---" || value == "n/a") return DecayGroup.OTHER
        return when {
            value.startsWith("α") || value == "a" || value.startsWith("alpha") -> DecayGroup.ALPHA
            value.startsWith("β−") || value.startsWith("β-") || value.startsWith("b-") ||
                    value.startsWith("beta-") -> DecayGroup.BETA_MINUS
            value.startsWith("β+") || value.startsWith("b+") || value.startsWith("beta+") ||
                    value.startsWith("ec") || value.contains("electron capture") -> DecayGroup.BETA_PLUS_EC
            value.startsWith("it") || value.contains("isomeric") -> DecayGroup.ISOMERIC
            value.startsWith("sf") || value.contains("fission") -> DecayGroup.FISSION
            // Direct nucleon emission: "p", "2p", "n", "2n".
            value.matches(Regex("\\d*[pn]")) -> DecayGroup.NUCLEON_EMISSION
            else -> DecayGroup.OTHER
        }
    }
}
