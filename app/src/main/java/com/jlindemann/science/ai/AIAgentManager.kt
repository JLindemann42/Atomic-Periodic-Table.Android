package com.jlindemann.science.ai

import android.content.Context
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.utils.ElementDataLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Manager for AI agent responses and element data retrieval.
 * Handles fuzzy matching for element names and keywords to be resilient to typos.
 */
class AIAgentManager(private val context: Context?) {
    
    private var elementData: JSONObject? = null
    private var isDataLoaded = false
    private var conversationHistory = mutableListOf<ChatMessage>()
    private var currentElement: String? = null
    private val sharedProperties = mutableSetOf<String>()
    
    /**
     * Initialize AI agent with element data
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                val ctx = context ?: return@withContext
                val language = ElementDataLoader.getAppLanguage(ctx)
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
        val ctx = context ?: return null
        return try {
            val fileName = "elements_$language.json"
            val inputStream = ctx.assets.open(fileName)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            JSONObject(jsonString)
        } catch (e: Exception) {
            // Fallback to English
            try {
                val inputStream = ctx.assets.open("elements_en.json")
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
            // 1. Explicitly requested element in query
            val foundElement = findElementByQuery(userMessage)
            if (foundElement != null) {
                val elementName = foundElement.optString("element", "")
                if (elementName != currentElement) {
                    currentElement = elementName
                    sharedProperties.clear()
                }
            }
            
            // 2. Use context if no new element is found
            val targetElement = currentElement ?: contextElement ?: inferElementFromContext(userMessage)
            
            val responseText = when {
                userMessage.isBlank() -> AIPersonality.getNoDataResponse(userMessage)
                targetElement != null -> {
                    currentElement = targetElement
                    handleElementContextQuery(userMessage, targetElement)
                }
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
        // Look for common patterns like "about [Element]"
        val pattern = "about\\s+([A-Z][a-z]+)".toRegex(RegexOption.IGNORE_CASE)
        val match = pattern.find(response)
        if (match != null) {
            return match.groupValues[1]
        }
        
        // Check if response contains an element name we know
        val words = response.split(Regex("[^a-zA-Z]+"))
        for (word in words) {
            if (word.length > 2 && elementData?.has(word.lowercase()) == true) {
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
            val lowerQuery = query.lowercase()
            
            val response = when {
                // Basic properties
                hasKeyword(lowerQuery, listOf("atomic number", "proton", "number")) -> {
                    val prop = "atomic number"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val atomicNum = element.optString("element_atomic_number", "")
                    AIPersonality.formatElementResponse(elementName, prop, atomicNum, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("mass", "weight")) && !lowerQuery.contains("molar") -> {
                    val prop = "atomic mass"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val mass = element.optString("element_atomicmass", "")
                    AIPersonality.formatElementResponse(elementName, prop, mass, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("symbol", "short")) -> {
                    val prop = "symbol"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val symbol = element.optString("short", "")
                    AIPersonality.formatElementResponse(elementName, "chemical symbol", symbol, isRepeat)
                }
                
                // Classification
                hasKeyword(lowerQuery, listOf("type", "category", "class")) -> {
                    val prop = "category"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val type = element.optString("element_type", "")
                    AIPersonality.formatElementResponse(elementName, prop, type, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("group", "column")) -> {
                    val prop = "group"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val group = element.optString("element_group", "")
                    AIPersonality.formatElementResponse(elementName, prop, group, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("period", "row")) -> {
                    val prop = "period"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val period = element.optString("element_period", "")
                    AIPersonality.formatElementResponse(elementName, prop, period, isRepeat)
                }
                
                // Appearance and physical properties
                hasKeyword(lowerQuery, listOf("appear", "look", "color", "colour", "physical", "visual")) -> {
                    val prop = "appearance"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val appearance = element.optString("element_appearance", "")
                    if (appearance.isNotEmpty()) {
                        val intro = if (isRepeat) "As I mentioned," else AIPersonality.getEncouragement()
                        "$intro $elementName appears as: $appearance"
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Electron configuration and structure
                hasKeyword(lowerQuery, listOf("electron", "shell", "orbital", "config")) -> {
                    val prop = "electron configuration"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val config = element.optString("element_electron_config", "")
                    val shells = element.optString("element_shells_electrons", "")
                    if (lowerQuery.contains("shell") && shells.isNotEmpty()) {
                        AIPersonality.formatElementResponse(elementName, "electron shells", shells, isRepeat)
                    } else {
                        AIPersonality.formatElementResponse(elementName, prop, config, isRepeat)
                    }
                }
                
                // Temperature properties
                hasKeyword(lowerQuery, listOf("boiling", "boil")) -> {
                    val prop = "boiling point"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val boiling = element.optString("element_boiling_celsius", "")
                    AIPersonality.formatElementResponse(elementName, prop, if (boiling.isNotEmpty()) "$boiling°C" else "", isRepeat)
                }
                hasKeyword(lowerQuery, listOf("melting", "melt")) -> {
                    val prop = "melting point"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val melting = element.optString("element_melting_celsius", "")
                    AIPersonality.formatElementResponse(elementName, prop, if (melting.isNotEmpty()) "$melting°C" else "", isRepeat)
                }
                
                // Density and volume
                hasKeyword(lowerQuery, listOf("density", "dense")) -> {
                    val prop = "density"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val density = element.optString("element_density", "")
                    AIPersonality.formatElementResponse(elementName, prop, density, isRepeat)
                }
                
                // Electrochemistry
                hasKeyword(lowerQuery, listOf("ion", "charge", "valency")) -> {
                    val prop = "oxidation state"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val charge = element.optString("element_ion_charge", "")
                    AIPersonality.formatElementResponse(elementName, "common ion charge", charge, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("electronegativity", "negative")) -> {
                    val prop = "electronegativity"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val electronegativity = element.optString("element_electronegativty", "")
                    AIPersonality.formatElementResponse(elementName, prop, electronegativity, isRepeat)
                }
                
                // Discovery and history
                hasKeyword(lowerQuery, listOf("discover", "found", "who", "when", "year", "history")) -> {
                    val prop = "history"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val discoverer = element.optString("element_discovered_name", "")
                    val year = element.optString("element_year", "")
                    val discovery = when {
                        discoverer.isNotEmpty() && year.isNotEmpty() -> 
                            "$elementName was discovered by $discoverer in $year."
                        year.isNotEmpty() -> 
                            "$elementName was discovered in $year."
                        else -> ""
                    }
                    if (discovery.isNotEmpty()) {
                        val intro = if (isRepeat) "Just to recap," else AIPersonality.getEncouragement()
                        "$intro $discovery"
                    } else AIPersonality.getNoDataResponse(query)
                }
                
                // Radioactivity
                hasKeyword(lowerQuery, listOf("radioactive", "radiation", "decay")) -> {
                    val radioactive = element.optString("radioactive", "")
                    val intro = AIPersonality.getEncouragement()
                    if (radioactive.isNotEmpty()) {
                        "$intro $elementName is radioactive: $radioactive"
                    } else {
                        "$intro $elementName is generally considered stable and not radioactive."
                    }
                }
                
                // Phase and state
                hasKeyword(lowerQuery, listOf("phase", "state", "solid", "liquid", "gas")) -> {
                    val prop = "phase"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val phase = element.optString("element_phase", "")
                    AIPersonality.formatElementResponse(elementName, prop, phase, isRepeat)
                }

                // More info or specific deep-dive keywords (placed lower to avoid intercepting "tell me about")
                hasKeyword(lowerQuery, listOf("additional", "extra", "further", "tell me more", "what else")) -> {
                    provideNewInformation(element, elementName)
                }

                // Property specific keywords - priority for targeted data if explicit property mentioned
                hasKeyword(lowerQuery, listOf("density", "dense", "weight", "mass", "atomic number", "boiling", "melting", "symbol", "electron", "configuration", "shell", "oxidation", "charge", "electronegativity", "discovered", "discoverer", "year", "radioactive", "radiation", "decay", "phase", "state")) -> {
                    // Re-checking specifically for properties to avoid generic overview if a property is named
                    when {
                        hasKeyword(lowerQuery, listOf("atomic number", "proton")) -> {
                            val atomicNum = element.optString("element_atomic_number", "")
                            AIPersonality.formatElementResponse(elementName, "atomic number", atomicNum, sharedProperties.contains("atomic number")).also { sharedProperties.add("atomic number") }
                        }
                        hasKeyword(lowerQuery, listOf("mass", "weight")) -> {
                            val mass = element.optString("element_atomicmass", "")
                            AIPersonality.formatElementResponse(elementName, "atomic mass", mass, sharedProperties.contains("atomic mass")).also { sharedProperties.add("atomic mass") }
                        }
                        // ... (other specific property checks can be moved or kept in the main when block)
                        else -> provideOverview(element, elementName)
                    }
                }
                
                // Default: try to return a comprehensive summary or overview
                else -> {
                    provideOverview(element, elementName)
                }
            }
            response
        } catch (e: Exception) {
            AIPersonality.getNoDataResponse(query)
        }
    }

    private fun provideOverview(element: JSONObject, elementName: String): String {
        val symbol = element.optString("short", "")
        val atomicNum = element.optString("element_atomic_number", "")
        val type = element.optString("element_type", "")
        val group = element.optString("element_group", "")
        val discoverer = element.optString("element_discovered_name", "")
        val year = element.optString("element_year", "")
        val description = element.optString("description", "")
        val appearance = element.optString("element_appearance", "")
        val protons = element.optString("element_protons", "")
        val neutrons = element.optString("element_neutron_common", "")
        val electrons = element.optString("element_electrons", "")

        return if (!sharedProperties.contains("overview")) {
            // First time getting a generic overview for this element
            sharedProperties.add("overview")
            sharedProperties.add("symbol")
            sharedProperties.add("atomic number")
            sharedProperties.add("category")
            sharedProperties.add("history")
            sharedProperties.add("appearance")
            sharedProperties.add("protons")
            sharedProperties.add("neutrons")
            sharedProperties.add("electrons")

            val discoveryInfo = when {
                discoverer.isNotEmpty() && year.isNotEmpty() ->
                    "It was discovered by $discoverer in $year."
                year.isNotEmpty() ->
                    "It was discovered in $year."
                discoverer.isNotEmpty() ->
                    "It was discovered by $discoverer."
                else -> ""
            }

            AIPersonality.formatElementOverview(
                elementName,
                symbol,
                atomicNum,
                type,
                group,
                appearance,
                discoveryInfo,
                description,
                protons,
                neutrons,
                electrons
            )
        } else {
            // We've already shared the overview, give a lighter summary or a new fact
            val summaryData = mutableMapOf<String, String>()
            if (symbol.isNotEmpty()) summaryData["symbol"] = symbol
            if (atomicNum.isNotEmpty()) summaryData["atomic number"] = atomicNum
            if (type.isNotEmpty()) summaryData["category"] = type

            if (summaryData.size > 1) {
                AIPersonality.formatComprehensiveResponse(elementName, summaryData)
            } else if (description.isNotEmpty()) {
                "${AIPersonality.getEncouragement()} $description"
            } else {
                provideNewInformation(element, elementName)
            }
        }
    }

    /**
     * Provide a piece of information that hasn't been shared yet in this conversation
     */
    private fun provideNewInformation(element: JSONObject, elementName: String): String {
        val potentialProperties = listOf(
            "atomic mass" to "element_atomicmass",
            "boiling point" to "element_boiling_celsius",
            "melting point" to "element_melting_celsius",
            "density" to "element_density",
            "electron configuration" to "element_electron_config",
            "electronegativity" to "element_electronegativty",
            "common ion charge" to "element_ion_charge",
            "crystal structure" to "crystal_structure", // If available in your JSON
            "ionization energy" to "element_ionization_energy1"
        )
        
        // Find properties we haven't shared yet
        val availableNewFacts = potentialProperties.filter { !sharedProperties.contains(it.first) }
        
        return if (availableNewFacts.isNotEmpty()) {
            val (label, jsonKey) = availableNewFacts.random()
            sharedProperties.add(label)
            val value = element.optString(jsonKey, "")
            if (value.isNotEmpty() && value != "---") {
                AIPersonality.formatElementResponse(elementName, label, value)
            } else {
                // If the selected property is empty, try again recursively once
                provideNewInformation(element, elementName)
            }
        } else {
            // If everything is shared, give a fun fact instead
            "${AIPersonality.getEncouragement()} We've covered a lot! Did you know? ${AIPersonality.getRandomFact()}"
        }
    }
    
