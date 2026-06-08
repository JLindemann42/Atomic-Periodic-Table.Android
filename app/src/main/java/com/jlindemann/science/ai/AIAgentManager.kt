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
    private var conversationHistory = mutableListOf<ChatMessage>()
    
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
     * Set conversation history for context-aware responses
     */
    fun setConversationHistory(messages: List<ChatMessage>) {
        conversationHistory = messages.toMutableList()
    }
    
    /**
     * Add a message to conversation history
     */
    fun addToConversationHistory(message: ChatMessage) {
        conversationHistory.add(message)
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
                else -> {
                    // Try to infer element from context if not explicitly provided
                    val inferredElement = inferElementFromContext(userMessage)
                    if (inferredElement != null) {
                        handleElementContextQuery(userMessage, inferredElement)
                    } else {
                        handleElementQuery(userMessage)
                    }
                }
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
     * Try to infer element context from conversation history
     */
    private fun inferElementFromContext(currentQuery: String): String? {
        // Check if query references "it", "that", "this element" etc.
        val contextKeywords = listOf("it", "that", "this", "its", "the element")
        val hasContextReference = contextKeywords.any { currentQuery.lowercase().contains(it) }
        
        if (hasContextReference) {
            // Find the last mentioned element in conversation history
            for (i in conversationHistory.size - 1 downTo 0) {
                val message = conversationHistory[i]
                if (!message.isFromUser) {
                    // Try to extract element name from AI response
                    val elementName = extractElementNameFromResponse(message.text)
                    if (elementName != null) {
                        return elementName
                    }
                }
            }
        }
        return null
    }
    
    /**
     * Extract element name from an AI response
     */
    private fun extractElementNameFromResponse(response: String): String? {
        // Look for common patterns like "Here's what I found about [Element]:"
        val pattern = "about\\s+([A-Z][a-z]+)".toRegex(RegexOption.IGNORE_CASE)
        val match = pattern.find(response)
        if (match != null) {
            return match.groupValues[1]
        }
        
        // Check if response starts with encouragement and element name
        val words = response.split(" ")
        for (word in words) {
            if (elementData?.has(word.lowercase()) == true) {
                return word
            }
        }
        
        return null
    }
    
    /**
     * Handle queries about a specific element when viewing element details
     */
    private fun handleElementContextQuery(query: String, elementName: String): String {
        return try {
            val element = elementData?.optJSONObject(elementName.lowercase()) ?: return AIPersonality.getNoDataResponse(query)
            
            val response = when {
                // Basic properties
                query.contains("atomic", ignoreCase = true) && query.contains("number", ignoreCase = true) -> {
                    val atomicNum = element.optString("element_atomic_number", "")
                    if (atomicNum.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "atomic number", atomicNum)
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("mass", ignoreCase = true) && !query.contains("molar", ignoreCase = true) -> {
                    val mass = element.optString("element_atomicmass", "")
                    if (mass.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "atomic mass", mass)
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Appearance and physical properties
                query.contains("appear", ignoreCase = true) -> {
                    val appearance = element.optString("element_appearance", "")
                    if (appearance.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName appears as: $appearance"
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("description", ignoreCase = true) || query.contains("about", ignoreCase = true) -> {
                    val description = element.optString("description", "")
                    if (description.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $description"
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Electron configuration and structure
                query.contains("electron", ignoreCase = true) -> {
                    val config = element.optString("element_electron_config", "")
                    if (config.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "electron configuration", config)
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("shell", ignoreCase = true) -> {
                    val shells = element.optString("element_shells_electrons", "")
                    if (shells.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "electron shells", shells)
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Temperature properties
                query.contains("boiling", ignoreCase = true) -> {
                    val boiling = element.optString("element_boiling_celsius", "")
                    if (boiling.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "boiling point", "$boiling°C")
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("melting", ignoreCase = true) -> {
                    val melting = element.optString("element_melting_celsius", "")
                    if (melting.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "melting point", "$melting°C")
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("sublimation", ignoreCase = true) -> {
                    val sublimation = element.optString("element_sublimation_celsius", "")
                    if (sublimation.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "sublimation point", "$sublimation°C")
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Density and volume
                query.contains("density", ignoreCase = true) -> {
                    val density = element.optString("element_density", "")
                    if (density.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "density", density)
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("volume", ignoreCase = true) -> {
                    val volume = element.optString("element_volume_magnetic_susceptibility", "")
                    val molarVol = element.optString("molar_volume", "")
                    val data = if (molarVol.isNotEmpty()) molarVol else volume
                    if (data.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "volume", data)
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Electrochemistry
                query.contains("ion", ignoreCase = true) && query.contains("charge", ignoreCase = true) -> {
                    val charge = element.optString("element_ion_charge", "")
                    if (charge.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "common ion charge", charge)
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("electron affinity", ignoreCase = true) -> {
                    val affinity = element.optString("electron_affinity", "")
                    if (affinity.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "electron affinity", affinity)
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("electronegativity", ignoreCase = true) -> {
                    val electronegativity = element.optString("element_electronegativty", "")
                    if (electronegativity.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "electronegativity", electronegativity)
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("ionization", ignoreCase = true) -> {
                    val ionization = element.optString("element_ionization_energy1", "")
                    if (ionization.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "first ionization energy", ionization)
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Crystal and structure
                query.contains("crystal", ignoreCase = true) -> {
                    val crystal = element.optString("element_crystal_structure", "")
                    if (crystal.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName has a $crystal crystal structure."
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("block", ignoreCase = true) -> {
                    val block = element.optString("element_block", "")
                    if (block.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName is in the $block block of the periodic table."
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("group", ignoreCase = true) -> {
                    val group = element.optString("element_group", "")
                    if (group.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName belongs to group $group."
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Thermal and electrical properties
                query.contains("thermal", ignoreCase = true) && query.contains("conductivity", ignoreCase = true) -> {
                    val thermal = element.optString("thermal_conductivity", "")
                    if (thermal.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "thermal conductivity", thermal)
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("electrical", ignoreCase = true) && query.contains("conductivity", ignoreCase = true) -> {
                    val electrical = element.optString("element_electrical_conductivity", "")
                    if (electrical.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "electrical conductivity", electrical)
                    } else AIPersonality.getNoDataResponse(query)
                }
                query.contains("resistivity", ignoreCase = true) -> {
                    val resistivity = element.optString("resistivity", "")
                    if (resistivity.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "electrical resistivity", resistivity)
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Discovery and history
                query.contains("discover", ignoreCase = true) || query.contains("found", ignoreCase = true) -> {
                    val discoverer = element.optString("element_discovered_name", "")
                    val year = element.optString("element_year", "")
                    val discovery = when {
                        discoverer.isNotEmpty() && year.isNotEmpty() -> 
                            "$elementName was discovered by $discoverer in $year."
                        year.isNotEmpty() -> 
                            "$elementName was discovered in $year."
                        else -> AIPersonality.getNoDataResponse(query)
                    }
                    "${AIPersonality.getEncouragement()} $discovery"
                }
                
                // Radioactivity
                query.contains("radioactive", ignoreCase = true) -> {
                    val radioactive = element.optString("radioactive", "")
                    val response = if (radioactive.isNotEmpty()) {
                        "$elementName is radioactive: $radioactive"
                    } else AIPersonality.getNoDataResponse(query)
                    "${AIPersonality.getEncouragement()} $response"
                }
                
                // Phase and state
                query.contains("phase", ignoreCase = true) -> {
                    val phase = element.optString("element_phase", "")
                    if (phase.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName is a $phase at standard conditions."
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Hardness
                query.contains("hard", ignoreCase = true) -> {
                    val mohs = element.optString("mohs_hardness", "")
                    val vickers = element.optString("vickers_hardness", "")
                    val hardness = if (mohs.isNotEmpty()) "Mohs hardness: $mohs" 
                                   else if (vickers.isNotEmpty()) "Vickers hardness: $vickers"
                                   else ""
                    if (hardness.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName has $hardness."
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Oxidation states
                query.contains("oxidation", ignoreCase = true) -> {
                    val oxPos = element.optString("oxidation_state_pos", "")
                    val oxNeg = element.optString("oxidation_state_neg", "")
                    val oxidation = when {
                        oxPos.isNotEmpty() && oxNeg.isNotEmpty() -> 
                            "positive: $oxPos, negative: $oxNeg"
                        oxPos.isNotEmpty() -> 
                            "positive: $oxPos"
                        else -> ""
                    }
                    if (oxidation.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName has oxidation states: $oxidation"
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Abundance
                query.contains("abundance", ignoreCase = true) -> {
                    val crust = element.optString("element_group", "")
                    val earth = element.optString("crustal_rocks", "")
                    if (earth.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName makes up about $earth of Earth's crust."
                    } else if (crust.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $elementName exists in Earth's crust."
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Default: try to return description
                else -> {
                    val description = element.optString("description", "")
                    if (description.isNotEmpty()) {
                        "${AIPersonality.getEncouragement()} $description"
                    } else AIPersonality.getNoDataResponse(query)
                }
            }
            response
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
                
                // Classify the query type and respond accordingly
                val response = when {
                    // Questions about appearance
                    query.contains("look", ignoreCase = true) || query.contains("appear", ignoreCase = true) -> {
                        val appearance = element.optString("element_appearance", "")
                        if (appearance.isNotEmpty()) {
                            "$elementName appears as: $appearance"
                        } else element.optString("description", AIPersonality.getRandomFact())
                    }
                    
                    // Questions about discovery
                    query.contains("discover", ignoreCase = true) || query.contains("found", ignoreCase = true) -> {
                        val discoverer = element.optString("element_discovered_name", "")
                        val year = element.optString("element_year", "")
                        when {
                            discoverer.isNotEmpty() && year.isNotEmpty() -> 
                                "$elementName was discovered by $discoverer in $year."
                            year.isNotEmpty() -> 
                                "$elementName was discovered in $year."
                            else -> element.optString("description", AIPersonality.getRandomFact())
                        }
                    }
                    
                    // Questions about properties
                    query.contains("what", ignoreCase = true) || query.contains("tell", ignoreCase = true) || 
                    query.contains("info", ignoreCase = true) -> {
                        element.optString("description", AIPersonality.getRandomFact())
                    }
                    
                    // Questions about numbers
                    query.contains("number", ignoreCase = true) || query.contains("atomic", ignoreCase = true) -> {
                        val atomicNum = element.optString("element_atomic_number", "")
                        if (atomicNum.isNotEmpty()) {
                            "$elementName's atomic number is $atomicNum."
                        } else element.optString("description", AIPersonality.getRandomFact())
                    }
                    
                    // Questions about density
                    query.contains("dense", ignoreCase = true) || query.contains("density", ignoreCase = true) -> {
                        val density = element.optString("element_density", "")
                        if (density.isNotEmpty()) {
                            "$elementName has a density of $density."
                        } else element.optString("description", AIPersonality.getRandomFact())
                    }
                    
                    // Questions about state/phase
                    query.contains("state", ignoreCase = true) || query.contains("solid", ignoreCase = true) ||
                    query.contains("liquid", ignoreCase = true) || query.contains("gas", ignoreCase = true) -> {
                        val phase = element.optString("element_phase", "")
                        if (phase.isNotEmpty()) {
                            "$elementName is a $phase at standard conditions."
                        } else element.optString("description", AIPersonality.getRandomFact())
                    }
                    
                    // Default
                    else -> element.optString("description", AIPersonality.getRandomFact())
                }
                
                "${AIPersonality.getEncouragement()} Here's what I found: $response"
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
