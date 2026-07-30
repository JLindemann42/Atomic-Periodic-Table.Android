package com.jlindemann.science.ai

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jlindemann.science.R

/**
 * Manages the "learning" aspects of the AI.
 * Tracks user interaction patterns to improve response relevance and style.
 * Does NOT store user-provided facts (privacy-focused).
 */
class AILearningManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_learning_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Tracking user interaction patterns
    private var elementInterests: MutableMap<String, Int> = mutableMapOf()
    private var propertyInterests: MutableMap<String, Int> = mutableMapOf()
    
    // Learning response style preference (0.0 = General, 1.0 = Technical)
    private var technicalScore: Float = 0.5f
    private var sessionCount: Int = 0

    init {
        loadData()
    }

    private fun loadData() {
        val elementJson = prefs.getString("element_interests", "{}")
        val propertyJson = prefs.getString("property_interests", "{}")
        technicalScore = prefs.getFloat("technical_score", 0.5f)
        sessionCount = prefs.getInt("session_count", 0)

        val mapType = object : TypeToken<MutableMap<String, Int>>() {}.type
        elementInterests = gson.fromJson(elementJson, mapType) ?: mutableMapOf()
        propertyInterests = gson.fromJson(propertyJson, mapType) ?: mutableMapOf()
    }

    fun saveData() {
        prefs.edit().apply {
            putString("element_interests", gson.toJson(elementInterests))
            putString("property_interests", gson.toJson(propertyInterests))
            putFloat("technical_score", technicalScore)
            putInt("session_count", sessionCount)
            apply()
        }
    }

    fun incrementSession() {
        sessionCount++
        saveData()
    }

    /**
     * Track that the user asked about a specific element
     */
    fun trackElementInterest(elementName: String) {
        val count = elementInterests.getOrDefault(elementName.lowercase(), 0)
        elementInterests[elementName.lowercase()] = count + 1
        saveData()
    }

    /**
     * Track interest and adjust response style
     */
    fun trackPropertyInterest(property: String, isTechnical: Boolean) {
        val count = propertyInterests.getOrDefault(property.lowercase(), 0)
        propertyInterests[property.lowercase()] = count + 1
        
        // Adjust technical score based on query type
        if (isTechnical) {
            technicalScore = (technicalScore * 0.9f) + 0.1f // Slowly lean technical
        } else {
            technicalScore = (technicalScore * 0.9f) // Slowly lean general
        }
        
        saveData()
    }

    /**
     * AI uses this to decide if it should give a technical or simplified answer
     */
    fun isTechnicalPreferred(): Boolean = technicalScore > 0.7f

    fun getFavoriteElement(): String? {
        return elementInterests.maxByOrNull { it.value }?.key
    }

    fun getTopProperties(limit: Int = 3): List<String> {
        return propertyInterests.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }
    
    fun getPersonalizedGreeting(context: Context, language: String): String? {
        val favElement = getFavoriteElement() ?: return null
        val interests = getTopProperties(1)
        val localizedCtx = AIPersonality.getLocalizedContext(context, language)
        
        return when {
            interests.isNotEmpty() -> 
                localizedCtx.getString(R.string.ai_personalized_greeting_interest, interests[0], favElement)
            else -> 
                localizedCtx.getString(R.string.ai_personalized_greeting_fav, favElement)
        }
    }
}
