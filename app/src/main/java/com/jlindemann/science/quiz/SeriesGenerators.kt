package com.jlindemann.science.quiz

import com.jlindemann.science.R
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.SeriesId

/**
 * Elements grouped by the *text* of their series label, with UNKNOWN dropped.
 *
 * Grouping by label rather than by [SeriesId] is what makes both generators below correct:
 * transition and post-transition metals share one label, as do the two nonmetal series, so an
 * "odd one out" drawn across that boundary would not actually be odd. UNKNOWN is excluded because
 * it also resolves to "Nonmetal", which would present an unclassified element as a nonmetal.
 */
private fun elementsByLabel(ctx: GeneratorContext): Map<String, List<ElementRecord>> =
    ctx.store.elements
        .filter { it.series != SeriesId.UNKNOWN }
        .groupBy { ctx.seriesLabel(it.series) }

/**
 * "What group does gold belong to?"
 *
 * Replaces the raw-JSON version of this game. `element_group` is authored inconsistently —
 * "Other Nonmetal" vs "Other Nonmetals", "Post-transition Metals" vs "Post-Transition Metals",
 * "Alkaline earth metal" — and the old generator offered those spellings as separate options,
 * so a question could carry two answers that meant the same thing. Reading the canonical
 * [SeriesId] instead removes the whole class of defect.
 */
class ClassificationGenerator(
    override val categoryKey: String,
    override val baseXp: Int
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val byLabel = elementsByLabel(ctx)
        if (byLabel.size < 2) return emptyList()
        val labels = byLabel.keys.toList()
        val pool = byLabel.values.flatten()

        val questions = ArrayList<QuizQuestion>(count)
        val used = HashSet<String>()
        var attempts = count * 8

        while (questions.size < count && attempts-- > 0) {
            val element = pool.random(ctx.random)
            if (!used.add(element.key)) continue

            val correct = ctx.seriesLabel(element.series)
            val wrongs = labels.filter { it != correct }.shuffled(ctx.random).take(3)
            if (wrongs.isEmpty()) continue

            questions.add(
                QuizQuestion(
                    question = ctx.string(R.string.question_element_group, ctx.name(element)),
                    correctAnswer = correct,
                    alternatives = (wrongs + correct).shuffled(ctx.random),
                    baseXp = baseXp
                )
            )
        }
        return questions
    }
}

/** "Which of these is NOT a noble gas?" — three from one family, one from another. */
class OddOneOutGenerator(
    override val categoryKey: String,
    override val baseXp: Int,
    private val optionCount: Int = 4
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val byLabel = elementsByLabel(ctx)
        val families = byLabel.filterValues { it.size >= optionCount - 1 }
        if (families.size < 2) return emptyList()
        val outsiders = byLabel.keys.associateWith { label ->
            byLabel.filterKeys { it != label }.values.flatten()
        }

        val questions = ArrayList<QuizQuestion>(count)
        val asked = HashSet<String>()
        var attempts = count * 12

        while (questions.size < count && attempts-- > 0) {
            val label = families.keys.random(ctx.random)
            val pool = outsiders[label].orEmpty()
            if (pool.isEmpty()) continue

            val insiders = families.getValue(label).shuffled(ctx.random).take(optionCount - 1)
            val odd = pool.random(ctx.random)

            val options = (insiders + odd).map { ctx.name(it) }
            if (options.distinct().size != options.size) continue

            val text = ctx.string(R.string.question_not_a_series, label)
            if (!asked.add(text + "|" + options.sorted().joinToString("|"))) continue

            questions.add(
                QuizQuestion(text, ctx.name(odd), options.shuffled(ctx.random), baseXp)
            )
        }
        return questions
    }
}
