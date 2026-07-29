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
import com.jlindemann.science.ai.cards.CardSelector
import com.jlindemann.science.ai.cards.ChatCardPolicy
import com.jlindemann.science.ai.cards.NfpaLabeller
import com.jlindemann.science.ai.data.UnitConverter

/**
 * Rendered answer text, the actions it offers, and the visual that goes with it.
 *
 * [card] is defaulted so every existing construction site compiles untouched, and null far more
 * often than not — most answers have no visual worth adding.
 */
data class ComposedAnswer(
    val text: String,
    val actions: List<ChatAction>,
    val card: com.jlindemann.science.ai.cards.ChatCard? = null
)

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
    private val strings: StringProvider,
    /**
     * Runtime switches for the visual cards — offline mode, Pro gating, minimum data thresholds.
     * Defaulted so every existing construction site compiles untouched.
     */
    private val cardPolicy: ChatCardPolicy = ChatCardPolicy.DEFAULT
) {

    /**
     * Render without the sources block.
     *
     * A compound answer composes each clause separately and then emits one merged Sources
     * section, rather than repeating the heading after every part.
     */
    fun composeBody(result: ExecutionResult, plan: QueryPlan): ComposedAnswer =
        compose(result, plan, includeCitations = false)

    fun compose(result: ExecutionResult, plan: QueryPlan): ComposedAnswer =
        compose(result, plan, includeCitations = true)

    private fun compose(
        result: ExecutionResult,
        plan: QueryPlan,
        includeCitations: Boolean
    ): ComposedAnswer {
        val body = when (result) {
            is ExecutionResult.Property -> property(result)
            is ExecutionResult.Comparison -> comparison(result) + uncoveredAspects(plan)
            is ExecutionResult.ElementList -> elementList(result, plan)
            is ExecutionResult.Aggregate -> aggregate(result)
            is ExecutionResult.Formula -> formula(result)
            is ExecutionResult.Comparative -> comparative(result)
            is ExecutionResult.Neighbour -> neighbour(result)
            is ExecutionResult.Nuclide -> nuclide(result)
            is ExecutionResult.IsotopeComparison -> isotopeComparison(result)
            is ExecutionResult.MoleConversion -> moleConversion(result)
            is ExecutionResult.Isotopes -> isotopes(result)
            is ExecutionResult.Safety -> safety(result)
            is ExecutionResult.EmissionSpectrum -> emissionSpectrum(result)
            is ExecutionResult.Dataset -> dataset(result)
            is ExecutionResult.NoData -> noData(result)
            is ExecutionResult.Locked -> locked(result)
            is ExecutionResult.Empty -> strings.get(R.string.ai_filter_none)
        }
        return ComposedAnswer(
            text = if (includeCitations) body + citationBlock(result.citations) else body,
            actions = (
                result.citations.map { it.toAction(strings) } +
                        // A locked answer offers the way out, using the same chip row as citations
                        // rather than a second affordance nobody has seen before.
                        listOfNotNull(upgradeAction(result))
                ).distinctBy { it.label },
            // Derived from the typed result, never from the query text — so the picture can only
            // show what the prose above it has already asserted.
            card = CardSelector.select(result, plan, store, strings, localized, cardPolicy)
        )
    }

    /** The "Get PRO" chip shown beside a withheld answer, or null when nothing was withheld. */
    private fun upgradeAction(result: ExecutionResult): ChatAction? {
        if (result !is ExecutionResult.Locked) return null
        val label = strings.get(
            if (result.tier == com.jlindemann.science.ai.data.Tier.PRO_PLUS) R.string.get_pro_plus_button
            else R.string.get_pro_button
        )
        return ChatAction(label, com.jlindemann.science.ai.data.DeepLinkTarget.PRO_PAGE)
    }

    /** The sources section on its own, for a caller assembling several parts. */
    fun citationsFor(citations: List<Citation>): String = citationBlock(citations)

    // ---- Per-result rendering -----------------------------------------------------------

    /**
     * A property answer, with enough context to mean something.
     *
     * A bare figure is not an answer to most people: "19.3 g/cm³" only lands if you already
     * know the range it sits in. Adding where the value ranks, and what kind of element this is,
     * turns a lookup into something a reader learns from.
     */
    private fun property(result: ExecutionResult.Property): String {
        val name = displayName(result.element.key)
        val label = fieldLabel(result.fieldId)
        val builder = StringBuilder(
            strings.get(R.string.ai_property_is, name, label, result.display)
        )

        result.quantity?.let { q ->
            if (q.isRange) builder.append("\n").append(strings.get(R.string.ai_value_is_range))
            q.note?.let { builder.append("\n").append(it) }
        }

        rankSentence(result)?.let { builder.append("\n").append(it) }
        return builder.toString()
    }

    /** "That is the highest of 105 elements…", or where it ranks. Null when not rankable. */
    private fun rankSentence(result: ExecutionResult.Property): String? {
        val rank = result.rank ?: return null
        if (result.rankedOutOf < 2) return null
        val label = fieldLabel(result.fieldId).lowercase()
        return when (rank) {
            1 -> strings.get(R.string.ai_rank_highest, result.rankedOutOf, label)
            result.rankedOutOf -> strings.get(R.string.ai_rank_lowest, result.rankedOutOf, label)
            else -> strings.get(R.string.ai_rank_nth, rank, result.rankedOutOf, label)
        }
    }

    private fun comparative(result: ExecutionResult.Comparative): String {
        val winner = displayName(result.winner.key)
        val loser = displayName(result.loser.key)
        val label = fieldLabel(result.fieldId).lowercase()
        val winnerValue = result.winnerValue.display
        val loserValue = result.loserValue.display

        val headline = when {
            // A yes/no question gets a yes or a no, before any figures.
            result.claimHolds == true ->
                strings.get(R.string.ai_comparative_yes, winner, winnerValue, loser, loserValue, label)
            result.claimHolds == false ->
                strings.get(R.string.ai_comparative_no, winner, winnerValue, loser, loserValue, label)
            else ->
                strings.get(R.string.ai_comparative_winner, winner, winnerValue, loser, loserValue, label)
        }

        val ratio = result.ratio ?: return headline
        return headline + "\n" + strings.get(
            R.string.ai_comparative_ratio,
            winner, format(ratio), loser, "$winnerValue ${strings.get(R.string.ai_versus)} $loserValue", label
        )
    }

    private fun neighbour(result: ExecutionResult.Neighbour): String {
        val target = displayName(result.to.key)
        val from = displayName(result.from.key)
        val sentence = strings.get(
            if (result.forward) R.string.ai_neighbour_after else R.string.ai_neighbour_before,
            target, result.to.atomicNumber, from
        )
        return sentence + "\n" + elementContext(result.to)
    }

    /** One line placing an element: what family it belongs to and which period. */
    private fun elementContext(element: com.jlindemann.science.ai.data.ElementRecord): String {
        val series = localized?.elements?.get(element.key)?.seriesName?.takeIf { it.isNotBlank() }
            ?: element.series.name.lowercase().replace('_', ' ')
        return strings.get(
            R.string.ai_context_series, displayName(element.key), series, element.period
        )
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
        verdict(result)?.let { builder.append("\n\n").append(it) }
        return builder.toString()
    }

    /**
     * Who came out ahead, for a two-element comparison.
     *
     * A table of five properties against two elements is data, not an answer — it leaves the reader
     * to do the comparison the question asked for. Counting the fields where each element holds the
     * higher value turns it back into an answer, without claiming that higher is *better*: the
     * sentence says "has the higher value", which is the only thing the numbers support.
     *
     * Two elements only. With three or more, "leads on 2 of 5" hides more than it says, and the
     * table really is the right shape for the answer.
     */
    private fun verdict(result: ExecutionResult.Comparison): String? {
        if (result.elements.size != 2) return null
        val wins = HashMap<String, Int>()
        var decided = 0
        for (fieldId in result.fieldIds) {
            val rows = result.values[fieldId].orEmpty().filter { it.quantity != null }
            if (rows.size != 2) continue
            val top = rows.maxByOrNull { it.quantity!!.value } ?: continue
            val bottom = rows.minByOrNull { it.quantity!!.value } ?: continue
            if (top.quantity!!.value == bottom.quantity!!.value) continue
            decided++
            wins[top.element.key] = (wins[top.element.key] ?: 0) + 1
        }
        if (decided < MIN_FIELDS_TO_JUDGE) return null
        val leader = wins.maxByOrNull { it.value } ?: return null
        // An even split has no verdict worth stating.
        if (wins.values.count { it == leader.value } > 1) return null
        return strings.get(
            R.string.ai_comparison_verdict, displayName(leader.key), leader.value, decided
        )
    }

    /**
     * Name the aspects a multi-part comparison asked for but could not cover.
     *
     * "Compare lithium, sodium and potassium on reactivity, density and applications" can only
     * answer density from stored fields. Saying so is the difference between an incomplete
     * answer and a misleading one.
     */
    private fun uncoveredAspects(plan: QueryPlan): String {
        val named = plan.evidence
            .filter { it.startsWith("no field for ") }
            .map { it.removePrefix("no field for ") }
            .distinct()
        // Drop terms contained in another, so "application, applications" reads as one aspect.
        val missing = named.filter { term -> named.none { it != term && it.contains(term) } }
        if (missing.isEmpty()) return ""
        return "\n\n" + strings.get(R.string.ai_aspects_not_compared, missing.joinToString(", "))
    }

    private fun elementList(result: ExecutionResult.ElementList, plan: QueryPlan): String {
        if (result.results.isEmpty()) return strings.get(R.string.ai_filter_none)

        // A superlative asks for one element, so answer it as a sentence rather than a list.
        if (result.fieldId != null && plan.limit == 1) {
            val top = result.results.first()
            val direction = strings.get(
                if (result.descending) R.string.ai_superlative_highest else R.string.ai_superlative_lowest
            )
            val sentence = if (result.rankOffset > 0) {
                // "the third densest element" — say which place, or the answer looks wrong.
                val place = result.rankOffset + 1
                strings.get(
                    R.string.ai_superlative_nth, place, ordinalSuffix(place),
                    direction, fieldLabel(result.fieldId).lowercase(),
                    displayName(top.element.key), top.display
                )
            } else {
                strings.get(
                    R.string.ai_superlative_result,
                    direction, fieldLabel(result.fieldId), displayName(top.element.key), top.display
                )
            }
            val builder = StringBuilder(sentence)

            // Say what the winner won against. A plain property lookup already reports "the 6th
            // densest of 105 elements with a recorded density"; a superlative was answering with a
            // bare winner and no sense of the field it topped. Reuses the same two strings, so this
            // costs no new translation in any of the fourteen locales.
            val withAValue = result.matched - result.missing
            if (withAValue >= MIN_POOL_TO_REPORT && result.rankOffset == 0) {
                builder.append("\n").append(
                    strings.get(
                        if (result.descending) R.string.ai_rank_highest else R.string.ai_rank_lowest,
                        withAValue,
                        fieldLabel(result.fieldId).lowercase()
                    )
                )
            }

            // The next one down turns a bare winner into a sense of the margin.
            result.runnerUp?.let {
                builder.append("\n").append(
                    strings.get(R.string.ai_runner_up, displayName(it.element.key), it.display)
                )
            }
            if (result.missing > 0) {
                builder.append("\n").append(
                    strings.get(R.string.ai_aggregate_missing_note, result.missing)
                )
            }
            return builder.toString()
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

        // Give the list a shape: the span it covers is more informative than the rows alone.
        if (result.fieldId != null && result.results.size > 2) {
            val values = result.results.mapNotNull { it.display.takeIf { d -> d.isNotBlank() } }
            if (values.size > 2) {
                builder.append("\n\n").append(
                    strings.get(R.string.ai_list_spread, values.first(), values.last())
                )
            }
        }
        if (result.missing > 0 && result.fieldId != null) {
            builder.append("\n").append(strings.get(R.string.ai_aggregate_missing_note, result.missing))
        }
        return builder.toString()
    }

    /** English ordinal suffix: 1st, 2nd, 3rd, 4th, with the teens handled. */
    private fun ordinalSuffix(n: Int): String = strings.get(
        when {
            n % 100 in 11..13 -> R.string.ai_ordinal_suffix_th
            n % 10 == 1 -> R.string.ai_ordinal_suffix_st
            n % 10 == 2 -> R.string.ai_ordinal_suffix_nd
            n % 10 == 3 -> R.string.ai_ordinal_suffix_rd
            else -> R.string.ai_ordinal_suffix_th
        }
    )

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

        // An average with no spread is the classic misleading statistic: the mean density of the
        // transition metals is about 10 g/cm³, and nothing in that number hints that the set runs
        // from scandium at 3 to osmium at 22.6. Both ends are already in `contributors`, so this
        // costs a scan rather than another pass over the table. Sums are excluded — the endpoints of
        // a total say nothing about it.
        if (result.aggregation != Aggregation.SUM && result.contributors.size >= MIN_POOL_TO_REPORT) {
            val ordered = result.contributors
                .filter { it.quantity != null }
                .sortedBy { it.quantity!!.value }
            val low = ordered.firstOrNull()
            val high = ordered.lastOrNull()
            if (low != null && high != null && low.element.key != high.element.key) {
                builder.append("\n").append(
                    strings.get(
                        R.string.ai_aggregate_spread,
                        low.display, displayName(low.element.key),
                        high.display, displayName(high.element.key)
                    )
                )
            }
        }

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
        // The per-element breakdown *is* the calculation, so a compound shows it whether or not the
        // word "composition" was used. Asked for the molar mass of Ca(NO3)2 the agent gave the total
        // and nothing else, and the follow-up "show the calculation step by step" then retrieved the
        // dictionary definition of stoichiometry. A single-element formula is excluded: one row
        // restating the total is noise. Reuses the existing rows, so no locale gains a string.
        if (result.wantsComposition || r.parts.size >= MIN_PARTS_TO_BREAK_DOWN) {
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

    private fun isotopeComparison(result: ExecutionResult.IsotopeComparison): String {
        val left = result.left
        val right = result.right
        val leftName = nuclideName(left)
        val rightName = nuclideName(right)

        val builder = StringBuilder("### ")
            .append(strings.get(R.string.ai_isotope_compare_header, leftName, rightName))

        // The interesting fact for two isotopes of one element is that only the neutrons differ,
        // so lead with that rather than repeating the shared proton count as a difference.
        if (left.element.key == right.element.key) {
            builder.append("\n").append(
                strings.get(
                    R.string.ai_isotope_same_element,
                    displayName(left.element.key), left.protons
                )
            )
            builder.append("\n").append(
                strings.get(
                    R.string.ai_isotope_neutron_diff, leftName, left.neutrons,
                    rightName, right.neutrons, kotlin.math.abs(left.neutrons - right.neutrons)
                )
            )
        } else {
            builder.append("\n").append(
                row(R.string.ai_label_protons, left.protons.toString(), right.protons.toString())
            )
        }

        builder.append("\n").append(
            row(R.string.ai_label_mass_number, left.massNumber.toString(), right.massNumber.toString())
        )
        builder.append("\n").append(
            row(R.string.ai_label_neutrons, left.neutrons.toString(), right.neutrons.toString())
        )
        builder.append("\n").append(
            row(R.string.ai_label_half_life, halfLife(left), halfLife(right))
        )
        val decayLeft = left.isotope?.decayType
        val decayRight = right.isotope?.decayType
        if (decayLeft != null || decayRight != null) {
            builder.append("\n").append(
                row(R.string.ai_label_decay, decayLeft ?: "—", decayRight ?: "—")
            )
        }
        return builder.toString()
    }

    private fun row(labelRes: Int, left: String, right: String): String =
        strings.get(R.string.ai_isotope_compare_row, strings.get(labelRes), left, right)

    private fun nuclideName(facts: ExecutionResult.NuclideFacts): String =
        "${displayName(facts.element.key)}-${facts.massNumber}"

    /** The authored half-life, "stable", or an admission that the nuclide is not listed. */
    private fun halfLife(facts: ExecutionResult.NuclideFacts): String {
        val isotope = facts.isotope ?: return strings.get(R.string.ai_isotope_unlisted)
        if (isotope.stable) return strings.get(R.string.ai_stable_label)
        val display = isotope.halfLifeDisplay.ifBlank {
            return strings.get(R.string.ai_isotope_unlisted)
        }
        return groupDigits(display)
    }

    /**
     * Insert thin spaces into long digit runs, so a half-life reads as "703 800 000 years"
     * rather than an unbroken nine-digit string. Uses spaces rather than a locale separator
     * because the value is authored text being made legible, not a number being formatted.
     */
    private fun groupDigits(text: String): String =
        Regex("""\d{5,}""").replace(text) { match ->
            match.value.reversed().chunked(3).joinToString(" ").reversed()
        }

    private fun moleConversion(result: ExecutionResult.MoleConversion): String {
        val moles = result.moles ?: return ""
        val particles = scientific(result.particles)

        // Mass to moles is the question the other way round, and it is the one users actually ask —
        // they have a mass on a balance. It answers with the mole count rather than a particle
        // count, so the Avogadro note below would be answering something else.
        result.grams?.let { grams ->
            return strings.get(
                R.string.ai_grams_to_moles,
                format(grams),
                result.substance.orEmpty(),
                format(moles)
            )
        }

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

    /**
     * The emission spectrum, which is a picture and a disclaimer.
     *
     * The sentence says what the card below it shows and, in the same breath, that the app holds no
     * wavelengths — so the agent is never asked to name a line it does not have. Offline the card is
     * dropped by [ChatCardPolicy], and the text says so rather than describing an image that is not
     * going to appear, reusing the same wording the element screen shows in that situation.
     */
    private fun emissionSpectrum(result: ExecutionResult.EmissionSpectrum): String {
        val name = displayName(result.element.key)
        val body = strings.get(R.string.ai_emission_spectrum, name)
        if (cardPolicy.allowNetworkCards) return body
        return body + "\n" + strings.get(R.string.go_online_for_emission)
    }

    // Delegated rather than duplicated: NfpaLabeller owns the rating-to-wording mapping, because
    // the hazard diamond card and the element screen need exactly the same table. It used to exist
    // here and again, as hardcoded English, inside InfoExtension.
    private fun healthLabel(rating: Int): String =
        strings.get(NfpaLabeller.healthDescription(rating))

    private fun flammabilityLabel(rating: Int): String =
        strings.get(NfpaLabeller.flammabilityDescription(rating))

    /**
     * A row from one of the app's reference tables.
     *
     * Those tables are English-only, which `DatasetIndex` documents and says the composer "flags
     * rather than pretending otherwise". It did not flag it, so a Swedish user asking what an
     * isotope is got an English definition with no indication why. Said once, and only when the
     * conversation is not already in English.
     */
    private fun dataset(result: ExecutionResult.Dataset): String {
        val body = "**${result.row.title}**\n${result.row.detail}"
        if (strings.language == ENGLISH) return body
        val note = runCatching { strings.get(R.string.ai_dataset_english_note) }.getOrNull()
        return if (note == null || note.startsWith("str:")) body else "$body\n\n$note"
    }

    /**
     * An answer withheld behind the paywall.
     *
     * Deliberately different from [noData]: this says the app *has* the value and is not showing it,
     * rather than claiming not to know. Telling a user "I don't have that" about a figure their
     * element screen visibly displays as a lock would be a lie, and a confusing one.
     *
     * The fields are named. Knowing *what* is behind the paywall is not the same as being told the
     * value, and it is what makes the upsell concrete rather than a blank refusal.
     */
    private fun locked(result: ExecutionResult.Locked): String {
        val labels = result.fieldIds.mapNotNull { FieldRegistry.byId[it] }
            .joinToString(", ") { fieldLabel(it.id).lowercase() }
            .ifBlank { null }
        val tierName = strings.get(
            if (result.tier == com.jlindemann.science.ai.data.Tier.PRO_PLUS) R.string.get_pro_plus
            else R.string.get_pro
        )
        return if (labels == null) {
            strings.get(R.string.ai_locked_generic, tierName)
        } else {
            strings.get(R.string.ai_locked_fields, labels, tierName)
        }
    }

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
        val builder = StringBuilder(sentence).append("\n").append(coverage)

        // Say what *is* known. "Tungsten has no recorded Curie point" is honest and a dead end; the
        // same answer plus the neighbouring properties that are recorded gives the reader somewhere
        // to go, and costs nothing — those values are already loaded.
        result.element?.let { element ->
            val siblings = FieldRegistry.byId[result.fieldId]?.category?.let { category ->
                FieldRegistry.ALL
                    .filter { it.category == category && it.id != result.fieldId }
                    .filter { element.value(it.id) != null }
                    .take(MAX_ALTERNATIVES)
                    .map { fieldLabel(it.id) }
            }.orEmpty()
            if (siblings.isNotEmpty()) {
                builder.append("\n").append(
                    strings.get(
                        R.string.ai_no_data_alternatives,
                        displayName(element.key),
                        siblings.joinToString(", ")
                    )
                )
            }
        }
        return builder.toString()
    }

    // ---- Citations ------------------------------------------------------------------------

    private fun citationBlock(citations: List<Citation>): String {
        if (citations.isEmpty()) return ""
        val lines = citations.distinctBy { it.label to it.source }.take(MAX_CITATIONS).map {
            if (it.fromTable) strings.get(R.string.ai_source_table, it.label, it.source)
            else strings.get(R.string.ai_source_element_data, it.label, it.source)
        }
        return "\n\n### ${strings.get(R.string.ai_sources_header)}\n" + lines.joinToString("\n") { "• $it" }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private fun displayName(key: String): String =
        localized?.name(key)?.takeIf { it.isNotBlank() } ?: key.replaceFirstChar { it.uppercase() }

    private fun fieldLabel(fieldId: String): String {
        val spec = FieldRegistry.byId[fieldId] ?: return fieldId.replace('_', ' ')
        // The sentence form where one is defined: the shipped labels are table headings, and the
        // abundance ones are prepositional phrases that read as nonsense inside a sentence.
        val label = runCatching { strings.get(spec.sentenceLabel()) }.getOrNull()
            ?.takeIf { !it.startsWith("str:") }
            ?: runCatching { strings.get(spec.labelRes) }.getOrNull()
        return if (label == null || label.startsWith("str:")) fieldId.replace('_', ' ')
        else label.replace(":", "").replace("：", "").trim()
    }

    private companion object {
        const val MAX_CITATIONS = 4
        const val LIST_PREVIEW = 10

        /** The one language the app's reference tables are written in. */
        const val ENGLISH = "en"

        /** Below this, naming the pool a superlative won against adds noise rather than context. */
        const val MIN_POOL_TO_REPORT = 3

        /** Below this many distinct elements a formula breakdown just repeats the total. */
        const val MIN_PARTS_TO_BREAK_DOWN = 2

        /** Below this many decided fields, a tally of wins says less than the table above it. */
        const val MIN_FIELDS_TO_JUDGE = 2

        /** Longest list of neighbouring properties offered beside a missing one. */
        const val MAX_ALTERNATIVES = 4
    }
}
