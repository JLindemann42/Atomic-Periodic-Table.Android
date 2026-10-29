package com.jlindemann.science.study

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * One card's schedule (roadmap 2.6), as it crosses the wire in `study.cards`.
 *
 * @property stability days until recall falls to 90%
 * @property difficulty 1 to 10
 * @property due when the card is next due, epoch ms
 * @property last when it was last reviewed, epoch ms
 */
data class CardState(
    val stability: Double,
    val difficulty: Double,
    val due: Long,
    val last: Long,
    val reps: Int,
    val lapses: Int
)

/**
 * FSRS-5 with its published default parameters, rated Again or Good, since a quiz answer is only
 * right or wrong. Specified in the shared data's `sync/study.md`; `tools/fsrs_golden.py` is the
 * reference, and `golden/fsrs.tsv` pins this and the Windows `Fsrs.cs` to it.
 */
object Fsrs {

    const val AGAIN = 1
    const val GOOD = 3

    private val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val RETENTION = 0.9
    private const val MIN_STABILITY = 0.01
    private const val MAX_INTERVAL = 36_500
    const val DAY_MS = 86_400_000L

    /** The card after a review rated [rating] at [now], or a new card's first schedule. */
    fun review(card: CardState?, rating: Int, now: Long): CardState {
        val stability: Double
        val difficulty: Double
        if (card == null) {
            stability = initialStability(rating)
            difficulty = initialDifficulty(rating)
        } else {
            val elapsed = (now - card.last).toDouble() / DAY_MS
            stability = when {
                elapsed < 1 -> card.stability * exp(W[17] * (rating - 3 + W[18]))
                rating > 1 -> recallStability(card.difficulty, card.stability, retrievability(elapsed, card.stability), rating)
                else -> forgetStability(card.difficulty, card.stability, retrievability(elapsed, card.stability))
            }
            difficulty = nextDifficulty(card.difficulty, rating)
        }
        val s = max(stability, MIN_STABILITY)
        return CardState(
            stability = s,
            difficulty = difficulty,
            due = now + intervalDays(s) * DAY_MS,
            last = now,
            reps = (card?.reps ?: 0) + 1,
            lapses = (card?.lapses ?: 0) + if (rating == AGAIN) 1 else 0
        )
    }

    /** The scheduled interval for a stability, in whole days, rounded half up. */
    fun intervalDays(stability: Double): Long {
        val raw = stability / FACTOR * (RETENTION.pow(1 / DECAY) - 1)
        return min(max(floor(raw + 0.5), 1.0), MAX_INTERVAL.toDouble()).toLong()
    }

    private fun clampDifficulty(d: Double): Double = min(max(d, 1.0), 10.0)

    private fun initialStability(g: Int): Double = max(W[g - 1], MIN_STABILITY)

    private fun initialDifficulty(g: Int): Double = clampDifficulty(W[4] - exp(W[5] * (g - 1)) + 1)

    private fun retrievability(elapsedDays: Double, s: Double): Double = (1 + FACTOR * elapsedDays / s).pow(DECAY)

    private fun nextDifficulty(d: Double, g: Int): Double {
        val damped = d + (-W[6] * (g - 3)) * (10 - d) / 9
        return clampDifficulty(W[7] * initialDifficulty(4) + (1 - W[7]) * damped)
    }

    private fun recallStability(d: Double, s: Double, r: Double, g: Int): Double {
        val hard = if (g == 2) W[15] else 1.0
        val easy = if (g == 4) W[16] else 1.0
        return s * (exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp(W[10] * (1 - r)) - 1) * hard * easy + 1)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double =
        min(W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r)), s)
}
