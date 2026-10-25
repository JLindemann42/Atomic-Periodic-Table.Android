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
    /** Set when answering threw, and the user was given the decline instead. */
    val engineError: String? = null,
    /** Set only on [Path.RATE_LIMITED]. */
    val dailyLimit: Int? = null,
    /**
     * On [Path.FALLBACK], why the engine declined — a [com.jlindemann.science.ai.core.DeclineReason]
     * in lower case. The question itself is never recorded (roadmap 0.7).
     */
    val declineReason: String? = null
) {
    /**
     * Coarse buckets rather than one label per handler, mirroring
     * [com.jlindemann.science.ai.chat.ReplySource]: whether the engine answered from data, the
     * personality handlers answered something the engine declines by design, or nothing could.
     *
     * The keyword router's paths (`legacy`, `local_knowledge`, `retrieval`, `atomic_number`, `quiz`)
     * went with it. Earlier events carrying them are from builds before the orchestrator.
     */
    object Path {
        /** The structured engine — planner, executor, composer — including "tell me more". */
        const val ENGINE = "engine"

        /**
         * A handler for what the engine declines by design: overviews, quizzes, reactions,
         * similarity, usage and etymology, greetings, facts and questions about the app.
         */
        const val PERSONALITY = "personality"

        /** Two elements ordered by the reactivity heuristic, which no stored field can do. */
        const val REACTIVITY = "reactivity"

        /** Nothing could be said honestly, so the user got the decline. */
        const val FALLBACK = "fallback"

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
