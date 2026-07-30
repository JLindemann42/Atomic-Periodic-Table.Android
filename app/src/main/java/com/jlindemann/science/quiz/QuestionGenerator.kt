package com.jlindemann.science.quiz

import androidx.annotation.StringRes
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedView
import com.jlindemann.science.ai.data.Quantity
import com.jlindemann.science.ai.data.SeriesId
import kotlin.random.Random

/**
 * One generated multiple-choice question.
 *
 * Deliberately mirrors `LearningGamesActivity.Question` instead of reusing it: generators are a
 * data concern and should not depend on an Activity. The activity maps between the two.
 */
data class QuizQuestion(
    val question: String,
    val correctAnswer: String,
    val alternatives: List<String>,
    val baseXp: Int
)

/**
 * Everything a generator needs, built once per game.
 *
 * [store] is the typed element index from the AI data layer. The flashcard screen's own
 * `ElementData` keeps every value as a raw String, so `"8.0 × 10^4"` and `"160-350 (MPa)"` are not
 * comparable there; questions that rank or compare are only possible on top of parsed [Quantity]s.
 */
class GeneratorContext(
    val store: KnowledgeStore,
    private val names: LocalizedView?,
    private val strings: StringProvider,
    val difficulty: String,
    val random: Random = Random.Default
) {

    private val rankableCache = HashMap<String, List<Pair<ElementRecord, Quantity>>>()

    /** The element's name in the user's language, falling back to the English key. */
    fun name(element: ElementRecord): String =
        names?.name(element.key)?.takeIf { it.isNotBlank() }
            ?: element.key.replaceFirstChar { it.uppercase() }

    fun string(@StringRes res: Int, vararg args: Any): String = strings.get(res, *args)

    /**
     * The localized name of an element series.
     *
     * Several [SeriesId]s deliberately share a label — transition and post-transition metals are
     * both "Transition Metal", and both nonmetal series plus UNKNOWN are all "Nonmetal". Group by
     * this resolved text rather than by the enum, or a question can end up with two right answers.
     */
    fun seriesLabel(series: SeriesId): String = strings.get(series.labelRes)

    /**
     * Elements carrying a comparable value for a field, memoised for the life of the game.
     *
     * Ranges are dropped rather than ranked by midpoint: "which has the higher hardness" is not a
     * fair question when one of the candidates is recorded as `160-350`.
     */
    fun rankableFor(fieldId: String): List<Pair<ElementRecord, Quantity>> =
        rankableCache.getOrPut(fieldId) {
            store.elements.mapNotNull { element ->
                element.quantity(fieldId)?.takeIf { !it.isRange }?.let { element to it }
            }
        }
}

/** Builds the questions for one flashcard category. */
interface QuestionGenerator {
    val categoryKey: String
    val baseXp: Int
    fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion>
}
