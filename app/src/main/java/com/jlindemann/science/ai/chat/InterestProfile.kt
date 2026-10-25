package com.jlindemann.science.ai.chat

/**
 * Which elements this user keeps asking about, so the assistant can greet them by name.
 *
 * Counts only. No question text and no answer is kept — the promise the old `AILearningManager`
 * made ("Does NOT store user-provided facts"), kept here structurally by having nowhere to put one.
 *
 * Deliberately smaller than `AILearningManager`. That class also kept a property-interest map and a
 * "technical score", but `trackPropertyInterest` and `incrementSession` were never called, so the
 * score sat at 0.5 forever and `ai_personalized_greeting_interest` was unreachable. Only the element
 * half ever ran, so only the element half is here.
 */
class InterestProfile {

    private val counts = HashMap<String, Int>()

    /** How many times each element key has been asked about. */
    val elements: Map<String, Int> get() = counts

    /** Count one question about an element. */
    fun track(elementKey: String?) {
        if (elementKey.isNullOrBlank()) return
        val key = elementKey.trim().lowercase()
        counts[key] = (counts[key] ?: 0) + 1
    }

    /**
     * The most-asked-about element, or null if none yet. Ties break on the key, so the greeting
     * does not change between launches for someone who asked about two elements equally often.
     */
    val favourite: String?
        get() = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key

    /** Restore a saved profile. */
    fun load(saved: Map<String, Int>?) {
        counts.clear()
        saved?.forEach { (key, count) ->
            if (key.isNotBlank() && count > 0) counts[key.lowercase()] = count
        }
    }

    /** Forget everything. */
    fun clear() = counts.clear()
}
