package com.jlindemann.science.ai.cards

import androidx.annotation.StringRes
import com.jlindemann.science.R
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.Nfpa

/** The four number/description pairs of an NFPA 704 diamond, already localized. */
data class NfpaLabels(
    val fire: String, val fireText: String,
    val health: String, val healthText: String,
    val reactivity: String, val reactivityText: String,
    val special: String, val specialText: String
)

/**
 * Turns NFPA ratings into localized labels.
 *
 * This is the single owner of that mapping. It previously existed twice — once in `AnswerComposer`
 * for the text answer, and once inside `InfoExtension.setHazards` as **hardcoded English literals**
 * in an app that ships twelve languages. The composer now delegates here rather than a third copy
 * being added for the card.
 *
 * Pure: takes a [StringProvider], returns strings, touches no view and no Context.
 */
object NfpaLabeller {

    fun labels(nfpa: Nfpa, strings: StringProvider): NfpaLabels = NfpaLabels(
        fire = rating(nfpa.flammability),
        fireText = strings.get(flammabilityDescription(nfpa.flammability)),
        health = rating(nfpa.health),
        healthText = strings.get(healthDescription(nfpa.health)),
        reactivity = rating(nfpa.instability),
        reactivityText = strings.get(instabilityDescription(nfpa.instability)),
        special = specialCode(nfpa.special),
        specialText = strings.get(specialDescription(nfpa.special))
    )

    /** True when at least one axis has a value, so a diamond would say something. */
    fun hasAnyRating(nfpa: Nfpa?): Boolean =
        nfpa != null &&
                (nfpa.health != null || nfpa.flammability != null ||
                        nfpa.instability != null || !nfpa.special.isNullOrBlank())

    @StringRes fun healthDescription(rating: Int?): Int = when (rating?.coerceIn(0, 4)) {
        0 -> R.string.ai_nfpa_health_0
        1 -> R.string.ai_nfpa_health_1
        2 -> R.string.ai_nfpa_health_2
        3 -> R.string.ai_nfpa_health_3
        4 -> R.string.ai_nfpa_health_4
        else -> R.string.ai_nfpa_unrated
    }

    @StringRes fun flammabilityDescription(rating: Int?): Int = when (rating?.coerceIn(0, 4)) {
        0 -> R.string.ai_nfpa_flammable_0
        1 -> R.string.ai_nfpa_flammable_1
        2 -> R.string.ai_nfpa_flammable_2
        3 -> R.string.ai_nfpa_flammable_3
        4 -> R.string.ai_nfpa_flammable_4
        else -> R.string.ai_nfpa_unrated
    }

    @StringRes fun instabilityDescription(rating: Int?): Int = when (rating?.coerceIn(0, 4)) {
        0 -> R.string.ai_nfpa_instability_0
        1 -> R.string.ai_nfpa_instability_1
        2 -> R.string.ai_nfpa_instability_2
        3 -> R.string.ai_nfpa_instability_3
        4 -> R.string.ai_nfpa_instability_4
        else -> R.string.ai_nfpa_unrated
    }

    /** The white quadrant's code: "SA", "W", "OX", or a dash when there is none. */
    fun specialCode(special: String?): String {
        val value = special?.trim().orEmpty()
        return when {
            value.isEmpty() || value == "---" -> "–"
            value.contains("asphyxiant", ignoreCase = true) -> "SA"
            value.equals("W", ignoreCase = true) -> "W"
            value.equals("OX", ignoreCase = true) -> "OX"
            else -> value.take(3).uppercase()
        }
    }

    @StringRes fun specialDescription(special: String?): Int = when (specialCode(special)) {
        "SA" -> R.string.ai_nfpa_special_sa
        "W" -> R.string.ai_nfpa_special_w
        "OX" -> R.string.ai_nfpa_special_ox
        else -> R.string.ai_nfpa_special_none
    }

    /** An absent rating shows as a dash rather than a misleading zero, which means "no hazard". */
    private fun rating(value: Int?): String = value?.coerceIn(0, 4)?.toString() ?: "–"
}