    /**
     * Handle general element queries
     */
    private fun handleElementQuery(query: String): String {
        return try {
            val lowerQuery = query.lowercase()
            
            // Try to find element by name (fuzzy matching)
            val element = findElementByQuery(lowerQuery)
            
            if (element != null) {
                val elementName = element.optString("element", "Element")
                // Delegate to the more detailed context handler once element is identified
                handleElementContextQuery(query, elementName)
            } else {
                AIPersonality.getNoDataResponse(query)
            }
        } catch (e: Exception) {
            AIPersonality.getNoDataResponse(query)
        }
    }
    
    /**
     * Find element by name or symbol in the data with fuzzy matching support.
     * Now supports finding an element mentioned within a larger sentence.
     */
    private fun findElementByQuery(query: String): JSONObject? {
        val lowerQuery = query.lowercase().trim()
        if (lowerQuery.isEmpty()) return null
        
        val data = elementData ?: return null
        
        // Split query into words to check for exact element names/symbols
        val queryWords = lowerQuery.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 1 }
        
        var bestMatch: JSONObject? = null
        var minDistance = Int.MAX_VALUE
        
        try {
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val element = data.optJSONObject(key) ?: continue
                val name = element.optString("element", "").lowercase()
                val symbol = element.optString("short", "").lowercase()
                
                // 1. Check if name or symbol is explicitly mentioned as a word in the query
                if (queryWords.contains(name) || queryWords.contains(symbol)) return element
                
                // 2. Exact match of the whole query
                if (name == lowerQuery || symbol == lowerQuery) return element
                
                // 3. Fuzzy match for each word in the query
                for (word in queryWords) {
                    if (word.length < 3) continue
                    val distance = levenshteinDistance(word, name)
                    val threshold = when {
                        name.length > 6 -> 2
                        name.length > 3 -> 1
                        else -> 0
                    }
                    
                    if (distance <= threshold && distance < minDistance) {
                        minDistance = distance
                        bestMatch = element
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        return bestMatch
    }

    /**
     * Check if message is a greeting
     */
    private fun isGeneralGreeting(message: String): Boolean {
        val greetings = listOf("hi", "hello", "hey", "start", "begin", "help", "what can you do")
        return hasKeyword(message, greetings)
    }
    
    /**
     * Check if message is asking for a fact
     */
    private fun isFactRequest(message: String): Boolean {
        val factKeywords = listOf("fact", "fun fact", "did you know", "interesting", "cool")
        return hasKeyword(message, factKeywords)
    }

    /**
     * Helper to check if query contains any of the keywords, with fuzzy matching for spelling mistakes
     */
    private fun hasKeyword(query: String, keywords: List<String>): Boolean {
        val lowerQuery = query.lowercase()
        // Direct contains check
        if (keywords.any { lowerQuery.contains(it) }) return true
        
        // Split query into words and check each against keywords
        val words = lowerQuery.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 3 }
        for (word in words) {
            for (keyword in keywords) {
                val threshold = when {
                    keyword.length > 8 -> 2
                    keyword.length > 3 -> 1
                    else -> 0
                }
                
                // Check against keyword or parts of multi-word keywords
                val keywordParts = keyword.split(" ")
                for (part in keywordParts) {
                    if (part.length > 3 && levenshteinDistance(word, part) <= threshold) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Calculate Levenshtein distance between two strings to handle spelling mistakes
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    minOf(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
