package com.jlindemann.science.ai.chat

import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.retrieval.TextMatching
import kotlin.random.Random

/**
 * "Tell me more" — one fact about the element in focus that has not been said yet.
 *
 * The other half of the overview: the overview says the few things worth leading with, and this
 * walks the rest of the element one turn at a time, so a user can keep saying "more" and keep
 * learning something.
 *
 * This only *chooses* the field. Rendering goes through
 * [com.jlindemann.science.ai.core.AiEngine.fieldAnswerFor], so the sentence, the unit, the citation
 * and the PRO gate are the ones a direct question would have got.
 */
object ElementMoreInfo {

    /**
     * Bare "more" is deliberately absent: it is the ordinary comparative ("which has more
     * protons"), and claiming it would turn a question into a change of subject.
     */
    private val MORE_WORDS = listOf(
        "additional", "extra", "further", "tell me more", "what else", "deep dive",
        "keep going", "next", "continue", "anything else", "more info",
        "mer info", "beratta mer", "mer om", "vidare", "nasta", "fortsatt",
        "plus", "mehr", "weiter", "sonst noch", "mas", "mais", "altro", "ancora",
        "karagdagan", "aur", "aur batao", "और", "और बताओ", "मजीद",
        "更多", "继续", "还有"
    )

    /**
     * The fields "more" draws on, by JSON key, resolved through [FieldRegistry.byJsonKey] so the
     * list cannot silently drift from the registry. `thermal_conductivity` was once listed as
     * `element_thermal_conductivity`, which no element has, so it was never offered.
     */
    private val CANDIDATE_JSON_KEYS = listOf(
        "element_atomicmass", "element_boiling_celsius", "element_melting_celsius",
        "element_density", "element_electron_config", "element_electronegativty",
        "element_ion_charge", "element_ionization_energy1", "magnetic_type",
        "electrical_type", "element_block", "element_protons", "element_neutron_common",
        "element_electrons", "young_modulus", "poisson_ratio", "earth_crust",
        "crystal_structure", "cas_number", "molar_volume",
        "thermal_conductivity", "element_year"
    )

    /** The same list as field ids, in order. */
    val candidateFieldIds: List<String> = CANDIDATE_JSON_KEYS.mapNotNull { FieldRegistry.byJsonKey[it]?.id }

    /**
     * What reads naturally after what. A boiling point invites the melting point far more than a
     * CAS number, and following one with the other is most of the difference between a tour and a
     * shuffle.
     */
    private val READS_WELL_AFTER: List<Pair<String, List<String>>> = listOf(
        "boiling_point" to listOf("melting_point"),
        "melting_point" to listOf("boiling_point", "density"),
        "protons" to listOf("common_neutrons", "electrons"),
        "atomic_mass" to listOf("density", "abundance_earth_crust")
    )

    /** Whether this asks for another fact about whatever is already being discussed. */
    fun asks(normalizedQuery: String?): Boolean =
        !normalizedQuery.isNullOrBlank() && MORE_WORDS.any { TextMatching.containsWord(normalizedQuery, it) }

    /**
     * The candidate fields this element actually has a value for, in order. Filtered up front:
     * picking first and checking after, then recursing on a miss, could go eight deep for an
     * element missing eight of the twenty-two.
     */
    fun candidatesFor(element: ElementRecord): List<String> =
        candidateFieldIds.filter { !element.value(it).isMissing }

    /**
     * The next field to talk about, or null when they have all been covered.
     *
     * @param lastShared the most recent field said about this element, for the pacing above
     */
    fun next(
        candidates: List<String>,
        alreadyShared: (String) -> Boolean,
        lastShared: String?,
        random: Random
    ): String? {
        val remaining = candidates.filterNot(alreadyShared)
        if (remaining.isEmpty()) return null
        if (lastShared != null) {
            for ((after, prefer) in READS_WELL_AFTER) {
                if (after != lastShared) continue
                prefer.firstOrNull { it in remaining }?.let { return it }
            }
        }
        return remaining[random.nextInt(remaining.size)]
    }
}
