package com.jlindemann.science.ai

import android.content.Context
import android.content.res.Configuration
import com.jlindemann.science.R
import java.util.Locale

/** Response templates and personality strings for the AI agent. All text is localized. */
object AIPersonality {

    private fun getLocalizedContext(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getGreeting(context: Context, language: String): String {
        return try {
            getLocalizedContext(context, language).getString(R.string.ai_greeting)
        } catch (e: Exception) {
            "Hey there! 👋 I'm your chemistry buddy. Ask me anything about elements!"
        }
    }

    fun getEncouragement(context: Context, language: String): String {
        return try {
            getLocalizedContext(context, language).getString(R.string.ai_encouragement)
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
        val facts = data.entries.joinToString("\n") { (prop, value) -> "• $prop: $value" }
        return "$encouragement $intro\n$facts"
    }

    fun getRandomFact(context: Context, language: String): String {
        return try {
            getLocalizedContext(context, language).resources.getStringArray(R.array.ai_facts).random()
        } catch (e: Exception) {
            "Did you know? Oxygen is the most abundant element in Earth's crust!"
        }
    }
}
