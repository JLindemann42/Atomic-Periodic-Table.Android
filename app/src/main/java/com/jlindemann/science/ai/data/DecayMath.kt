package com.jlindemann.science.ai.data

import kotlin.math.ln
import kotlin.math.pow

/**
 * Exponential decay over a known half-life.
 *
 * Half-lives arrive from [IsotopeParser], which has already reconciled the forty-odd unit
 * spellings, scientific notations and corruptions the `iso_half_N` data is authored in. Nothing
 * here re-reads that field: a second parser would be a second set of edge cases to keep in step,
 * and the two would drift.
 */
object DecayMath {

    /**
     * Past this many half-lives, less than one part in 10^18 is left.
     *
     * Reported as "effectively none" rather than as a denormal, because printing 3.2e-25 g implies
     * a measurement nobody could make and no data in this app supports.
     */
    const val EFFECTIVELY_GONE = 60.0

    /** How many half-lives fit in an interval. Null when the half-life is not positive. */
    fun halfLives(elapsedSeconds: Double, halfLifeSeconds: Double): Double? {
        if (halfLifeSeconds <= 0.0 || !halfLifeSeconds.isFinite()) return null
        if (elapsedSeconds < 0.0 || !elapsedSeconds.isFinite()) return null
        return elapsedSeconds / halfLifeSeconds
    }

    /** The fraction of a sample left after [halfLives] half-lives: 2^-n. */
    fun fractionRemaining(halfLives: Double): Double = 2.0.pow(-halfLives)

    /** How much of [initial] is left after [elapsedSeconds]. */
    fun remaining(initial: Double, elapsedSeconds: Double, halfLifeSeconds: Double): Double? {
        val n = halfLives(elapsedSeconds, halfLifeSeconds) ?: return null
        return initial * fractionRemaining(n)
    }

    /**
     * How long it takes [initial] to fall to [remaining].
     *
     * Null when either amount is not positive, or when [remaining] exceeds [initial]. Asking how
     * long until a sample grows has no answer, and the logarithm would happily supply a negative
     * time for it.
     */
    fun elapsedFor(initial: Double, remaining: Double, halfLifeSeconds: Double): Double? {
        if (halfLifeSeconds <= 0.0 || !halfLifeSeconds.isFinite()) return null
        if (initial <= 0.0 || remaining <= 0.0) return null
        if (remaining > initial) return null
        return halfLifeSeconds * ln(initial / remaining) / ln(2.0)
    }

    /** The decay constant λ = ln 2 / t½, so the answer can show its working. */
    fun decayConstant(halfLifeSeconds: Double): Double? =
        if (halfLifeSeconds > 0.0 && halfLifeSeconds.isFinite()) ln(2.0) / halfLifeSeconds else null
}
