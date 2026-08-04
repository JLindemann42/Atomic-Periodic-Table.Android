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
        // Through the localized context, like every sibling here. Reading off the raw `context`
        // resolved these three sentences in the device locale while the rest of the same reply came
        // back in the detected chat language.
        val localizedCtx = getLocalizedContext(context, language)
        if (value.isEmpty() || value == "---") {
            return localizedCtx.getString(R.string.ai_no_property_data, elementName, propertyLabel)
        }
        return if (isRepeat) {
            localizedCtx.getString(R.string.ai_recap_is, elementName, propertyLabel, value)
        } else {
            localizedCtx.getString(R.string.ai_property_is, elementName, propertyLabel, value)
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

    /**
     * The chips offered under an empty or idle chat.
     *
     * Read from `<string-array>` resources rather than written here, so all twelve shipped
     * languages get them. The hand-written `when (language)` this replaced covered Swedish, German
     * and English only — Afrikaans, Filipino, Spanish, French, Hindi, Italian, Portuguese, Urdu and
     * Chinese users were offered English chips in an otherwise translated conversation.
     */
    fun getSuggestedQuestions(
        context: Context,
        language: String,
        elementName: String?,
        topic: String?
    ): List<String> {
        val localizedCtx = getLocalizedContext(context, language)
        val locale = Locale.forLanguageTag(toBcp47Tag(language))

        val suggestions = when {
            elementName != null -> {
                val element = elementName.replaceFirstChar { it.uppercase(locale) }
                // getStringArray does not format, so the substitution happens here — with the chat
                // locale, since this file deliberately refuses to touch Locale.setDefault.
                array(localizedCtx, R.array.ai_suggest_element).map { item ->
                    runCatching { String.format(locale, item, element) }.getOrDefault(item)
                }
            }
            topic == "trends" -> array(localizedCtx, R.array.ai_suggest_trends)
            // With no element and no topic in play there is room to advertise what the assistant
            // can do beyond lookups, which is otherwise undiscoverable from an empty chat.
            else -> array(localizedCtx, R.array.ai_suggest_general) +
                    array(localizedCtx, R.array.ai_suggest_tools).shuffled().take(1)
        }

        return suggestions.shuffled().take(3)
    }

    private fun array(context: Context, resId: Int): List<String> =
        runCatching { context.resources.getStringArray(resId).toList() }.getOrDefault(emptyList())
}
