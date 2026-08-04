package com.jlindemann.science.ai

/**
 * What happened during one turn of the assistant, recorded for analytics.
 *
 * Deliberately plain Kotlin with no Android or Firebase types. The agent runs on
 * `Dispatchers.Default` deep inside a package that is covered by JVM unit tests; it records this
 * and [com.jlindemann.science.utils.AiChatPanelController] does the logging, so measuring the AI
 * cannot break the AI's test suite.
 *
 * Carries no user text. A question is free-form input that may contain anything, so only its shape
 * travels: how long it was, and whether an element came out of it.
 */
data class AiTurnStats(
    /** Which part of the pipeline produced the answer. One of [Path]. */
    val path: String,
    /** The structured engine's [com.jlindemann.science.ai.core.Intent], when it claimed the query. */
    val intent: String? = null,
    /** The plan's confidence, when the engine claimed the query. */
    val confidence: Double? = null,
    val language: String,
    /** True when the query was in a different language than the conversation and forced a switch. */
    val languageSwitched: Boolean = false,
    val elementResolved: Boolean = false,
    val hasCard: Boolean = false,
    val cardKind: String? = null,
    val actionCount: Int = 0,
    val queryChars: Int = 0,
    val queryWords: Int = 0,
    /** Set when the structured engine threw and the legacy handlers took over. */
    val engineError: String? = null,
    /** Set only on [Path.RATE_LIMITED]. */
    val dailyLimit: Int? = null
) {
    /**
     * Coarse buckets rather than one label per handler.
     *
     * The question worth answering is whether the structured engine is claiming queries or the
     * older keyword chain is still carrying them; which of the two dozen keyword branches fired is
     * a level of detail that would cost a label at every branch and answer nothing on its own.
     */
    object Path {
        /** The structured engine — planner, executor, composer. */
        const val ENGINE = "engine"

        /** "element 79" and similar, resolved straight off the atomic number. */
        const val ATOMIC_NUMBER = "atomic_number"

        /** One of the keyword handlers the engine declined to claim. */
        const val LEGACY = "legacy"

        /** The in-app tables: constants, dictionary, ions, equations. */
        const val LOCAL_KNOWLEDGE = "local_knowledge"

        /** BM25 retrieval over the app's own documents. */
        const val RETRIEVAL = "retrieval"

        /** Nothing claimed it; a generic element answer or a no-data reply. */
        const val FALLBACK = "fallback"

        /** An answer to an outstanding quiz question. */
        const val QUIZ = "quiz"

        /** The daily message quota was already spent, so nothing was generated. */
        const val RATE_LIMITED = "rate_limited"

        /** The agent lost its context and could not answer at all. */
        const val CONTEXT_LOST = "context_lost"
    }

    /**
     * Confidence as a dimension rather than a raw number, so it groups in the console.
     */
    fun confidenceBucket(): String? = confidence?.let {
        when {
            it < 0.5 -> "lt_50"
            it < 0.7 -> "50_70"
            it < 0.85 -> "70_85"
            else -> "gte_85"
        }
    }
}
