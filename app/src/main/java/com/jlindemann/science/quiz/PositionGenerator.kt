package com.jlindemann.science.quiz

import com.jlindemann.science.R
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldRegistry

/**
 * What is being asked about an element's place in the table.
 *
 * All three are derived from atomic number rather than read from the JSON — the element files
 * carry no period, group or valence keys at all.
 */
enum class PositionAspect(val questionRes: Int) {

    PERIOD(R.string.question_period_of) {
        override fun valueOf(element: ElementRecord): Int? = element.period
    },

    GROUP(R.string.question_group_of) {
        // Null across the f-block, where the concept does not apply.
        override fun valueOf(element: ElementRecord): Int? = element.groupNumber
    },

    VALENCE(R.string.question_valence_electrons) {
        // Null across the d- and f-blocks, where inner shells are still filling.
        override fun valueOf(element: ElementRecord): Int? =
            FieldRegistry.valenceElectrons(element.atomicNumber)
    };

    abstract fun valueOf(element: ElementRecord): Int?
}

/** "Which period is titanium in?" — small integer answers, so no per-field wording is needed. */
class PositionGenerator(
    override val categoryKey: String,
    override val baseXp: Int,
    private val aspect: PositionAspect
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val pool = ctx.store.elements.mapNotNull { e -> aspect.valueOf(e)?.let { e to it } }
        val values = pool.map { it.second }.distinct().sorted()
        if (pool.isEmpty() || values.size < 2) return emptyList()

        val questions = ArrayList<QuizQuestion>(count)
        val used = HashSet<String>()
        var attempts = count * 8

        while (questions.size < count && attempts-- > 0) {
            val (element, correct) = pool.random(ctx.random)
            if (!used.add(element.key)) continue

            // Near misses make a better question than three values from the far end of the table.
            val wrongs = values.filter { it != correct }
                .sortedBy { kotlin.math.abs(it - correct) }
                .take(6)
                .shuffled(ctx.random)
                .take(3)
            if (wrongs.isEmpty()) continue

            val options = (wrongs + correct).map { it.toString() }
            questions.add(
                QuizQuestion(
                    question = ctx.string(aspect.questionRes, ctx.name(element)),
                    correctAnswer = correct.toString(),
                    alternatives = options.shuffled(ctx.random),
                    baseXp = baseXp
                )
            )
        }
        return questions
    }
}
