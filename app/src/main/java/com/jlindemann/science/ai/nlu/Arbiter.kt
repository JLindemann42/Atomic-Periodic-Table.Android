package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.core.Aggregation
import com.jlindemann.science.ai.core.Filter
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.data.ElementKey

/**
 * The planner's branches, each of which may propose a plan for a query (roadmap 1.8).
 *
 * The planner used to be a cascade: the first branch to return a plan won, so the order of ~30
 * `if` blocks in 900 lines decided every answer and could not be read anywhere. Every branch now
 * proposes, and [Arbiter] chooses.
 *
 * The branches up to [EXPLANATION] recognise what *kind* of question this is - chat, a formula, a
 * conversion, a compound, a request for prose, something to decline - and pre-empt in this order.
 * The rest read the same element-data question in different ways, and compete on evidence; this
 * order only breaks their ties.
 */
enum class Branch {
    DEFER_TO_PERSONALITY,
    OUT_OF_RANGE_ELEMENT,
    UNBACKED_EXPLANATION,
    UNBACKED_CONCEPT,
    COUNT_OF_ALL_ELEMENTS,
    CALCULATION,
    SHOW_STEPS,
    SET_OF_AN_ELEMENT,
    UNIT_CONVERT,
    COMPOUND,
    NARRATIVE_DECLINE,
    ETYMOLOGY_DECLINE,
    EXPLANATION,
    COMPARATIVE_AGAINST_FOCUS,
    COMPARATIVE_OVER_PREVIOUS,
    PERIPHRASTIC_COMPARATIVE,
    DIRECT_COMPARATIVE,
    AGGREGATE,
    SUPERLATIVE_OR_FILTERED_LIST,
    SUBSET_LIST,
    TREND,
    FIELD_COVERAGE_LIST,
    COMPARE_WITH_FOCUS,
    NEIGHBOUR,
    ALLOY,
    SELECTIVE_COMPARISON,
    COMPARISON,
    ISOTOPES,
    SAFETY,
    EMISSION_SPECTRUM,
    CATEGORY_LOOKUP,
    PROPERTY_LOOKUP,
    DATASET_ROW,
    NO_MATCH;

    /** Whether this branch pre-empts by order rather than competing on evidence. */
    val preempts: Boolean get() = this <= EXPLANATION
}

/**
 * What a query said, as the reading branches see it: the elements and fields it named, whether it
 * asked for a statistic, the unit it asked in, and which filters it did *not* say but inherited
 * from the turn before. Everything a plan holds that is not in here came from the conversation or
 * from a default.
 */
class Said(
    val elements: Set<ElementKey>,
    val fields: Set<String>,
    val statistic: Boolean,
    val unit: String?,
    val inheritedFilters: Set<Filter>
)

/**
 * A plan one branch proposes for a query, with the evidence it had when it proposed.
 *
 * @property uses the things the user said that this plan answers with - each named element and
 * field it uses, each filter the query stated, the statistic, the unit, and the words that only
 * this reading accounts for ("isotopes", "after", a comparative). The count is the proposal's
 * evidence; it is what the arbiter compares.
 */
data class Proposal(val branch: Branch, val plan: QueryPlan, val uses: Set<String> = emptySet()) {
    val score: Int get() = uses.size
}

/** Chooses among the planner's proposals (roadmap 1.8). */
object Arbiter {

    /**
     * The proposal to answer with. A pre-empting branch wins by [Branch] order; otherwise the
     * reading that accounts for most of what the user said, and on a tie the branch earlier in
     * [Branch] - and within one branch the proposal made first, since a branch that tries the
     * explicit "X is denser than Y" before its looser reading proposes both, and the explicit one
     * is the one it meant.
     */
    fun pick(proposals: List<Proposal>): Proposal? {
        val first = byOrder(proposals) ?: return null
        if (first.branch.preempts) return first
        var best = first
        for (proposal in proposals) {
            if (proposal.score > best.score ||
                (proposal.score == best.score && proposal.branch < best.branch)
            ) best = proposal
        }
        return best
    }

    /** The pick of stage 1, by order alone: kept so the tests can show what evidence changed. */
    fun byOrder(proposals: List<Proposal>): Proposal? = proposals.minByOrNull { it.branch.ordinal }

    /** What [plan] uses of what the query [said], plus the [cues] only its branch reads. */
    fun uses(plan: QueryPlan, said: Said?, cues: List<String>): Set<String> {
        if (said == null) return cues.toSet()
        return buildSet {
            plan.elementKeys.filter { it in said.elements }.forEach { add("element $it") }
            (plan.fieldIds + listOfNotNull(plan.sortField)).filter { it in said.fields }.forEach { add("field $it") }
            plan.filters.filter { it !in said.inheritedFilters }.forEachIndexed { i, filter -> add("filter ${i + 1}: $filter") }
            if (plan.aggregation != Aggregation.NONE && said.statistic) add("statistic")
            if (plan.targetUnit != null && plan.targetUnit == said.unit) add("unit")
            addAll(cues)
        }
    }
}
