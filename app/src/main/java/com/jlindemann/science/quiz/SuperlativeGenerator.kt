package com.jlindemann.science.quiz

import androidx.annotation.StringRes
import kotlin.math.abs
import kotlin.math.max

/**
 * A numeric field a superlative question can be asked about, with its own authored wording.
 *
 * The wording is per field rather than a generic frame with the field name substituted in.
 * `FieldSpec.labelRes` values are table headings — several end in a colon and carry units — and
 * dropping one into "Which of these has the highest %s?" reads badly in German compounds and in
 * Hindi/Urdu word order.
 */
data class RankableField(
    val fieldId: String,
    @StringRes val highestRes: Int,
    @StringRes val lowestRes: Int
)

/**
 * "Which of these has the highest density?"
 *
 * Asks which of the *drawn* elements is the extreme, not which element is the extreme overall.
 * The latter has exactly one answer per field, so a player memorises the whole category in a
 * sitting; drawing a fresh set each time keeps the pool effectively unlimited.
 *
 * Also serves the two-option pairwise game — the answer grid already hides cards beyond
 * `alternatives.size`, so the only difference is [optionCount].
 */
class SuperlativeGenerator(
    override val categoryKey: String,
    override val baseXp: Int,
    private val fields: List<RankableField>,
    /** true for always "highest", false for always "lowest", null to pick per question. */
    private val descending: Boolean?,
    private val optionCount: Int = 4
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val questions = ArrayList<QuizQuestion>(count)
        val asked = HashSet<String>()
        val margin = separationMargin(ctx.difficulty)
        var attempts = count * 12

        while (questions.size < count && attempts-- > 0) {
            val field = fields.random(ctx.random)
            val pool = ctx.rankableFor(field.fieldId)
            if (pool.size < optionCount) continue

            val draw = pool.shuffled(ctx.random).take(optionCount)
            val wantHighest = descending ?: ctx.random.nextBoolean()
            val ordered = draw.sortedByDescending { it.second.mid }
                .let { if (wantHighest) it else it.reversed() }

            // A near-tie is not a fair question: nobody can be expected to separate 7.85 from 7.87.
            if (!clearlyApart(ordered[0].second.mid, ordered[1].second.mid, margin)) continue

            val options = draw.map { ctx.name(it.first) }
            // Two cards showing the same text would make one of them wrongly count as incorrect.
            if (options.distinct().size != options.size) continue

            val text = ctx.string(if (wantHighest) field.highestRes else field.lowestRes)
            if (!asked.add(text + "|" + options.sorted().joinToString("|"))) continue

            questions.add(
                QuizQuestion(
                    question = text,
                    correctAnswer = ctx.name(ordered[0].first),
                    alternatives = options.shuffled(ctx.random),
                    baseXp = baseXp
                )
            )
        }
        return questions
    }

    private fun clearlyApart(winner: Double, runnerUp: Double, margin: Double): Boolean {
        val scale = max(abs(winner), abs(runnerUp))
        if (scale == 0.0) return false
        return abs(winner - runnerUp) / scale >= margin
    }

    /** Harder difficulties allow the top two to sit closer together. */
    private fun separationMargin(difficulty: String): Double = when (difficulty) {
        "hard" -> 0.05
        "medium" -> 0.12
        else -> 0.25
    }
}
