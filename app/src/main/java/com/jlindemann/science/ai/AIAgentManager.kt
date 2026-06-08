package com.jlindemann.science.ai

import android.content.Context
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.preferences.LanguagePreference
import com.jlindemann.science.utils.ElementDataLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Manager for AI agent responses and element data retrieval
 */
class AIAgentManager(private val context: Context) {
    
    private var elementData: JSONObject? = null
    private var isDataLoaded = false
    
    /**
     * Initialize AI agent with element data
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                val language = ElementDataLoader.getAppLanguage(context)
                elementData = getElementDataByLanguage(language)
                isDataLoaded = true
            } catch (e: Exception) {
                isDataLoaded = false
            }
        }
    }
    
    /**
     * Load element data for the specified language using assets
     */
    private fun getElementDataByLanguage(language: String): JSONObject? {
        return try {
            val fileName = "elements_$language.json"
            val inputStream = context.assets.open(fileName)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            JSONObject(jsonString)
        } catch (e: Exception) {
            // Fallback to English
            try {
                val inputStream = context.assets.open("elements_en.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                JSONObject(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Generate AI response based on user query
     */
    suspend fun generateResponse(
        userMessage: String,
        contextElement: String? = null
    ): ChatMessage {
        return withContext(Dispatchers.Default) {
            val responseText = when {
                userMessage.isBlank() -> AIPersonality.getNoDataResponse(userMessage)
                contextElement != null -> handleElementContextQuery(userMessage, contextElement)
                isGeneralGreeting(userMessage) -> AIPersonality.getGreeting()
                isFactRequest(userMessage) -> AIPersonality.getRandomFact()
                else -> handleElementQuery(userMessage)
            }
            
            ChatMessage(
                id = UUID.randomUUID().toString(),
                text = responseText,
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Handle queries about a specific element when viewing element details
     */
    private fun handleElementContextQuery(query: String, elementName: String): String {
        return try {
            val element = elementData?.optJSONObject(elementName.lowercase()) ?: return AIPersonality.getNoDataResponse(query)
            
            val response = when {
                query.contains("atomic", ignoreCase = true) -> {
                    val atomicNum = element.optString("element_atomic_number", "N/A")
                    AIPersonality.formatElementResponse(elementName, "atomic number", atomicNum)
                }
                query.contains("mass", ignoreCase = true) -> {
                    val mass = element.optString("element_atomicmass", "N/A")
                    AIPersonality.formatElementResponse(elementName, "atomic mass", mass)
                }
                query.contains("description", ignoreCase = true) || query.contains("about", ignoreCase = true) -> {
                    element.optString("description", AIPersonality.getRandomFact())
                }
                query.contains("electron", ignoreCase = true) -> {
                    val config = element.optString("element_electron_config", "N/A")
                    AIPersonality.formatElementResponse(elementName, "electron configuration", config)
                }
                query.contains("boiling", ignoreCase = true) -> {
                    val boiling = element.optString("element_boiling_celsius", "N/A")
                    AIPersonality.formatElementResponse(elementName, "boiling point", boiling)
                }
                query.contains("melting", ignoreCase = true) -> {
                    val melting = element.optString("element_melting_celsius", "N/A")
                    AIPersonality.formatElementResponse(elementName, "melting point", melting)
                }
                query.contains("density", ignoreCase = true) -> {
                    val density = element.optString("element_density", "N/A")
                    AIPersonality.formatElementResponse(elementName, "density", density)
                }
                else -> element.optString("description", AIPersonality.getRandomFact())
            }
            "${AIPersonality.getEncouragement()} $response"
        } catch (e: Exception) {
            AIPersonality.getNoDataResponse(query)
        }
    }
    
    /**
     * Handle general element queries
     */
    private fun handleElementQuery(query: String): String {
        return try {
            val lowerQuery = query.lowercase()
            
            // Try to find element by name
            val element = findElementByQuery(lowerQuery)
            
            if (element != null) {
                val elementName = element.optString("element", "Element")
                val description = element.optString("description", "")
                
                val response = when {
                    description.isNotEmpty() -> description.take(200) + "..."
                    else -> AIPersonality.getRandomFact()
                }
                
                "${AIPersonality.getEncouragement()} Here's what I found about $elementName: $response"
            } else {
                AIPersonality.getNoDataResponse(query)
            }
        } catch (e: Exception) {
            AIPersonality.getNoDataResponse(query)
        }
    }
    
    /**
     * Find element by name or symbol in the data
     */
    private fun findElementByQuery(query: String): JSONObject? {
        return try {
            elementData?.keys()?.forEach { key ->
                val element = elementData!!.optJSONObject(key) ?: return@forEach
                val name = element.optString("element", "").lowercase()
                val symbol = element.optString("short", "").lowercase()
                
                if (name.contains(query) || symbol.contains(query)) {
                    return element
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if message is a greeting
     */
    private fun isGeneralGreeting(message: String): Boolean {
        val greetings = listOf("hi", "hello", "hey", "start", "begin", "help", "what can you do")
        return greetings.any { message.lowercase().contains(it) }
    }
    
    /**
     * Check if message is asking for a fact
     */
    private fun isFactRequest(message: String): Boolean {
        val factKeywords = listOf("fact", "fun fact", "did you know", "interesting", "cool")
        return factKeywords.any { message.lowercase().contains(it) }
    }
}
