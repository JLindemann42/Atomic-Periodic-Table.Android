package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.Aggregation
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.Dimension
import com.jlindemann.science.ai.data.ElementKey
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldKind
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore
import java.util.Locale

/**
 * Follow-up chips that fit the answer just given (roadmap 2.1).
 *
 * The fixed chips under a reply - "Uses of gold", "Atomic mass of gold" - say what the assistant can
 * do, but not what to ask next. These are built from the answer's own plan: after "the density of
 * gold", the same field for gold's neighbour in its group, the element with the highest density,
 * and gold's density in another unit; after a ranking, the other end of it; after a list, how many
 * there are.
 *
 * A chip is only offered if the engine would answer it: [answers] is asked with each candidate as
 * the next turn of the conversation, and a chip it would decline, or one that asks the question just
 * answered, is dropped. That is what keeps the templates honest in twelve languages - a phrasing the
 * planner cannot read in one of them is never shown there, rather than leading to "I don't know".
 *
 * @param answers the plan the engine would answer [String] with as the next turn, or null
 * @param name an element's name in the reader's language
 */
class FollowUps(
    private val store: KnowledgeStore,
    private val answers: (String) -> QueryPlan?,
    private val name: (ElementKey) -> String
) {

    /** Up to [limit] chips for the answer [plan] produced, most specific first. */
    fun after(plan: QueryPlan, strings: StringProvider, limit: Int): List<String> {
        val chips = LinkedHashSet<String>()
        for (candidate in candidates(plan, strings)) {
            if (chips.size >= limit) break
            val next = answers(candidate) ?: continue
            if (!asksTheSame(next, plan)) chips.add(candidate)
        }
        return chips.toList()
    }

    /** Whether [chip] would ask [plan] again, so offering it would only repeat the answer. */
    fun repeats(chip: String, plan: QueryPlan): Boolean = answers(chip)?.let { asksTheSame(it, plan) } ?: false

    private fun candidates(plan: QueryPlan, strings: StringProvider): List<String> {
        val field = plan.fieldIds.firstOrNull()?.let { FieldRegistry.byId[it] }
            ?.takeIf { it.kind == FieldKind.NUMERIC }
        val label = field?.let { label(it.id, strings) }
        return when (plan.intent) {
            Intent.PROPERTY_LOOKUP -> {
                val element = plan.elementKeys.firstOrNull()?.let { store.element(it) }
                if (element == null || field == null || label == null) emptyList()
                else listOfNotNull(
                    partner(element, field.id)?.let {
                        format(strings, R.string.ai_follow_compare, label, name(element.key), name(it.key))
                    },
                    format(strings, R.string.ai_follow_highest, label),
                    otherUnit(field.dimension, plan.targetUnit)?.let {
                        format(strings, R.string.ai_follow_unit, label, name(element.key), it)
                    }
                )
            }
            // The pair is answered; what is left is where the field tops out.
            Intent.COMPARISON, Intent.COMPARATIVE ->
                listOfNotNull(label?.let { format(strings, R.string.ai_follow_highest, it) })
            // A ranking over the whole table invites its other end. Within a subset the chip would
            // have to name the subset, which it cannot do in every language, so it is left out.
            Intent.SUPERLATIVE -> if (label == null || plan.filters.isNotEmpty()) emptyList() else listOf(
                format(strings, if (plan.sortDescending) R.string.ai_follow_lowest else R.string.ai_follow_highest, label)
            )
            // A calculation invites its working, unless that is what was just shown (roadmap 2.2).
            in Intent.CALCULATIONS -> if (plan.showSteps) emptyList() else listOf(strings.get(R.string.ai_follow_steps))
            Intent.FILTER_LIST -> if (plan.filters.isEmpty()) emptyList() else listOf(strings.get(R.string.ai_follow_count))
            Intent.AGGREGATE ->
                if (plan.filters.isEmpty() || plan.aggregation == Aggregation.COUNT) emptyList()
                else listOf(strings.get(R.string.ai_follow_count))
            else -> emptyList()
        }
    }

    /**
     * The element to compare with: the nearest in the same group that has a value for [fieldId], and
     * for the lanthanides and actinides, which have no group, the nearest in the series. A natural
     * element is preferred to a synthetic one, and on a tie the lighter: gold's partner is silver,
     * not roentgenium, whose values are estimates.
     */
    private fun partner(element: ElementRecord, fieldId: String): ElementRecord? {
        val candidates = store.elements.filter {
            it.key != element.key && !it.value(fieldId).isMissing && (element.synthetic || !it.synthetic)
        }
        val group = element.groupNumber
        return if (group != null) {
            candidates.filter { it.groupNumber == group }
                .minWithOrNull(compareBy({ kotlin.math.abs(it.period - element.period) }, { it.period }))
        } else {
            candidates.filter { it.groupNumber == null && it.series == element.series }
                .minWithOrNull(compareBy({ kotlin.math.abs(it.atomicNumber - element.atomicNumber) }, { it.atomicNumber }))
        }
    }

    /** A unit the converter really converts [dimension] to, other than the one just shown, as written. */
    private fun otherUnit(dimension: Dimension?, shown: String?): String? {
        val units = OTHER_UNITS[dimension] ?: return null
        return units.firstOrNull { (_, unit) -> unit != shown }?.first
    }

    /** The field as the composer names it in a sentence, lower-cased where the language has case. */
    private fun label(fieldId: String, strings: StringProvider): String? {
        val spec = FieldRegistry.byId[fieldId] ?: return null
        val raw = runCatching { strings.get(spec.sentenceLabel()) }.getOrNull()?.takeIf { !it.startsWith("str:") }
            ?: runCatching { strings.get(spec.labelRes) }.getOrNull()?.takeIf { !it.startsWith("str:") }
            ?: return null
        val label = raw.replace(":", "").replace("：", "").trim()
        // The labels are table headings, "Atomic Mass". German capitalises every noun; elsewhere a
        // label inside a question is lower-case, except an acronym ("NFPA").
        if (strings.language == "de") return label
        return label.split(' ').joinToString(" ") { word ->
            if (word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() }) word else word.lowercase(Locale.ROOT)
        }
    }

    private fun format(strings: StringProvider, id: Int, vararg args: String): String =
        strings.get(id, *args).replaceFirstChar { it.uppercase(Locale.ROOT) }

    private companion object {
        /**
         * Units a field can be shown in besides its own, by dimension, in the order offered: how the
         * chip writes it, and the unit that is. Only units
         * [com.jlindemann.science.ai.data.UnitConverter] converts to; eV to kJ/mol is one of its
         * bridges, which apply when a conversion is asked for outright, as a chip asks. Ångström is
         * spelled out because a bare "Å" folds to "a", too short to be read as a unit.
         */
        val OTHER_UNITS: Map<Dimension, List<Pair<String, String>>> = mapOf(
            Dimension.TEMPERATURE to listOf("°F" to "°F", "°C" to "°C"),
            Dimension.DENSITY to listOf("kg/m³" to "kg/m³", "g/cm³" to "g/cm³"),
            Dimension.LENGTH to listOf("ångström" to "Å", "pm" to "pm"),
            Dimension.ENERGY to listOf("kJ/mol" to "kJ/mol", "eV" to "eV"),
            Dimension.ENERGY_PER_MOL to listOf("eV" to "eV", "kJ/mol" to "kJ/mol")
        )

        /** The same question: the same reading of the same elements and fields. */
        fun asksTheSame(next: QueryPlan, previous: QueryPlan): Boolean =
            next.intent == previous.intent && next.elementKeys == previous.elementKeys &&
                    next.fieldIds == previous.fieldIds && next.filters == previous.filters &&
                    next.sortDescending == previous.sortDescending && next.targetUnit == previous.targetUnit &&
                    next.showSteps == previous.showSteps
    }
}
