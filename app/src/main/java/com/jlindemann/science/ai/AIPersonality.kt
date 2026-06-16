package com.jlindemann.science.ai

import android.content.Context
import android.content.res.Configuration
import com.jlindemann.science.R
import java.util.Locale

/**
 * AI personality and response templates for the AI agent
 * Supports multiple languages via localized string resources
 */
object AIPersonality {
    
    private fun getLocalizedContext(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getGreeting(context: Context, language: String): String {
        val localizedCtx = getLocalizedContext(context, language)
        return try {
            localizedCtx.getString(R.string.ai_greeting)
        } catch (e: Exception) {
            "Hey there! 👋 I'm your chemistry buddy. Ask me anything about elements!"
        }
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
        val localizedCtx = getLocalizedContext(context, language)
        return try {
            localizedCtx.getString(R.string.ai_no_data)
        } catch (e: Exception) {
            "Hmm, I couldn't find information about that. Try asking about an element like 'oxygen' or 'gold'!"
        }
    }

    private val elementTemplates = mapOf(
        "atomic number" to R.string.atomic_number_label,
        "atomic mass" to R.string.atomic_mass_colon,
        "boiling point" to R.string.boiling_point_colon,
        "melting point" to R.string.melting_point_colon,
        "density" to R.string.density_colon,
        "electron configuration" to R.string.electron_configuration_colon,
        "oxidation state" to R.string.oxidation_states_colon,
        "crystal structure" to R.string.crystal_structure,
        "electronegativity" to R.string.electronegativity_colon,
        "ionization energy" to R.string.ionization_energies_colon,
        "category" to R.string.element_groups,
        "group" to R.string.group_label,
        "period" to R.string.element_number
    )
    
    fun formatElementResponse(context: Context, language: String, elementName: String, propertyLabel: String, value: String, isRepeat: Boolean = false): String {
        if (value.isEmpty() || value == "---") return context.getString(R.string.ai_no_property_data, elementName, propertyLabel)
        
        val intro = if (isRepeat) {
            context.getString(R.string.ai_recap_is, elementName, propertyLabel, value)
        } else {
            context.getString(R.string.ai_property_is, elementName, propertyLabel, value)
        }
        
        return "$intro"
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
        
        val appearanceText = if (appearance.isNotEmpty()) {
            localizedCtx.getString(R.string.ai_element_overview_appearance, appearance)
        } else ""
        
        val isotopeInfo = if (protons.isNotEmpty() && neutrons.isNotEmpty()) {
            localizedCtx.getString(R.string.ai_element_overview_isotopes, elementName, protons, electrons, neutrons)
        } else if (electrons.isNotEmpty()) {
            localizedCtx.getString(R.string.ai_element_overview_electrons, electrons)
        } else ""
        
        val discoveryText = if (discovery.isNotEmpty()) " $discovery" else ""
        val descriptionText = if (description.isNotEmpty()) " $description" else ""
        
        return "$intro$groupText$appearanceText$isotopeInfo$discoveryText$descriptionText"
    }

    fun formatComprehensiveResponse(context: Context, language: String, elementName: String, data: Map<String, String>): String {
        val encouragement = getEncouragement(context, language)
        val intro = context.getString(R.string.ai_comparing_title, elementName)
        
        val facts = data.entries.joinToString(" ") { (prop, value) ->
            "• $prop: $value"
        }
        
        return "$encouragement $intro\n$facts"
    }
    
    fun getRandomFact(context: Context, language: String): String {
        val localizedCtx = getLocalizedContext(context, language)
        return try {
            val facts = localizedCtx.resources.getStringArray(R.array.ai_facts)
            facts.random()
        } catch (e: Exception) {
            "Did you know? Oxygen is the most abundant element in Earth's crust!"
        }
    }
}
