package com.jlindemann.science.ai

import android.content.Context
import com.jlindemann.science.R
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

    private fun resolveAppFeature(query: String): QueryResult? {
        val features = listOf(
            listOf("nuclide", "table of nuclides", "nuklid", "kärnavfall", "isotoptabell") to "The app includes a comprehensive **Table of Nuclides** (Nuclide Table) showing isotopes for all elements, color-coded by their decay types (alpha, beta, etc.).",
            listOf("emission", "spectrum", "spectral", "lines", "spektrum", "emissionsspektrum") to "You can view the **Emission Spectrum** (spectral lines) for elements in their detailed property panels. It shows the unique light pattern each element produces.",
            listOf("flashcard", "game", "learn", "quiz", "test", "lärspel", "öva") to "The app features several **Flashcard mini-games** to help you master element symbols, atomic masses, classifications, and more. You can track your level and XP!",
            listOf("achievement", "progress", "stat", "prestation", "framsteg", "statistik") to "Track your chemistry knowledge with **Achievements** and usage statistics, which can be found on the User Page.",
            listOf("molar mass", "calculator", "formula", "molmassa", "beräkna", "kalkylator") to "There is a built-in **Molar Mass Calculator** tool that lets you calculate the molecular weight of complex chemical compounds.",
            listOf("unit", "converter", "temperature", "kelvin", "celsius", "enhetsomvandlare") to "The app includes a versatile **Unit Converter** for converting temperatures and other scientific units used in chemistry.",
            listOf("ideal gas", "pv=nrt", "gas law", "ideala gaslagen") to "Use the **Ideal Gas Calculator** to calculate pressure, volume, moles, or temperature for any ideal gas using the PV=nRT equation.",
            listOf("reaction", "balancer", "equation", "favorit", "reaktionsbalanserare") to "The **Chemical Reaction Balancer** tool helps you balance complex chemical equations and save your favorite reactions for later study.",
            listOf("how to use", "what can you do", "features", "vad kan du göra", "hjälp", "funktioner") to "I can help you explore elements, compare properties, calculate molar masses, explain chemical concepts, and guide you through the app's tables and tools! Just ask about an element or a chemistry term."
        )

        for ((keywords, desc) in features) {
            if (keywords.any { query.contains(it) }) {
                return QueryResult(response = desc, topic = "app_feature", isTechnical = false)
            }
        }
        return null
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
        
        // Use word boundaries for very short candidates to avoid matching "me" (electron mass) in "mer"
        if (candidate.length <= 2) {
            val regex = "\\b${Regex.escape(candidate)}\\b".toRegex()
            return if (regex.containsMatchIn(query)) 100 else 0
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
