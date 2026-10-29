package com.jlindemann.science.ai.data

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.StringProvider

/**
 * One element's place in one field's ranking (roadmap 2.7): "osmium is the densest of the 100
 * elements with a recorded density".
 *
 * @property rank 1 for the extreme, counted from the top when [highest] and from the bottom otherwise
 * @property of how many elements have a value for the field
 */
data class DataFact(
    val elementKey: String,
    val fieldId: String,
    val rank: Int,
    val of: Int,
    val highest: Boolean,
    val quantity: Quantity
)

/**
 * Facts read off the data rather than written by hand (roadmap 2.7), for the Science Daily widget,
 * Element of the Day and the chat's fun facts. Ten free fields that are worth ranking, over the
 * elements up to fermium: past it most values are predictions, and seaborgium's predicted density
 * would otherwise make it the densest element.
 */
class DataFacts(private val store: KnowledgeStore) {

    private val rankings: Map<String, List<Pair<ElementRecord, Quantity>>> = FIELDS.associateWith { fieldId ->
        store.elements
            .filter { it.atomicNumber in 1..LAST_MEASURED }
            .mapNotNull { element -> element.quantity(fieldId)?.takeIf { !it.isRange }?.let { element to it } }
            // Ties at either end go to the lighter element, the better measured: caesium and
            // francium share the lowest electronegativity, and francium's is an estimate.
            .sortedWith(compareByDescending<Pair<ElementRecord, Quantity>> { it.second.mid }.thenBy { it.first.atomicNumber })
    }.filterValues { it.size >= MIN_RANKED }

    /** The top and the bottom of every field: twenty facts, in a fixed order. */
    fun extremes(): List<DataFact> = rankings.flatMap { (fieldId, ranked) ->
        val bottom = ranked.filter { it.second.mid == ranked.last().second.mid }.minByOrNull { it.first.atomicNumber }!!
        listOf(
            DataFact(ranked.first().first.key, fieldId, 1, ranked.size, true, ranked.first().second),
            DataFact(bottom.first.key, fieldId, 1, ranked.size, false, bottom.second)
        )
    }

    /**
     * The element's most striking place, or null when it is nowhere near either end: the smallest
     * rank from the top or the bottom of any field, up to [NOTABLE_RANK].
     */
    fun forElement(elementKey: String): DataFact? = rankings.flatMap { (fieldId, ranked) ->
        val at = ranked.indexOfFirst { it.first.key == elementKey }
        if (at < 0) emptyList() else listOf(
            DataFact(elementKey, fieldId, at + 1, ranked.size, true, ranked[at].second),
            DataFact(elementKey, fieldId, ranked.size - at, ranked.size, false, ranked[at].second)
        )
    }.filter { it.rank <= NOTABLE_RANK }.minWithOrNull(compareBy({ it.rank }, { FIELDS.indexOf(it.fieldId) }))

    /** The fact for a given day, so a widget shows one fact all day and a new one tomorrow. */
    fun forDay(dayIndex: Int): DataFact? = extremes().takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(dayIndex, it.size)] }

    /** The fact as a sentence in the reader's language. */
    fun render(fact: DataFact, strings: StringProvider, names: LocalizedView?): String {
        val name = names?.name(fact.elementKey)?.takeIf { it.isNotBlank() }
            ?: fact.elementKey.replaceFirstChar { it.uppercase() }
        val value = FieldRegistry.byId[fact.fieldId]?.displayWithUnit(fact.quantity) { strings.get(it) }
            ?: fact.quantity.display
        val label = fieldLabel(fact.fieldId, strings)
        return when {
            fact.rank == 1 -> strings.get(
                if (fact.highest) R.string.ai_fact_data_highest else R.string.ai_fact_data_lowest,
                name, label, fact.of, value
            )
            else -> strings.get(
                if (fact.highest) R.string.ai_fact_data_rank_high else R.string.ai_fact_data_rank_low,
                name, fact.rank, fact.of, label, value
            )
        }
    }

    /** The sentence form of a field's label, as the answers use it. */
    private fun fieldLabel(fieldId: String, strings: StringProvider): String {
        val spec = FieldRegistry.byId[fieldId] ?: return fieldId.replace('_', ' ')
        val label = runCatching { strings.get(spec.sentenceLabel()) }.getOrNull()?.takeIf { !it.startsWith("str:") }
            ?: runCatching { strings.get(spec.labelRes) }.getOrNull()
        val text = if (label == null || label.startsWith("str:")) fieldId.replace('_', ' ')
        else label.replace(":", "").replace("：", "").trim()
        // Mid-sentence, so lower case as the answers' rank sentence has it, except German nouns.
        return if (strings.language == "de") text else text.lowercase()
    }

    companion object {
        /** Free fields worth ranking. Atomic mass is left out: it follows atomic number. */
        val FIELDS = listOf(
            "density", "melting_point", "boiling_point", "electronegativity", "atomic_radius",
            "ionization_energy", "thermal_conductivity", "specific_heat_capacity", "fusion_heat", "vaporization_heat"
        )

        /** Fermium. Past it most values in the data are predictions, not measurements. */
        const val LAST_MEASURED = 100

        /** A field needs this many values before its extremes mean anything. */
        private const val MIN_RANKED = 20

        /** How far from either end a place still makes an element's fact. */
        const val NOTABLE_RANK = 3
    }
}
