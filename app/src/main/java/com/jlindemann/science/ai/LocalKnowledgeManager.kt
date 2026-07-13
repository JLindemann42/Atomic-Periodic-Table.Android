package com.jlindemann.science.ai

import android.content.Context
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

class LocalKnowledgeManager(private val context: Context) {

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

        return resolveEquation(normalized)
            ?: resolveConstant(normalized)
            ?: resolveIndicator(normalized)
            ?: resolvePoisson(normalized)
            ?: resolveGeology(normalized)
            ?: resolveIon(normalized)
            ?: resolveDictionary(normalized, activeLanguage)
    }

    private fun resolveEquation(query: String): QueryResult? {
        if (!containsAny(query, listOf("equation", "formula", "law", "formel", "ekvation"))) return null

        val match = equations.maxByOrNull { scoreMatch(query, normalize(it.equationTitle)) } ?: return null
        val score = scoreMatch(query, normalize(match.equationTitle))
        if (score <= 0) return null

        val description = match.description.ifBlank { "The app includes this equation as a visual reference." }
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
            containsAny(query, listOf("hardness", "mohs")) ->
                "${match.name}: hardness ${match.hardness}."
            containsAny(query, listOf("density")) ->
                "${match.name}: density ${match.density}."
            containsAny(query, listOf("color", "colour")) ->
                "${match.name}: color ${match.color}, streak ${match.streak}."
            containsAny(query, listOf("magnetic", "magnetism")) ->
                "${match.name}: ${match.magnetism}."
            else ->
                "${match.name} is a ${match.type.lowercase(Locale.ROOT)} in the ${match.group} group. " +
                    "Color: ${match.color}. Hardness: ${match.hardness}. Density: ${match.density}. Magnetism: ${match.magnetism}."
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
            response = "${match.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} " +
                "has ${match.count} ionization energy value(s) available in the app.",
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
            response = "$displayName: acid below pH ${match.acid} is ${match.acidColor}, " +
                "neutral range ${match.neutral} is ${match.neutralColor}, " +
                "and above pH ${match.alkali} it is ${match.alkaliColor}.",
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
            response = "${match.name}: Poisson's ratio range ${range} (${match.type}).",
            topic = "poisson ratio",
            isTechnical = true
        )
    }

    private fun containsAny(query: String, needles: List<String>): Boolean {
        return needles.any { query.contains(normalize(it)) }
    }

    private fun scoreMatch(query: String, candidate: String): Int {
        if (candidate.isBlank()) return 0
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
