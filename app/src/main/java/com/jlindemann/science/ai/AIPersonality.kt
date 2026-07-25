package com.jlindemann.science.ai

import android.content.Context
import android.content.res.Configuration
import com.jlindemann.science.R
import java.util.Locale

/** Response templates and personality strings for the AI agent. All text is localized. */
object AIPersonality {

    fun getLocalizedContext(context: Context, language: String): Context {
        val localeTag = toBcp47Tag(language)
        val locale = Locale.forLanguageTag(localeTag).let { parsed ->
            if (parsed.language.isNullOrBlank()) {
                val base = language.substringBefore("-").substringBefore("_")
                Locale(base)
            } else parsed
        }
        // Deliberately no Locale.setDefault here. The chat language is auto-detected per message,
        // and setting the process default would leak it into every unrelated formatter in the
        // app — a Swedish-detected turn would render numbers as "3,14" app-wide. The
        // configuration context below is what actually resolves the localized strings, and from
        // API 24 Resources.getString formats using that config's locale.
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun toBcp47Tag(language: String): String {
        val trimmed = language.trim()
        if (trimmed.isEmpty()) return "en"
        val normalized = trimmed.replace('_', '-')
        val legacyRegion = Regex("^([a-zA-Z]{2,3})-r([a-zA-Z]{2})$")
        val match = legacyRegion.matchEntire(normalized)
        return if (match != null) "${match.groupValues[1]}-${match.groupValues[2]}" else normalized
    }

    fun getGreeting(context: Context, language: String): String {
        val localizedCtx = if (context.resources.configuration.locales[0].language == resolveBaseLanguage(language)) context 
                           else getLocalizedContext(context, language)
        return try {
            localizedCtx.getString(R.string.ai_greeting)
        } catch (e: Exception) {
            "Hey there! 👋 I'm your chemistry buddy. Ask me anything about elements!"
        }
    }

    private fun resolveBaseLanguage(language: String?): String {
        val raw = language?.trim().orEmpty()
        if (raw.isBlank()) return "en"
        val tag = toBcp47Tag(raw)
        val parsedLanguage = Locale.forLanguageTag(tag).language.lowercase()
        if (parsedLanguage.isNotBlank()) return parsedLanguage
        return raw.substringBefore("-").substringBefore("_").lowercase().ifBlank { "en" }
    }

    fun getEncouragement(context: Context, language: String): String {
        val localizedCtx = getLocalizedContext(context, language)
        return try {
            localizedCtx.getString(R.string.ai_encouragement)
        } catch (e: Exception) {
            "Great question! 🌟"
        }
    }

    fun getNoDataResponse(context: Context, language: String, query: String): String {
        return try {
            getLocalizedContext(context, language).getString(R.string.ai_no_data)
        } catch (e: Exception) {
            "Hmm, I couldn't find information about that. Try asking about an element like 'oxygen' or 'gold'!"
        }
    }

    fun formatElementResponse(
        context: Context,
        language: String,
        elementName: String,
        propertyLabel: String,
        value: String,
        isRepeat: Boolean = false
    ): String {
        if (value.isEmpty() || value == "---") {
            return context.getString(R.string.ai_no_property_data, elementName, propertyLabel)
        }
        return if (isRepeat) {
            context.getString(R.string.ai_recap_is, elementName, propertyLabel, value)
        } else {
            context.getString(R.string.ai_property_is, elementName, propertyLabel, value)
        }
    }

    fun formatElementOverview(
        context: Context,
        language: String,
        elementName: String,
        symbol: String,
        atomicNumber: String,
        category: String,
        group: String,
        appearance: String,
        discovery: String,
        description: String,
        protons: String,
        neutrons: String,
        electrons: String
    ): String {
        val localizedCtx = getLocalizedContext(context, language)
        val encouragement = getEncouragement(context, language)
        val intro = localizedCtx.getString(R.string.ai_element_overview_intro, encouragement, elementName, symbol, atomicNumber, category)
        val groupText = if (group.isNotEmpty() && !category.contains(group, ignoreCase = true)) {
            localizedCtx.getString(R.string.ai_element_overview_series, group)
        } else ""
        val appearanceText = if (appearance.isNotEmpty()) localizedCtx.getString(R.string.ai_element_overview_appearance, appearance) else ""
        val isotopeInfo = when {
            protons.isNotEmpty() && neutrons.isNotEmpty() -> localizedCtx.getString(R.string.ai_element_overview_isotopes, elementName, protons, electrons, neutrons)
            electrons.isNotEmpty() -> localizedCtx.getString(R.string.ai_element_overview_electrons, electrons)
            else -> ""
        }
        val discoveryText = if (discovery.isNotEmpty()) " $discovery" else ""
        val descriptionText = if (description.isNotEmpty()) " $description" else ""
        return "$intro$groupText$appearanceText$isotopeInfo$discoveryText$descriptionText"
    }

    fun formatComprehensiveResponse(context: Context, language: String, elementName: String, data: Map<String, String>): String {
        val localizedCtx = getLocalizedContext(context, language)
        val encouragement = getEncouragement(context, language)
        val intro = localizedCtx.getString(R.string.ai_comparing_title, elementName)
        val facts = data.entries.joinToString("\n") { (prop, value) -> 
            localizedCtx.getString(R.string.ai_compare_property_row, prop, value, "")
        }
        return "$encouragement $intro\n$facts"
    }

    fun formatRichOverview(
        context: Context,
        language: String,
        elementName: String,
        sections: List<Pair<Int, String>> // List of Pair(Header Resource ID, Content)
    ): String {
        val localizedCtx = getLocalizedContext(context, language)
        val sb = StringBuilder()
        
        for ((headerResId, content) in sections) {
            val header = localizedCtx.getString(headerResId, elementName)
            sb.append("### $header\n")
            sb.append(content).append("\n\n")
        }
        
        return sb.toString().trim()
    }

    fun getRandomFact(context: Context, language: String): String {
        return try {
            getLocalizedContext(context, language).resources.getStringArray(R.array.ai_facts).random()
        } catch (e: Exception) {
            "Did you know? Oxygen is the most abundant element in Earth's crust!"
        }
    }

    fun getSuggestedQuestions(
        context: Context,
        language: String,
        elementName: String?,
        topic: String?
    ): List<String> {
        val suggestions = mutableListOf<String>()

        when (language) {
            "sv" -> {
                if (elementName != null) {
                    val element = elementName.replaceFirstChar { it.uppercase() }
                    suggestions.add("Användning av $element")
                    suggestions.add("Atomnummer för $element")
                    suggestions.add("Är $element farligt?")
                    suggestions.add("Vart finns $element?")
                    suggestions.add("Elektronkonfiguration")
                } else if (topic == "trends") {
                    suggestions.add("Vad är periodiska trender?")
                    suggestions.add("Elektronegativitet")
                    suggestions.add("Atomradie")
                    suggestions.add("Joniseringsenergi")
                } else {
                    suggestions.add("Berätta en kul fakta")
                    suggestions.add("Starta en quiz")
                    suggestions.add("Vilka är ädelgaserna?")
                    suggestions.add("Tyngsta grundämnet?")
                }
            }
            "de" -> {
                if (elementName != null) {
                    val element = elementName.replaceFirstChar { it.uppercase() }
                    suggestions.add("Verwendung von $element")
                    suggestions.add("Atommasse von $element")
                    suggestions.add("Ist $element gefährlich?")
                    suggestions.add("Wo wird $element gefunden?")
                } else if (topic == "trends") {
                    suggestions.add("Was sind periodische Trends?")
                    suggestions.add("Elektronegativität")
                    suggestions.add("Atomradius")
                    suggestions.add("Ionisierungsenergie")
                } else {
                    suggestions.add("Erzähl mir einen Fakt")
                    suggestions.add("Starte ein Quiz")
                    suggestions.add("Was sind Edelgase?")
                    suggestions.add("Schwerstes Element?")
                }
            }
            else -> {
                if (elementName != null) {
                    val element = elementName.replaceFirstChar { it.uppercase() }
                    suggestions.add("Usage of $element")
                    suggestions.add("Atomic mass of $element")
                    suggestions.add("Is $element dangerous?")
                    suggestions.add("Where is $element found?")
                    suggestions.add("Electron configuration")
                } else if (topic == "trends") {
                    suggestions.add("What are periodic trends?")
                    suggestions.add("Electronegativity trend")
                    suggestions.add("Atomic radius trend")
                    suggestions.add("Ionization energy")
                } else {
                    suggestions.add("Tell me a fun fact")
                    suggestions.add("Start a quiz")
                    suggestions.add("What are alkali metals?")
                    suggestions.add("Heaviest element?")
                }
            }
        }

        return suggestions.shuffled().take(3)
    }
}
