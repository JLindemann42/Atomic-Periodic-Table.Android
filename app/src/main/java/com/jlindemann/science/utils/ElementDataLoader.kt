package com.jlindemann.science.utils

import android.content.Context
import android.content.res.AssetManager
import org.json.JSONObject
import java.io.IOException
import java.util.*

/**
 * Utility class for loading element data from consolidated JSON files with language support.
 * Supports fallback to English if element data is not available in the requested language.
 */
object ElementDataLoader {
    private const val DEFAULT_LANGUAGE = "en"
    
    // Cache for loaded language files
    private val languageCache = mutableMapOf<String, JSONObject>()
    
    /**
     * Load element data for a specific element with language support
     * 
     * @param context Android context for accessing assets
     * @param elementName Name of the element (e.g., "hydrogen")
     * @param language Language code (e.g., "en", "sv"). Defaults to system language or "en"
     * @return JSONObject containing element data, or null if not found
     */
    fun loadElementData(
        context: Context, 
        elementName: String, 
        language: String? = null
    ): JSONObject? {
        val lang = language ?: getSystemLanguage()
        
        // Try to load from requested language
        val elementData = loadFromLanguage(context.assets, elementName, lang)
        if (elementData != null) {
            return elementData
        }
        
        // Fallback to English if not found in requested language
        if (lang != DEFAULT_LANGUAGE) {
            Pasteur.info("ElementDataLoader", "Element '$elementName' not found in language '$lang', falling back to English")
            return loadFromLanguage(context.assets, elementName, DEFAULT_LANGUAGE)
        }
        
        return null
    }
    
    /**
     * Load element data from a specific language file
     */
    private fun loadFromLanguage(
        assets: AssetManager,
        elementName: String,
        language: String
    ): JSONObject? {
        try {
            val elementsData = getLanguageData(assets, language)
            if (elementsData.has(elementName)) {
                return elementsData.getJSONObject(elementName)
            }
        } catch (e: IOException) {
            Pasteur.error("ElementDataLoader", "Error loading element data for language '$language': ${e.message}")
        }
        return null
    }
    
    /**
     * Load and cache the entire language file
     */
    private fun getLanguageData(assets: AssetManager, language: String): JSONObject {
        // Check cache first
        if (languageCache.containsKey(language)) {
            return languageCache[language]!!
        }
        
        // Load from assets
        val fileName = "elements_$language.json"
        val inputStream = assets.open(fileName)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        
        // Cache the loaded data
        languageCache[language] = jsonObject
        
        return jsonObject
    }
    
    /**
     * Get the system language code (2-letter ISO 639-1)
     */
    private fun getSystemLanguage(): String {
        val locale = Locale.getDefault()
        return locale.language
    }
    
    /**
     * Clear the language cache (useful for testing or memory management)
     */
    fun clearCache() {
        languageCache.clear()
    }
    
    /**
     * Check if a language file is available
     */
    fun isLanguageAvailable(assets: AssetManager, language: String): Boolean {
        return try {
            val fileName = "elements_$language.json"
            assets.open(fileName).close()
            true
        } catch (e: IOException) {
            false
        }
    }
    
    /**
     * Get list of available languages
     */
    fun getAvailableLanguages(assets: AssetManager): List<String> {
        val languages = mutableListOf<String>()
        try {
            val files = assets.list("") ?: emptyArray()
            for (file in files) {
                if (file.startsWith("elements_") && file.endsWith(".json")) {
                    val lang = file.substring(9, file.length - 5) // Extract language code
                    languages.add(lang)
                }
            }
        } catch (e: IOException) {
            Pasteur.error("ElementDataLoader", "Error listing available languages: ${e.message}")
        }
        return languages.sorted()
    }
}
