package com.jlindemann.science.ai

import android.content.Context
import com.jlindemann.science.R
import com.jlindemann.science.ai.nlu.Lexicon
import com.jlindemann.science.ai.retrieval.TextMatching
import com.jlindemann.science.model.Constants
import com.jlindemann.science.model.ConstantsModel
import com.jlindemann.science.model.Dictionary
import com.jlindemann.science.model.DictionaryModel
import com.jlindemann.science.model.Equation
import com.jlindemann.science.model.EquationModel
import com.jlindemann.science.model.Geology
import com.jlindemann.science.model.GeologyModel
import com.jlindemann.science.model.Indicator
import com.jlindemann.science.model.IndicatorModel
import com.jlindemann.science.model.Ion
import com.jlindemann.science.model.IonModel
import com.jlindemann.science.model.Poisson
import com.jlindemann.science.model.PoissonModel
import java.util.Locale

class LocalKnowledgeManager(private val context: Context?) {

    data class QueryResult(
        val response: String,
        val topic: String,
        val isTechnical: Boolean = false
    )

    private val constants by lazy {
        ArrayList<Constants>().also { ConstantsModel.getList(it) }
    }

    private val dictionary by lazy {
        ArrayList<Dictionary>().also { DictionaryModel.getList(it) }
    }

    private val geology by lazy {
        ArrayList<Geology>().also { GeologyModel.getList(it) }
    }

    private val ions by lazy {
        ArrayList<Ion>().also { IonModel.getList(it) }
    }

    private val equations by lazy {
        ArrayList<Equation>().also { EquationModel.getList(it) }
    }

    private val indicators by lazy {
        ArrayList<Indicator>().also { IndicatorModel.getList(it) }
    }

    private val poissonValues by lazy {
        ArrayList<Poisson>().also { PoissonModel.getList(it) }
    }

    fun resolve(query: String, activeLanguage: String): QueryResult? {
        val normalized = normalize(query)
        if (normalized.isBlank()) return null

        return resolveAppFeature(normalized)
            ?: resolveEquation(normalized)
            ?: resolveConstant(normalized)
            ?: resolveIndicator(normalized)
            ?: resolvePoisson(normalized)
            ?: resolveGeology(normalized)
            ?: resolveIon(normalized)
            ?: resolveDictionary(normalized, activeLanguage)
    }

    /**
     * Which in-app tool a question is about.
     *
     * Vocabulary comes from [Lexicon.APP_FEATURE_WORDS] and the answers from `strings.xml`, so all
     * twelve shipped languages reach these. Previously both were written inline here: the answers
     * as English literals and the keywords in English and Swedish only, which left ten languages
     * unable to ask the question and every language reading the answer in English.
     *
     * Matched on word boundaries rather than by `contains`. A bare substring search found "unit"
     * inside "opportunity" and "reaction" inside "interaction", and answered a chemistry question
     * with a tour of the app.
     */
    private fun resolveAppFeature(query: String): QueryResult? {
        for ((feature, keywords) in Lexicon.APP_FEATURE_WORDS) {
            if (keywords.none { TextMatching.containsWord(query, it) }) continue
            val response = featureAnswer(feature) ?: continue
            return QueryResult(response = response, topic = "app_feature", isTechnical = false)
        }
        return null
    }

    private fun featureAnswer(feature: String): String? {
        val id = FEATURE_STRINGS[feature] ?: return null
        return context?.getString(id) ?: FEATURE_FALLBACK[feature]
    }

