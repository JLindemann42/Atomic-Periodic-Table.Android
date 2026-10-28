package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.core.QueryPlan

/**
 * The planner's branches, each of which may propose a plan for a query (roadmap 1.8).
 *
 * The planner used to be a cascade: the first branch to return a plan won, so the order of ~30
 * `if` blocks in 900 lines decided every answer and could not be read anywhere. Every branch now
 * proposes, and [Arbiter] chooses. In this first stage the choice is still the old order - this
 * enum's - so no answer changes; the order is simply data now. Later stages replace it, one
 * conflict at a time, with the evidence each proposal carries.
 */
enum class Branch {
    DEFER_TO_PERSONALITY,
    OUT_OF_RANGE_ELEMENT,
    UNBACKED_EXPLANATION,
    UNBACKED_CONCEPT,
    COUNT_OF_ALL_ELEMENTS,
    CALCULATION,
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
    NO_MATCH
}

/** A plan one branch proposes for a query, with the evidence it had when it proposed. */
data class Proposal(val branch: Branch, val plan: QueryPlan)

/** Chooses among the planner's proposals (roadmap 1.8). */
object Arbiter {

    /**
     * The proposal to answer with: the branch earliest in [Branch], and within one branch the
     * proposal made first - a branch that tries the explicit "X is denser than Y" before its looser
     * reading proposes both, and the explicit one is the one it meant.
     */
    fun pick(proposals: List<Proposal>): Proposal? = proposals.minByOrNull { it.branch.ordinal }
}
