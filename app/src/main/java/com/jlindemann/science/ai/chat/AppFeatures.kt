package com.jlindemann.science.ai.chat

import androidx.annotation.StringRes
import com.jlindemann.science.R
import com.jlindemann.science.ai.nlu.Lexicon
import com.jlindemann.science.ai.retrieval.TextMatching

/**
 * Which of the app's own tools a question is about — "what can you do", "do you have flashcards",
 * "vad kan du gora".
 *
 * This is the one half of the old `LocalKnowledgeManager` that the engine does not already cover.
 * Its other seven resolvers — equations, constants, indicators, Poisson values, geology, ions and
 * the dictionary — read tables [com.jlindemann.science.ai.data.DatasetIndex] owns, and
 * `datasets.tsv` holds the engine to answering them. Questions about the app itself have no
 * dataset behind them, so this answers them.
 */
object AppFeatures {

    /**
     * The features in match order, because the first match wins and some questions match two:
     * "help me with the reaction balancer" matches `reaction_balancer` and the "help me" in
     * `capabilities`. `mapOf` keeps declaration order too, but the Windows port needs this list,
     * and keeping both in one written order is what makes the two answer alike.
     */
    val features: List<String> = listOf(
        "nuclide_table",
        "emission_spectrum",
        "flashcards",
        "achievements",
        "molar_mass",
        "unit_converter",
        "ideal_gas",
        "reaction_balancer",
        "capabilities"
    )

    private val ANSWERS: Map<String, Int> = mapOf(
        "nuclide_table" to R.string.ai_feature_nuclide_table,
        "emission_spectrum" to R.string.ai_feature_emission_spectrum,
        "flashcards" to R.string.ai_feature_flashcards,
        "achievements" to R.string.ai_feature_achievements,
        "molar_mass" to R.string.ai_feature_molar_mass,
        "unit_converter" to R.string.ai_feature_unit_converter,
        "ideal_gas" to R.string.ai_feature_ideal_gas,
        "reaction_balancer" to R.string.ai_feature_reaction_balancer,
        "capabilities" to R.string.ai_feature_capabilities
    )

    /**
     * The string answering [normalizedQuery], or null if the question is not about the app.
     *
     * @param normalizedQuery already through [TextMatching.normalizeForLookup]. The keywords are
     *   diacritic-folded ASCII — "vad kan du gora", not "göra" — so they only match folded text.
     *
     * Matched on word boundaries, not by `contains`: a bare substring search found "unit" inside
     * "opportunity" and "reaction" inside "interaction", and answered a chemistry question with a
     * tour of the app.
     */
    @StringRes
    fun resolve(normalizedQuery: String): Int? {
        if (normalizedQuery.isBlank()) return null
        for (feature in features) {
            val keywords = Lexicon.APP_FEATURE_WORDS[feature] ?: continue
            if (keywords.none { TextMatching.containsWord(normalizedQuery, it) }) continue
            return ANSWERS.getValue(feature)
        }
        return null
    }
}