    private companion object {
        val FEATURE_STRINGS: Map<String, Int> = mapOf(
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
         * Used only when there is no Context at all, which is the shape the unit tests construct.
         * Deliberately terse: the real answers live in `strings.xml` and are translated there.
         */
        val FEATURE_FALLBACK: Map<String, String> = mapOf(
            "nuclide_table" to "The app includes a Table of Nuclides showing isotopes for all elements.",
            "emission_spectrum" to "You can view an element's emission spectrum in its detail panel.",
            "flashcards" to "The app has flashcard mini-games for symbols, masses and classifications.",
            "achievements" to "Track your progress with achievements on the user page.",
            "molar_mass" to "There is a built-in molar mass calculator for chemical compounds.",
            "unit_converter" to "The app includes a unit converter for temperatures and other units.",
            "ideal_gas" to "Use the ideal gas calculator to work with PV = nRT.",
            "reaction_balancer" to "The chemical reaction balancer helps you balance equations.",
            "capabilities" to "I can look up element properties, balance equations, calculate molar masses and convert units."
        )
    }

    private fun resolveEquation(query: String): QueryResult? {
        if (!containsAny(query, listOf("equation", "formula", "law", "formel", "ekvation"))) return null

        val match = equations.maxByOrNull { scoreMatch(query, normalize(it.equationTitle)) } ?: return null
        val score = scoreMatch(query, normalize(match.equationTitle))
        if (score <= 0) return null

        val description = match.description.ifBlank { context?.getString(R.string.ai_eqn_no_desc) ?: "No description available" }
        return QueryResult(
            response = "${match.equationTitle} (${match.category}):\n$description",
            topic = "equations",
            isTechnical = true
        )
    }

    private fun resolveConstant(query: String): QueryResult? {
        val match = constants.maxByOrNull {
            maxOf(
                scoreMatch(query, normalize(it.name)),
                scoreMatch(query, normalize(it.info)),
                scoreMatch(query, normalize(it.category))
            )
        } ?: return null

        val score = maxOf(
            scoreMatch(query, normalize(match.name)),
            scoreMatch(query, normalize(match.info))
        )
        if (score <= 0) return null

        val unitSuffix = if (match.unit == "-" || match.unit.isBlank()) "" else " ${match.unit}"
        return QueryResult(
            response = "${match.name}: ${match.value}$unitSuffix\n${match.info}",
            topic = "constants",
            isTechnical = true
        )
    }

    private fun resolveDictionary(query: String, activeLanguage: String): QueryResult? {
        val definitionIntent = containsAny(
            query,
            listOf(
                "what is", "what's", "tell me about", "define", "explain", "meaning of",
                "vad ar", "beratta om", "was ist", "qu est ce", "que es"
            )
        )

        val match = dictionary.maxByOrNull { scoreMatch(query, normalize(it.heading)) } ?: return null
        val score = scoreMatch(query, normalize(match.heading))
        if (score <= 0) return null
        if (!definitionIntent && query.length < 4) return null

        return QueryResult(
            response = "${match.heading}: ${match.text}",
            topic = "dictionary",
            isTechnical = activeLanguage == "en"
        )
    }

    private fun resolveGeology(query: String): QueryResult? {
        val match = geology.maxByOrNull { scoreMatch(query, normalize(it.name)) } ?: return null
        val score = scoreMatch(query, normalize(match.name))
        if (score <= 0) return null

        val response = when {
            containsAny(query, listOf("hardness", "mohs")) -> {
                val label = context?.getString(R.string.hardness_label)?.replace(":","")?.trim() ?: "Hardness"
                context?.getString(R.string.ai_geo_hard, match.name, label, match.hardness) ?: "${match.name}: ${match.hardness} (Mohs)"
            }
            containsAny(query, listOf("density")) -> {
                val label = context?.getString(R.string.density_label)?.replace(":","")?.trim() ?: "Density"
                context?.getString(R.string.ai_geo_dens, match.name, label, match.density) ?: "${match.name}: ${match.density} g/cm³"
            }
            containsAny(query, listOf("color", "colour")) -> {
                val cl = context?.getString(R.string.color_label)?.replace(":","")?.trim() ?: "Color"
                val sl = context?.getString(R.string.streak_label)?.replace(":","")?.trim() ?: "Streak"
                context?.getString(R.string.ai_geo_color, match.name, cl, match.color, sl, match.streak) ?: "${match.name}: ${match.color}."
            }
            containsAny(query, listOf("magnetic", "magnetism")) ->
                "${match.name}: ${match.magnetism}."
            else ->
                context?.getString(R.string.ai_geo_info, match.name, match.type, match.group, match.color, match.hardness, match.density, match.magnetism) ?: "${match.name} info."
        }

        return QueryResult(response = response, topic = "geology", isTechnical = false)
    }

    private fun resolveIon(query: String): QueryResult? {
        if (!containsAny(query, listOf("ion", "ionization", "ionisation"))) return null

        val match = ions.maxByOrNull {
            maxOf(scoreMatch(query, normalize(it.name)), scoreMatch(query, normalize(it.short)))
        } ?: return null
        val score = maxOf(scoreMatch(query, normalize(match.name)), scoreMatch(query, normalize(match.short)))
        if (score <= 0) return null

        return QueryResult(
            response = context?.getString(R.string.ai_ion_count, match.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, match.count) ?: "${match.name}: ${match.count}",
            topic = "ionization",
            isTechnical = true
        )
    }

    private fun resolveIndicator(query: String): QueryResult? {
        val indicatorIntent = containsAny(query, listOf("ph", "indicator", "acid", "alkali", "base"))
        val match = indicators.maxByOrNull { scoreMatch(query, normalize(it.name.replace('_', ' '))) } ?: return null
        val score = scoreMatch(query, normalize(match.name.replace('_', ' ')))
        if (score <= 0 || !indicatorIntent) return null

        val displayName = match.name.replace('_', ' ')
        return QueryResult(
            response = context?.getString(R.string.ai_ph_info, displayName, match.acid, match.acidColor, match.neutral, match.neutralColor, match.alkali, match.alkaliColor) ?: "$displayName indicator info.",
            topic = "ph indicator",
            isTechnical = false
        )
    }

    private fun resolvePoisson(query: String): QueryResult? {
        if (!containsAny(query, listOf("poisson"))) return null

        val match = poissonValues.maxByOrNull { scoreMatch(query, normalize(it.name)) } ?: return null
        val score = scoreMatch(query, normalize(match.name))
        if (score <= 0) return null

        val range = if (match.start == match.end) {
            match.start.toString()
        } else {
            "${match.start}-${match.end}"
        }

        return QueryResult(
            response = context?.getString(R.string.ai_poi_info, match.name, range, match.type) ?: "${match.name}: $range",
            topic = "poisson ratio",
            isTechnical = true
        )
    }

    private fun containsAny(query: String, needles: List<String>): Boolean {
        return needles.any { query.contains(normalize(it)) }
    }

    private fun scoreMatch(query: String, candidate: String): Int {
        if (candidate.isBlank()) return 0
        
        // Use word boundaries for very short candidates to avoid matching "me" (electron mass) in "mer".
        // The shared scanner is Unicode-aware; a regex `\b` is ASCII-only and would treat every
        // accented letter as a boundary.
        if (candidate.length <= 2) {
            return if (com.jlindemann.science.ai.retrieval.TextMatching.containsWord(query, candidate)) 100 else 0
        }

        return when {
            query == candidate -> 100
            query.contains(candidate) -> 80 + candidate.length
            candidate.contains(query) && query.length >= 4 -> 50 + query.length
            candidate.split(" ").all { token -> token.length > 2 && query.contains(token) } -> 40 + candidate.length
            else -> 0
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}0-9+._# -]+"), " ")
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
