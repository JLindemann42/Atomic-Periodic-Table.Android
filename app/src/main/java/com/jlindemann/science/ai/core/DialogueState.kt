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

    /**
     * A detached copy, for speculative planning.
     *
     * Answering a compound question means planning each clause in turn, with the second
     * inheriting from the first. If any clause fails the whole attempt is abandoned, so that
     * work has to happen somewhere the real conversation state cannot be corrupted.
     */
    fun copyOf(): DialogueState = DialogueState().also { copy ->
        copy.focusElement = focusElement
        copy.recentElements.addAll(recentElements)
        copy.lastFieldIds = lastFieldIds
        copy.lastPlan = lastPlan
        copy.lastResultKeys = lastResultKeys
        copy.lastTargetUnit = lastTargetUnit
        copy.activeLanguage = activeLanguage
    }

    /** Adopt another state's contents, once a speculative attempt has succeeded. */
    fun adopt(other: DialogueState) {
        focusElement = other.focusElement
        recentElements.clear()
        recentElements.addAll(other.recentElements)
        lastFieldIds = other.lastFieldIds
        lastPlan = other.lastPlan
        lastResultKeys = other.lastResultKeys
        lastTargetUnit = other.lastTargetUnit
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
