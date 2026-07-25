package com.jlindemann.science.ai.compose

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.Aggregation
import com.jlindemann.science.ai.core.Citation
import com.jlindemann.science.ai.core.ExecutionResult
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedView
import com.jlindemann.science.ai.data.UnitConverter

/** Rendered answer text plus the actions it offers. */
data class ComposedAnswer(val text: String, val actions: List<ChatAction>)

/**
 * Turns an [ExecutionResult] into the markdown the chat adapter can actually render.
 *
 * The renderer supports only `###` headings and `**bold**`, so the vocabulary here is headings,
 * literal `• ` bullets and emphasis. Links are not renderable at all, which is why citations
 * become tappable action chips instead of inline URLs.
 */
class AnswerComposer(
    private val store: KnowledgeStore,
    private val localized: LocalizedView?,
    private val strings: StringProvider
) {

    fun compose(result: ExecutionResult, plan: QueryPlan): ComposedAnswer {
        val body = when (result) {
            is ExecutionResult.Property -> property(result)
            is ExecutionResult.Comparison -> comparison(result)
            is ExecutionResult.ElementList -> elementList(result, plan)
            is ExecutionResult.Aggregate -> aggregate(result)
            is ExecutionResult.Formula -> formula(result)
            is ExecutionResult.Nuclide -> nuclide(result)
            is ExecutionResult.MoleConversion -> moleConversion(result)
            is ExecutionResult.Isotopes -> isotopes(result)
            is ExecutionResult.Safety -> safety(result)
            is ExecutionResult.Dataset -> dataset(result)
            is ExecutionResult.NoData -> noData(result)
            is ExecutionResult.Empty -> strings.get(R.string.ai_filter_none)
        }
        return ComposedAnswer(
            text = body + citationBlock(result.citations),
            actions = result.citations.map { it.toAction(strings) }.distinctBy { it.label }
        )
    }

    // ---- Per-result rendering -----------------------------------------------------------

    private fun property(result: ExecutionResult.Property): String {
        val name = displayName(result.element.key)
        val label = fieldLabel(result.fieldId)
        val sentence = strings.get(R.string.ai_property_is, name, label, result.display)
        val notes = ArrayList<String>(2)
        result.quantity?.let { q ->
            if (q.isRange) notes.add(strings.get(R.string.ai_value_is_range))
            q.note?.let { notes.add(it) }
        }
        return sentence + notes.joinToString("") { "\n$it" }
    }

    private fun comparison(result: ExecutionResult.Comparison): String {
        // A single element is a category lookup, not a comparison: render it as a labelled
        // property list rather than "Comparing Gold:".
        if (result.elements.size == 1) {
            val element = result.elements.first()
            val builder = StringBuilder("### ").append(displayName(element.key))
            for (fieldId in result.fieldIds) {
                val row = result.values[fieldId]?.firstOrNull() ?: continue
                if (row.display.isBlank()) continue
                builder.append("\n").append(
                    strings.get(R.string.ai_list_row, fieldLabel(fieldId), row.display)
                )
            }
            return builder.toString()
        }

        val names = result.elements.joinToString(", ") { displayName(it.key) }
        val builder = StringBuilder(strings.get(R.string.ai_comparing_title, names))
        for (fieldId in result.fieldIds) {
            val rows = result.values[fieldId].orEmpty()
            if (rows.all { it.display.isBlank() }) continue
            builder.append("\n\n### ").append(fieldLabel(fieldId))
            for (row in rows) {
                val shown = row.display.ifBlank { strings.get(R.string.ai_no_data_right_now) }
                builder.append("\n").append(
                    strings.get(R.string.ai_list_row, displayName(row.element.key), shown)
                )
            }
        }
        return builder.toString()
    }

    private fun elementList(result: ExecutionResult.ElementList, plan: QueryPlan): String {
        if (result.results.isEmpty()) return strings.get(R.string.ai_filter_none)

        // A superlative asks for one element, so answer it as a sentence rather than a list.
        if (result.fieldId != null && plan.limit == 1) {
            val top = result.results.first()
            val direction = strings.get(
                if (result.descending) R.string.ai_superlative_highest else R.string.ai_superlative_lowest
            )
            val sentence = strings.get(
                R.string.ai_superlative_result,
                direction, fieldLabel(result.fieldId), displayName(top.element.key), top.display
            )
            val note = if (result.missing > 0)
                "\n" + strings.get(R.string.ai_aggregate_missing_note, result.missing) else ""
            return sentence + note
        }

        // Say plainly when the shown rows are only the top slice of a larger match set.
        val truncated = result.results.size < result.matched
        val builder = StringBuilder(
            if (truncated && result.fieldId != null)
                strings.get(R.string.ai_filter_top, result.results.size, result.matched)
            else strings.get(R.string.ai_filter_matched, result.matched)
        )
        for (row in result.results) {
            builder.append("\n").append(
                if (row.display.isBlank() || result.fieldId == null)
                    strings.get(R.string.ai_list_row_plain, displayName(row.element.key))
                else strings.get(R.string.ai_list_row, displayName(row.element.key), row.display)
            )
        }
        val remaining = result.matched - result.results.size
        if (remaining > 0) builder.append("\n").append(strings.get(R.string.ai_list_more, remaining))
        if (result.missing > 0 && result.fieldId != null) {
            builder.append("\n\n").append(strings.get(R.string.ai_aggregate_missing_note, result.missing))
        }
        return builder.toString()
    }

    private fun aggregate(result: ExecutionResult.Aggregate): String {
        if (result.aggregation == Aggregation.COUNT) {
            val builder = StringBuilder(strings.get(R.string.ai_count_result, result.value.toInt()))
            result.contributors.take(LIST_PREVIEW).forEach {
                builder.append("\n").append(strings.get(R.string.ai_list_row_plain, displayName(it.element.key)))
            }
            val remaining = result.contributors.size - LIST_PREVIEW
            if (remaining > 0) builder.append("\n").append(strings.get(R.string.ai_list_more, remaining))
            return builder.toString()
        }

        val label = when (result.aggregation) {
            Aggregation.MEDIAN -> strings.get(R.string.ai_aggregate_median, fieldLabel(result.fieldId))
            Aggregation.SUM -> strings.get(R.string.ai_aggregate_sum, fieldLabel(result.fieldId))
            else -> strings.get(R.string.ai_aggregate_average, fieldLabel(result.fieldId))
        }
        val rendered = UnitConverter.formatValue(result.value) + (result.unit?.let { " $it" } ?: "")
        val builder = StringBuilder(strings.get(R.string.ai_aggregate_result, label, rendered))

        // A partial statistic is always disclosed as partial.
        val total = result.contributors.size + result.missing
        builder.append("\n").append(strings.get(R.string.ai_aggregate_over, result.contributors.size, total))
        if (result.missing > 0) {
            builder.append(" ").append(strings.get(R.string.ai_aggregate_missing_note, result.missing))
        }
        return builder.toString()
    }

    private fun formula(result: ExecutionResult.Formula): String {
        val r = result.result
        val builder = StringBuilder(
            strings.get(R.string.ai_formula_mass, r.formula, format(r.molarMass))
        )
        if (result.wantsComposition) {
            builder.append("\n\n### ").append(strings.get(R.string.ai_composition_header, r.formula))
            for (part in r.parts) {
                builder.append("\n").append(
                    strings.get(
                        R.string.ai_composition_row, part.symbol, part.count,
                        format(part.massContribution), format(part.percent)
                    )
                )
            }
        }
        return builder.toString()
    }

    private fun nuclide(result: ExecutionResult.Nuclide): String {
        val name = displayName(result.element.key)
        return "### " + strings.get(R.string.ai_nuclide_header, name, result.massNumber) +
                "\n" + strings.get(
            R.string.ai_nuclide_explain, result.massNumber, result.protons, result.neutrons
        ) + "\n" + strings.get(
            R.string.ai_nuclide_body, result.protons, result.neutrons, result.massNumber
        )
    }

    private fun moleConversion(result: ExecutionResult.MoleConversion): String {
        val moles = result.moles ?: return ""
        val particles = scientific(result.particles)
        val sentence = if (result.substance != null) {
            strings.get(R.string.ai_moles_of, format(moles), result.substance, particles)
        } else {
            strings.get(R.string.ai_moles_to_particles, format(moles), particles)
        }
        return sentence + "\n" + strings.get(R.string.ai_avogadro_note)
    }

    /** Two significant decimals, trailing zeroes trimmed. */
    private fun format(value: Double): String = UnitConverter.formatValue(value)

    /** Renders a large count in scientific notation, e.g. 1.2 × 10²⁴. */
    private fun scientific(value: Double): String {
        if (value == 0.0) return "0"
        val exponent = kotlin.math.floor(kotlin.math.log10(kotlin.math.abs(value))).toInt()
        val mantissa = value / Math.pow(10.0, exponent.toDouble())
        return "${format(mantissa)} × 10${superscript(exponent)}"
    }

    private fun superscript(n: Int): String {
        val digits = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        val sign = if (n < 0) "⁻" else ""
        return sign + kotlin.math.abs(n).toString().map { digits[it - '0'] }.joinToString("")
    }

    private fun isotopes(result: ExecutionResult.Isotopes): String {
        val builder = StringBuilder("### ")
            .append(strings.get(R.string.ai_isotopes_header, displayName(result.element.key)))
        builder.append("\n").append(
            strings.get(R.string.ai_isotopes_summary, result.total, result.stableCount)
        )
        for (isotope in result.shown) {
            builder.append("\n").append(
                when {
                    isotope.stable -> strings.get(R.string.ai_isotope_stable, isotope.name)
                    isotope.decayType != null -> strings.get(
                        R.string.ai_isotope_decays, isotope.name, isotope.decayType, isotope.halfLifeDisplay
                    )
                    else -> strings.get(
                        R.string.ai_isotope_halflife_only, isotope.name, isotope.halfLifeDisplay
                    )
                }
            )
        }
        val remaining = result.total - result.shown.size
        if (remaining > 0) builder.append("\n").append(strings.get(R.string.ai_list_more, remaining))
        return builder.toString()
    }

    private fun safety(result: ExecutionResult.Safety): String {
        val builder = StringBuilder("### ")
            .append(strings.get(R.string.ai_safety_header, displayName(result.element.key)))
        val nfpa = result.nfpa
        var anyRating = false
        nfpa.health?.let {
            anyRating = true
            builder.append("\n").append(strings.get(R.string.ai_safety_health, it, healthLabel(it)))
        }
        nfpa.flammability?.let {
            anyRating = true
            builder.append("\n").append(
                strings.get(R.string.ai_safety_flammability, it, flammabilityLabel(it))
            )
        }
        nfpa.instability?.let {
            anyRating = true
            builder.append("\n").append(strings.get(R.string.ai_safety_instability, it))
        }
        if (!anyRating) {
            builder.append("\n").append(
                strings.get(R.string.ai_safety_none_recorded, displayName(result.element.key))
            )
        }
        if (result.radioactive) {
            builder.append("\n").append(strings.get(R.string.ai_safety_radioactive_note))
        }
        return builder.toString()
    }

    private fun healthLabel(rating: Int): String = strings.get(
        when (rating.coerceIn(0, 4)) {
            0 -> R.string.ai_nfpa_health_0
            1 -> R.string.ai_nfpa_health_1
            2 -> R.string.ai_nfpa_health_2
            3 -> R.string.ai_nfpa_health_3
            else -> R.string.ai_nfpa_health_4
        }
    )

    private fun flammabilityLabel(rating: Int): String = strings.get(
        when (rating.coerceIn(0, 4)) {
            0 -> R.string.ai_nfpa_flammable_0
            1 -> R.string.ai_nfpa_flammable_1
            2 -> R.string.ai_nfpa_flammable_2
            3 -> R.string.ai_nfpa_flammable_3
            else -> R.string.ai_nfpa_flammable_4
        }
    )

    private fun dataset(result: ExecutionResult.Dataset): String =
        "**${result.row.title}**\n${result.row.detail}"

    /**
     * The honest failure. Names the field, says it was checked, and reports how sparse it is —
     * rather than printing the raw sentinel or drifting to an unrelated answer.
     */
    private fun noData(result: ExecutionResult.NoData): String {
        val label = fieldLabel(result.fieldId)
        val name = result.element?.let { displayName(it.key) }
        val sentence = if (name != null) strings.get(R.string.ai_no_property_data, name, label)
        else strings.get(R.string.ai_no_data_right_now)
        val coverage = strings.get(R.string.ai_coverage_note, label, result.coverage, store.size)
        return "$sentence\n$coverage"
    }

    // ---- Citations ------------------------------------------------------------------------

    private fun citationBlock(citations: List<Citation>): String {
        if (citations.isEmpty()) return ""
        val lines = citations.distinctBy { it.label to it.source }.take(MAX_CITATIONS).map {
            strings.get(R.string.ai_source_element_data, it.label, it.source)
        }
        return "\n\n### ${strings.get(R.string.ai_sources_header)}\n" + lines.joinToString("\n") { "• $it" }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private fun displayName(key: String): String =
        localized?.name(key)?.takeIf { it.isNotBlank() } ?: key.replaceFirstChar { it.uppercase() }

    private fun fieldLabel(fieldId: String): String {
        val spec = FieldRegistry.byId[fieldId] ?: return fieldId.replace('_', ' ')
        val label = runCatching { strings.get(spec.labelRes) }.getOrNull()
        return if (label == null || label.startsWith("str:")) fieldId.replace('_', ' ')
        else label.replace(":", "").replace("：", "").trim()
    }

    private companion object {
        const val MAX_CITATIONS = 4
        const val LIST_PREVIEW = 10
    }
}
