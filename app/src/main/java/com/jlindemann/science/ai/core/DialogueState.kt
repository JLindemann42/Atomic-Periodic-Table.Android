package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.ElementKey

/**
 * What the agent remembers across turns.
 *
 * The important mechanism here is **slot-emptiness inheritance**, not a pronoun list. A follow-up
 * like "and its density?" arrives with a field but no element, so the element is inherited from
 * [focusElement]; "what about iron?" arrives with an element but no field, so the field is
 * inherited from [lastFieldIds]. That single rule handles follow-ups in all twelve languages with
 * no per-language data at all — "och dess densitet?", "und seine Dichte?", "它的密度呢",
 * "اور اس کی کثافت؟" all work for free.
 */
class DialogueState {

    /** The element the conversation is currently about. */
    var focusElement: ElementKey? = null

    /** Recently discussed elements, newest first, for "the previous one" style references. */
    val recentElements: ArrayDeque<ElementKey> = ArrayDeque()

    /** Fields asked about on the previous turn, inherited by a bare element follow-up. */
    var lastFieldIds: List<String> = emptyList()

    /** The previous plan, cloned when a continuation supplies only one new slot. */
    var lastPlan: QueryPlan? = null

    /** Elements the previous answer listed, for "and the second one?". */
    var lastResultKeys: List<ElementKey> = emptyList()

    /** The unit the user last asked for, so "and iron?" keeps the same scale. */
    var lastTargetUnit: String? = null

    var activeLanguage: String = "en"

    /** Record what an answer covered so the next turn can refer back to it. */
    fun noteAnswer(plan: QueryPlan, resultKeys: List<ElementKey>) {
        lastPlan = plan
        if (plan.fieldIds.isNotEmpty()) lastFieldIds = plan.fieldIds
        if (plan.targetUnit != null) lastTargetUnit = plan.targetUnit
        if (resultKeys.isNotEmpty()) {
            lastResultKeys = resultKeys
            focusElement = resultKeys.first()
            for (key in resultKeys.asReversed()) {
                recentElements.remove(key)
                recentElements.addFirst(key)
            }
            while (recentElements.size > MAX_RECENT) recentElements.removeLast()
        }
    }

    fun clear() {
        focusElement = null
        recentElements.clear()
        lastFieldIds = emptyList()
        lastPlan = null
        lastResultKeys = emptyList()
        lastTargetUnit = null
    }

    private companion object {
        const val MAX_RECENT = 4
    }
}
