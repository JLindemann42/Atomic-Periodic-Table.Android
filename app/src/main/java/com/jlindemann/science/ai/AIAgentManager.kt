package com.jlindemann.science.ai

import android.content.Context
import com.jlindemann.science.R
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
    private var currentQuizAnswer: String? = null
    private var activeLanguage: String = "en"
    private var localizedContext: Context? = null
    private val learningManager: AILearningManager? by lazy { 
        context?.let { AILearningManager(it) } 
    }

    private val localizedElementMap = mutableMapOf<String, String>()

    fun getActiveLanguage(): String = activeLanguage

    suspend fun setLanguage(language: String) {
        activeLanguage = language
        updateLocalizedContext()
        withContext(Dispatchers.IO) {
            elementData = getElementDataByLanguage(language)
            // Reload map if language changes significantly? 
            // Actually, the map is cross-language, so we only need to load it once.
            if (localizedElementMap.isEmpty()) {
                loadCrossLanguageElementMap()
            }
        }
    }

    private fun updateLocalizedContext() {
        context?.let {
            val locale = java.util.Locale(activeLanguage)
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(it.resources.configuration)
            config.setLocale(locale)
            localizedContext = it.createConfigurationContext(config)
        }
    }

    private suspend fun loadCrossLanguageElementMap() {
        val ctx = context ?: return
        withContext(Dispatchers.IO) {
            val languages = listOf("af", "de", "en", "es", "fr", "hi", "it", "pt", "sv", "ur", "zh", "fil")
            for (lang in languages) {
                try {
                    val fileName = "elements_$lang.json"
                    val inputStream = ctx.assets.open(fileName)
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val data = JSONObject(jsonString)
                    val keys = data.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val element = data.optJSONObject(key)
                        val localizedName = element?.optString("element")?.lowercase()
                        if (localizedName != null) {
                            localizedElementMap[localizedName] = key
                        }
                    }
                } catch (e: Exception) {
                    // Skip if file doesn't exist or error
                }
            }
        }
    }
    
    /**
     * Initialize AI agent with element data
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                val ctx = context ?: return@withContext
                activeLanguage = ElementDataLoader.getAppLanguage(ctx)
                updateLocalizedContext()
                elementData = getElementDataByLanguage(activeLanguage)
                loadCrossLanguageElementMap()
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
            val ctx = context ?: return@withContext ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "Context lost",
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
            val lowerQuery = userMessage.lowercase().trim()

            // Handle active Quiz answer
            if (currentQuizAnswer != null && !isQuizQuery(lowerQuery)) {
                return@withContext ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = handleQuizAnswer(lowerQuery),
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            }

            // 0. Handle comparison
            var elementsInQuery = findMultipleElements(lowerQuery)
            
            // If user says "Compare with [Element]" and we have a current context
            if (elementsInQuery.size == 1 && (lowerQuery.contains("compare") || lowerQuery.contains("vs")) && currentElement != null) {
                val contextElementJson = elementData?.optJSONObject(currentElement!!.lowercase())
                if (contextElementJson != null) {
                    // Check if the one found is different from current
                    val foundName = elementsInQuery[0].optString("element", "").lowercase()
                    if (foundName != currentElement!!.lowercase()) {
                        elementsInQuery = listOf(contextElementJson, elementsInQuery[0])
                    }
                }
            }

            if (elementsInQuery.size >= 2) {
                return@withContext ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = handleComparison(elementsInQuery, lowerQuery),
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            }

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
            
            // Track element interest if found
            targetElement?.let { learningManager?.trackElementInterest(it) }

            val responseText = when {
                userMessage.isBlank() -> AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage)
                isQuizQuery(lowerQuery) -> handleQuizQuery(lowerQuery)
                isTrendsQuery(lowerQuery) -> handleTrendsQuery(lowerQuery)
                isMolarMassQuery(lowerQuery) -> handleMolarMassQuery(lowerQuery)
                isSuperlativeQuery(lowerQuery) -> handleSuperlativeQuery(lowerQuery)
                isFormulaQuery(lowerQuery) -> handleFormulaQuery(lowerQuery)
                isSeriesQuery(lowerQuery) -> handleSeriesQuery(lowerQuery)
                isBlockQuery(lowerQuery) -> handleBlockQuery(lowerQuery)
                targetElement != null -> {
                    currentElement = targetElement
                    handleElementContextQuery(userMessage, targetElement)
                }
                isGeneralGreeting(userMessage) -> learningManager?.getPersonalizedGreeting() ?: AIPersonality.getGreeting(ctx, activeLanguage)
                isFactRequest(userMessage) -> AIPersonality.getRandomFact(ctx, activeLanguage)
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
     * Find multiple elements mentioned in a query for comparison
     */
    private fun findMultipleElements(query: String): List<JSONObject> {
        val words = query.split(Regex("[^\\p{L}0-9]+")).filter { it.length >= 1 }
        val found = mutableListOf<JSONObject>()
        val seenKeys = mutableSetOf<String>()
        val data = elementData ?: return emptyList()

        for (word in words) {
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (seenKeys.contains(key)) continue
                
                val element = data.optJSONObject(key) ?: continue
                val name = element.optString("element", "").lowercase()
                val symbol = element.optString("short", "").lowercase()
                
                if (word == name || word == symbol) {
                    found.add(element)
                    seenKeys.add(key)
                    break
                }
            }
        }
        return found
    }

    private fun handleComparison(elements: List<JSONObject>, query: String): String {
        val lowerQuery = query.lowercase()
        val ctx = localizedContext ?: context!!
        
        // Support comparing up to 3 elements for now
        val compareList = elements.take(3)
        val names = compareList.map { it.optString("element", "Unknown") }
        
        // 1. Check for specific property comparison request
        val propertyResult = when {
            hasKeyword(lowerQuery, listOf("density", "dense", "densitet")) -> compareProperty(compareList, "element_density", ctx.getString(R.string.density_colon).replace(":",""), "g/cm³")
            hasKeyword(lowerQuery, listOf("boiling", "boil", "kokpunkt")) -> compareProperty(compareList, "element_boiling_celsius", ctx.getString(R.string.boiling_point_colon).replace(":",""), "°C")
            hasKeyword(lowerQuery, listOf("melting", "melt", "smältpunkt")) -> compareProperty(compareList, "element_melting_celsius", ctx.getString(R.string.melting_point_colon).replace(":",""), "°C")
            hasKeyword(lowerQuery, listOf("electronegativity", "negative", "elektronegativitet")) -> compareProperty(compareList, "element_electronegativty", ctx.getString(R.string.electronegativity_colon).replace(":",""), "")
            hasKeyword(lowerQuery, listOf("radius", "size", "radie", "storlek")) -> compareProperty(compareList, "element_atomic_radius_e", ctx.getString(R.string.atomic_radius_empirical_colon).replace(":",""), "pm")
            hasKeyword(lowerQuery, listOf("mass", "weight", "massa", "vikt")) -> compareProperty(compareList, "element_atomicmass", ctx.getString(R.string.atomic_mass_colon).replace(":",""), "u")
            else -> null
        }

        if (propertyResult != null) return propertyResult

        // 2. Default comprehensive comparison
        var comparison = ctx.getString(R.string.ai_comparing_title, names.joinToString(" vs ")) + "\n"
        
        val props = listOf(
            Triple(ctx.getString(R.string.atomic_number_label).replace(":",""), "element_atomic_number", ""),
            Triple(ctx.getString(R.string.atomic_mass_colon).replace(":",""), "element_atomicmass", ""),
            Triple(ctx.getString(R.string.type_label).replace(":",""), "element_type", ""),
            Triple(ctx.getString(R.string.phase_stp_colon).replace(":",""), "element_phase", ""),
            Triple(ctx.getString(R.string.electronegativity_colon).replace(":",""), "element_electronegativty", "")
        )

        for ((label, key, unit) in props) {
            val values = compareList.map { it.optString(key, "---").replace(unit, "").trim() }
            if (values.all { it == "---" || it.isEmpty() }) continue
            
            if (values.distinct().size == 1) {
                comparison += "\n• **$label:** All are ${values[0]}$unit."
            } else {
                comparison += "\n• **$label:** " + compareList.indices.joinToString(", ") { i ->
                    "${names[i]} is ${values[i]}$unit"
                } + "."
            }
        }
        
        return comparison
    }

    private fun compareProperty(elements: List<JSONObject>, jsonKey: String, label: String, unit: String): String {
        val names = elements.map { it.optString("element", "") }
        val values = elements.map { it.optString(jsonKey, "---").replace(unit, "").trim() }
        val ctx = localizedContext ?: context!!
        
        var response = ctx.getString(R.string.ai_comparing_title, label) + "\n"
        for (i in elements.indices) {
            response += "• ${names[i]}: ${values[i]}$unit\n"
        }
        
        // Try to find the "winner" if there are exactly 2 and they are numeric
        if (elements.size == 2) {
            try {
                val v1 = values[0].filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                val v2 = values[1].filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                
                if (v1 != null && v2 != null) {
                    val diff = Math.abs(v1 - v2)
                    
                    response += if (diff == 0.0) {
                        "\n" + ctx.getString(R.string.ai_same_value, label)
                    } else {
                        val winner = if (v1 > v2) names[0] else names[1]
                        val loser = if (v1 > v2) names[1] else names[0]
                        "\n" + ctx.getString(R.string.ai_higher_than, winner, label, loser)
                    }
                }
            } catch (e: Exception) { /* ignore non-numeric */ }
        }
        
        return response.trim()
    }

    private fun isTrendsQuery(query: String): Boolean {
        return query.contains("trend") || (query.contains("periodic table") || query.contains("periodiska systemet")) && (query.contains("how") || query.contains("change") || query.contains("hur") || query.contains("ändras"))
    }

    private fun handleTrendsQuery(query: String): String {
        val ctx = localizedContext ?: context!!
        return when {
            query.contains("electronegativity") || query.contains("elektronegativitet") || query.contains("elektronegativität") || query.contains("electronegatividad") || query.contains("électronégativité") -> ctx.getString(R.string.ai_trend_electronegativity)
            query.contains("radius") || query.contains("size") || query.contains("radie") || query.contains("storlek") || query.contains("größe") || query.contains("radio") || query.contains("tamaño") || query.contains("rayon") || query.contains("taille") -> ctx.getString(R.string.ai_trend_radius)
            query.contains("ionization") || query.contains("jonisering") || query.contains("ionisierung") || query.contains("ionización") || query.contains("ionisation") -> ctx.getString(R.string.ai_trend_ionization)
            query.contains("metallic") || query.contains("metallisk") || query.contains("metallisch") || query.contains("metálico") || query.contains("métallique") -> ctx.getString(R.string.ai_trend_metallic)
            else -> ctx.getString(R.string.ai_trend_general)
        }
    }

    private fun isMolarMassQuery(query: String): Boolean {
        return (query.contains("molar mass") || query.contains("molecular weight") || query.contains("molmassa")) && (query.contains("of") || query.contains("for") || query.contains("för") || query.contains("på"))
    }

    private fun handleMolarMassQuery(query: String): String {
        val ctx = localizedContext ?: context!!
        val formula = query.split("of ", "for ", "för ").last().trim().lowercase().replace(" ", "")
        
        if (isFormulaQuery(formula)) {
            val mass = when (formula) {
                "h2o" -> "18.015 g/mol"
                "co2" -> "44.009 g/mol"
                "nacl" -> "58.44 g/mol"
                "o2" -> "31.998 g/mol"
                "h2" -> "2.016 g/mol"
                "ch4" -> "16.04 g/mol"
                "nh3" -> "17.031 g/mol"
                "c6h12o6" -> "180.16 g/mol"
                "h2so4" -> "98.078 g/mol"
                "hcl" -> "36.46 g/mol"
                else -> null
            }
            if (mass != null) return ctx.getString(R.string.ai_molar_mass_of, formula.uppercase(), mass)
        }
        
        val element = findElementByQuery(formula)
        if (element != null) {
            val name = element.optString("element", "")
            val mass = element.optString("element_atomicmass", "").replace("(u)", "").trim()
            return ctx.getString(R.string.ai_molar_mass_of, name, "$mass g/mol")
        }

        return ctx.getString(R.string.ai_molar_mass_generic)
    }

    private fun isQuizQuery(query: String): Boolean {
        return query.contains("quiz") || query.contains("question") || query.contains("test") || query.contains("frågesport") || query.contains("tävling")
    }

    private fun handleQuizAnswer(query: String): String {
        val answer = currentQuizAnswer ?: return "I'm not in quiz mode right now."
        val ctx = localizedContext ?: context!!
        
        val userFoundElement = findElementByQuery(query)?.optString("element", "")?.lowercase()
        val isCorrect = query.contains(answer, ignoreCase = true) || userFoundElement == answer
        
        val response = if (isCorrect) {
            ctx.getString(R.string.ai_quiz_correct, answer.replaceFirstChar { it.uppercase() })
        } else {
            ctx.getString(R.string.ai_quiz_wrong, answer.replaceFirstChar { it.uppercase() })
        }
        currentQuizAnswer = null
        return response
    }

    private fun handleQuizQuery(query: String): String {
        val data = elementData ?: return "I can't start a quiz right now."
        val ctx = localizedContext ?: context!!
        
        if (currentQuizAnswer != null) {
            val words = query.split(" ")
            if (words.size <= 4) return handleQuizAnswer(query)
        }

        val keys = data.keys().asSequence().toList()
        val randomKey = keys.random()
        val element = data.optJSONObject(randomKey) ?: return "Oops!"
        
        val quizType = (0..2).random()
        currentQuizAnswer = element.optString("element", "").lowercase()
        
        return when (quizType) {
            0 -> ctx.getString(R.string.ai_quiz_start, element.optString("short", ""))
            1 -> ctx.getString(R.string.ai_quiz_atomic, element.optString("element_atomic_number", ""))
            else -> {
                val discoverer = element.optString("element_discovered_name", "---")
                val year = element.optString("element_year", "")
                if (discoverer == "---" || year == "---") {
                    currentQuizAnswer = null
                    handleQuizQuery("quiz")
                } else {
                    ctx.getString(R.string.ai_quiz_history, discoverer, year)
                }
            }
        }
    }

    private fun isSuperlativeQuery(query: String): Boolean {
        val keywords = listOf("most", "least", "highest", "lowest", "densest", "heaviest", "lightest", "biggest", "smallest", "hottest", "coldest", "tätaste", "tyngsta", "lättaste", "största", "minsta", "varmaste", "kallaste", "högsta", "lägsta")
        return keywords.any { query.contains(it) }
    }

    private fun handleSuperlativeQuery(query: String): String {
        val data = elementData ?: return "I can't check that right now."
        val ctx = localizedContext ?: context!!
        
        val (jsonKey, label, findMax) = when {
            query.contains("densest") || query.contains("most dense") || query.contains("tätaste") -> Triple("element_density", ctx.getString(R.string.density_colon).replace(":",""), true)
            query.contains("heaviest") || query.contains("highest mass") || query.contains("tyngsta") -> Triple("element_atomicmass", ctx.getString(R.string.atomic_mass_colon).replace(":",""), true)
            query.contains("lightest") || query.contains("lowest mass") || query.contains("lättaste") -> Triple("element_atomicmass", ctx.getString(R.string.atomic_mass_colon).replace(":",""), false)
            query.contains("highest boiling") || query.contains("hottest boiling") || query.contains("högsta kokpunkt") -> Triple("element_boiling_celsius", ctx.getString(R.string.boiling_point_colon).replace(":",""), true)
            query.contains("lowest boiling") || query.contains("kallaste kokpunkt") || query.contains("lägsta kokpunkt") -> Triple("element_boiling_celsius", ctx.getString(R.string.boiling_point_colon).replace(":",""), false)
            query.contains("most electronegative") || query.contains("högsta elektronegativitet") -> Triple("element_electronegativty", ctx.getString(R.string.electronegativity_colon).replace(":",""), true)
            else -> return ctx.getString(R.string.ai_superlative_generic)
        }

        var targetValue = if (findMax) Double.MIN_VALUE else Double.MAX_VALUE
        var targetElement = ""
        var targetValueStr = ""

        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val element = data.optJSONObject(key) ?: continue
            val name = element.optString("element", "")
            val valueStr = element.optString(jsonKey, "---").filter { it.isDigit() || it == '.' || it == '-' }
            val value = valueStr.toDoubleOrNull() ?: continue

            if (findMax) {
                if (value > targetValue) {
                    targetValue = value
                    targetElement = name
                    targetValueStr = element.optString(jsonKey, "")
                }
            } else {
                if (value < targetValue) {
                    targetValue = value
                    targetElement = name
                    targetValueStr = element.optString(jsonKey, "")
                }
            }
        }

        return if (targetElement.isNotEmpty()) {
            val adj = if (findMax) ctx.getString(R.string.highest) else ctx.getString(R.string.lowest)
            ctx.getString(R.string.ai_superlative_result, adj, label, targetElement, targetValueStr)
        } else {
            ctx.getString(R.string.ai_superlative_no_calculate)
        }
    }

    private fun isFormulaQuery(query: String): Boolean {
        // Simple check for common formulas
        val commonFormulas = listOf("h2o", "co2", "nacl", "o2", "h2", "ch4", "nh3", "c6h12o6", "h2so4", "hcl")
        return commonFormulas.any { query.contains(it) }
    }

    private fun handleFormulaQuery(query: String): String {
        val ctx = localizedContext ?: context!!
        return when {
            query.contains("h2o") -> ctx.getString(R.string.ai_formula_h2o)
            query.contains("co2") -> ctx.getString(R.string.ai_formula_co2)
            query.contains("nacl") -> ctx.getString(R.string.ai_formula_nacl)
            query.contains("o2") -> ctx.getString(R.string.ai_formula_o2)
            query.contains("ch4") -> ctx.getString(R.string.ai_formula_ch4)
            query.contains("nh3") -> ctx.getString(R.string.ai_formula_nh3)
            query.contains("c6h12o6") -> ctx.getString(R.string.ai_formula_c6h12o6)
            query.contains("h2so4") -> ctx.getString(R.string.ai_formula_h2so4)
            query.contains("hcl") -> ctx.getString(R.string.ai_formula_hcl)
            else -> ctx.getString(R.string.ai_formula_generic)
        }
    }

    private fun isSeriesQuery(query: String): Boolean {
        val series = listOf("alkali", "halogen", "noble gas", "lanthanide", "actinide", "metalloid", "transition metal", "ädelgas", "lantanid", "aktinid", "halvmetall", "övergångsmetall")
        return series.any { query.contains(it) }
    }

    private fun handleSeriesQuery(query: String): String {
        val ctx = localizedContext ?: context!!
        return when {
            query.contains("alkali") -> ctx.getString(R.string.ai_series_alkali)
            query.contains("halogen") -> ctx.getString(R.string.ai_series_halogen)
            query.contains("noble gas") || query.contains("ädelgas") -> ctx.getString(R.string.ai_series_noble_gas)
            query.contains("lanthanide") || query.contains("lantanid") -> ctx.getString(R.string.ai_series_lanthanide)
            query.contains("actinide") || query.contains("aktinid") -> ctx.getString(R.string.ai_series_actinide)
            query.contains("transition metal") || query.contains("övergångsmetall") -> ctx.getString(R.string.ai_series_transition)
            query.contains("metalloid") || query.contains("halvmetall") -> ctx.getString(R.string.ai_series_metalloid)
            else -> ctx.getString(R.string.ai_series_generic)
        }
    }

    private fun isBlockQuery(query: String): Boolean {
        return query.contains("block") && (query.contains("s-") || query.contains("p-") || query.contains("d-") || query.contains("f-"))
    }

    private fun handleBlockQuery(query: String): String {
        val ctx = localizedContext ?: context!!
        val block = when {
            query.contains("s-") -> "s-block"
            query.contains("p-") -> "p-block"
            query.contains("d-") -> "d-block"
            query.contains("f-") -> "f-block"
            else -> return ctx.getString(R.string.ai_block_generic)
        }
        
        return when (block) {
            "s-block" -> ctx.getString(R.string.ai_block_s)
            "p-block" -> ctx.getString(R.string.ai_block_p)
            "d-block" -> ctx.getString(R.string.ai_block_d)
            "f-block" -> ctx.getString(R.string.ai_block_f)
            else -> ""
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
    
    private fun handleElementContextQuery(query: String, elementName: String): String {
        val ctx = localizedContext ?: context!!
        return try {
            val element = elementData?.optJSONObject(elementName.lowercase()) ?: return AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
            val lowerQuery = query.lowercase()
            
            val response = when {
                // Basic properties
                hasKeyword(lowerQuery, listOf("atomic number", "proton", "number", "atomnummer", "atomnummeret", "ordnungszahl", "número atómico", "numéro atomique", "número", "numbro")) || matchLabel(lowerQuery, R.string.atomic_number_label) -> {
                    val prop = "atomic number"
                    val label = ctx.getString(R.string.atomic_number_label).replace(":", "").trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val atomicNum = element.optString("element_atomic_number", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, atomicNum, isRepeat)
                }
                (hasKeyword(lowerQuery, listOf("mass", "weight", "massa", "vikt", "masse", "masa", "poids")) && !lowerQuery.contains("molar") && !lowerQuery.contains("mol")) || matchLabel(lowerQuery, R.string.atomic_mass_colon) -> {
                    val prop = "atomic mass"
                    val label = ctx.getString(R.string.atomic_mass_colon).replace(":", "").trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val mass = element.optString("element_atomicmass", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, mass, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("symbol", "short", "förkortning", "symbole", "símbolo", "sign")) || lowerQuery.contains(ctx.getString(R.string.element_symbols).lowercase()) -> {
                    val prop = "symbol"
                    val label = ctx.getString(R.string.element_symbols).trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val symbol = element.optString("short", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, symbol, isRepeat)
                }
                
                // Classification
                hasKeyword(lowerQuery, listOf("type", "category", "class", "typ", "kategori", "klass", "art", "kategorie", "klasse", "tipo", "categoría", "clase", "catégorie")) -> {
                    val prop = "category"
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val type = element.optString("element_type", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, prop, type, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("group", "column", "grupp", "kolumn", "gruppe", "spalte", "grupo", "columna", "groupe", "colonne")) || matchLabel(lowerQuery, R.string.element_groups) -> {
                    val prop = "group"
                    val label = ctx.getString(R.string.element_groups).trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val group = element.optString("element_group", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, group, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("period", "row", "rad", "periode", "zeile", "periodo", "fila", "rangée")) -> {
                    val prop = "period"
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val period = element.optString("element_period", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, prop, period, isRepeat)
                }
                
                // Appearance and physical properties
                hasKeyword(lowerQuery, listOf("appear", "look", "color", "colour", "physical", "visual", "utseende", "färg", "ser ut", "aussehen", "farbe", "apariencia", "apparence", "couleur")) || matchLabel(lowerQuery, R.string.appearance_colon) -> {
                    val prop = "appearance"
                    val label = ctx.getString(R.string.appearance_colon).replace(":", "").trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val appearance = element.optString("element_appearance", "")
                    if (appearance.isNotEmpty()) {
                        if (isRepeat) ctx.getString(R.string.ai_recap_is, elementName, label, appearance) 
                        else "${AIPersonality.getEncouragement(ctx, activeLanguage)} ${ctx.getString(R.string.ai_appears_as, elementName, appearance)}"
                    } else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }
                
                // Electron configuration and structure
                hasKeyword(lowerQuery, listOf("electron", "shell", "orbital", "config", "elektron", "schale", "électron", "couche")) || matchLabel(lowerQuery, R.string.electron_configuration_colon) -> {
                    val prop = "electron configuration"
                    val label = ctx.getString(R.string.electron_configuration_colon).replace(":", "").trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = true)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val config = element.optString("element_electron_config", "")
                    val shells = element.optString("element_shells_electrons", "")
                    val shellsLabel = ctx.getString(R.string.electron_shell_colon).replace(":", "").trim()
                    if (lowerQuery.contains("shell") && shells.isNotEmpty()) {
                        AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, shellsLabel, shells, isRepeat)
                    } else {
                        AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, config, isRepeat)
                    }
                }
                
                // Temperature properties
                hasKeyword(lowerQuery, listOf("boiling", "boil", "kokpunkt", "kokar", "siedepunkt", "kocht", "ebullición", "hierve", "ébullition", "bout")) || matchLabel(lowerQuery, R.string.boiling_point_colon) -> {
                    val prop = "boiling point"
                    val label = ctx.getString(R.string.boiling_point_colon).replace(":", "").trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val boiling = element.optString("element_boiling_celsius", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, if (boiling.isNotEmpty()) "$boiling°C" else "", isRepeat)
                }
                hasKeyword(lowerQuery, listOf("melting", "melt", "smältpunkt", "smälter", "schmelzpunkt", "schmilzt", "fusión", "derrite", "fusion", "fond")) || matchLabel(lowerQuery, R.string.melting_point_colon) -> {
                    val prop = "melting point"
                    val label = ctx.getString(R.string.melting_point_colon).replace(":", "").trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val melting = element.optString("element_melting_celsius", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, if (melting.isNotEmpty()) "$melting°C" else "", isRepeat)
                }
                
                // Density and volume
                hasKeyword(lowerQuery, listOf("density", "dense", "densitet", "täthet", "dichte", "densidad", "densité")) || matchLabel(lowerQuery, R.string.density_colon) -> {
                    val prop = "density"
                    val label = ctx.getString(R.string.density_colon).replace(":", "").trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val density = element.optString("element_density", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, density, isRepeat)
                }
                
                // Electrochemistry
                hasKeyword(lowerQuery, listOf("ion", "charge", "valency", "jon", "laddning", "valens", "ladung", "ión", "carga", "valencia")) || matchLabel(lowerQuery, R.string.ion_charge_colon) -> {
                    val prop = "oxidation state"
                    val label = ctx.getString(R.string.ion_charge_colon).replace(":", "").trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = true)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val charge = element.optString("element_ion_charge", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, charge, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("electronegativity", "negative", "elektronegativitet", "elektronegativität", "electronegatividad", "électronégativité")) || matchLabel(lowerQuery, R.string.electronegativity_colon) -> {
                    val prop = "electronegativity"
                    learningManager?.trackPropertyInterest(prop, isTechnical = true)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val electronegativity = element.optString("element_electronegativty", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, prop, electronegativity, isRepeat)
                }
                
                // Discovery and history
                hasKeyword(lowerQuery, listOf("discover", "found", "who", "when", "year", "history", "upptäck", "hitta", "vem", "när", "år", "historia", "entdeckung", "gefunden", "wer", "wann", "jahr", "geschicte", "descubrimiento", "encontrado", "quién", "cuándo", "découverte", "trouvé", "qui", "quand")) || matchLabel(lowerQuery, R.string.year_discovered_colon) -> {
                    val prop = "history"
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val discoverer = element.optString("element_discovered_name", "")
                    val year = element.optString("element_year", "")
                    val discovery = when {
                        discoverer.isNotEmpty() && year.isNotEmpty() -> 
                            ctx.getString(R.string.ai_discovered_by, elementName, discoverer, year)
                        year.isNotEmpty() -> 
                            ctx.getString(R.string.ai_discovered_in, elementName, year)
                        else -> ""
                    }
                    if (discovery.isNotEmpty()) {
                        val intro = if (isRepeat) "Just to recap," else AIPersonality.getEncouragement(ctx, activeLanguage)
                        "$intro $discovery"
                    } else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }
                
                // Radioactivity
                hasKeyword(lowerQuery, listOf("radioactive", "radiation", "decay", "radioaktiv", "strålning", "sönderfall", "strahlung", "zerfall", "radiactivo", "radiación", "desintegración", "radioactif", "rayonnement", "désintégration")) || matchLabel(lowerQuery, R.string.radioactive_colon) -> {
                    val radioactive = element.optString("radioactive", "")
                    if (radioactive.isNotEmpty()) {
                        ctx.getString(R.string.ai_radioactive_yes, elementName, radioactive)
                    } else {
                        ctx.getString(R.string.ai_radioactive_no, elementName)
                    }
                }
                
                // Phase and state
                hasKeyword(lowerQuery, listOf("phase", "state", "solid", "liquid", "gas", "fas", "tillstånd", "fast", "flytande", "gasform", "zustand", "fest", "flüssig", "estado", "sólido", "líquido", "gaseoso", "état", "solide", "liquide", "gazeux")) || matchLabel(lowerQuery, R.string.phase_stp_colon) -> {
                    val prop = "phase"
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val phase = element.optString("element_phase", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, prop, phase, isRepeat)
                }

                // Abundance, Usage, and General Info (from description)
                hasKeyword(lowerQuery, listOf("use", "used", "find", "found", "abundance", "rare", "common", "where", "application", "användning", "används", "förekomst", "sällsynt", "vanlig", "var", "verwendung", "vorkommen", "selten", "häufig", "wo", "uso", "abundancia", "raro", "común", "dónde", "utilisation", "abondance", "où")) -> {
                    val description = element.optString("description", "")
                    if (description.isNotEmpty()) {
                        "${AIPersonality.getEncouragement(ctx, activeLanguage)} $description"
                    } else {
                        AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                    }
                }

                // Wikipedia and more info
                hasKeyword(lowerQuery, listOf("wiki", "more info", "link", "read more", "article", "länk", "läs mer", "artikel")) -> {
                    val link = element.optString("wikilink", "")
                    if (link.isNotEmpty()) {
                        "${AIPersonality.getEncouragement(ctx, activeLanguage)} You can read more about $elementName here: $link"
                    } else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }

                // Identifiers
                hasKeyword(lowerQuery, listOf("cas", "eg", "number", "id", "identification", "nummer")) -> {
                    val cas = element.optString("cas_number", "")
                    val eg = element.optString("eg_number", "")
                    
                    val title = ctx.getString(R.string.ai_identifiers_for, elementName)
                    val casLabel = ctx.getString(R.string.cas_number).trim()
                    val egLabel = ctx.getString(R.string.eg_number).trim()
                    
                    var idInfo = "$title"
                    if (cas.isNotEmpty() && cas != "---") idInfo += "\n• $casLabel $cas"
                    if (eg.isNotEmpty() && eg != "---") idInfo += "\n• $egLabel $eg"
                    
                    if (idInfo.length > title.length + 5) idInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }
                
                // Block
                hasKeyword(lowerQuery, listOf("block")) -> {
                    val block = element.optString("element_block", "")
                    val blockLabel = ctx.getString(R.string.block).trim()
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, blockLabel, block)
                }

                // Oxidation States
                hasKeyword(lowerQuery, listOf("oxidation", "valence", "outer", "bond")) -> {
                    val pos = element.optString("oxidation_state_pos", "")
                    val neg = element.optString("oxidation_state_neg", "")
                    val group = element.optString("element_group", "")
                    
                    var valenceInfo = when {
                        group == "Alkali Metal" -> "As an Alkali Metal, it has **1 valence electron** in its outer shell."
                        group == "Alkaline Earth Metal" -> "As an Alkaline Earth Metal, it has **2 valence electrons**."
                        group == "Halogen" -> "As a Halogen, it has **7 valence electrons** and is very eager to gain one more!"
                        group == "Noble Gas" -> {
                            if (elementName.lowercase() == "helium") "Helium has **2 valence electrons**, completing its only shell."
                            else "It has a full outer shell of **8 valence electrons**, making it very stable."
                        }
                        else -> ""
                    }

                    var stateInfo = if (valenceInfo.isNotEmpty()) "$valenceInfo\n\n" else ""
                    stateInfo += "$elementName has several possible oxidation states."
                    if (pos.isNotEmpty()) stateInfo += "\n• Positive: $pos"
                    if (neg.isNotEmpty()) stateInfo += "\n• Negative: $neg"
                    
                    val config = element.optString("element_electron_config", "")
                    if (config.isNotEmpty()) stateInfo += "\n\nIts electron configuration is $config."
                    
                    stateInfo
                }

                // Isotopes
                hasKeyword(lowerQuery, listOf("isotope", "decay", "half-life", "stable")) -> {
                    val iso1 = element.optString("iso_1", "")
                    val half1 = element.optString("iso_half_1", "")
                    val type1 = element.optString("decay_type_1", "")
                    
                    if (iso1.isNotEmpty() && iso1 != "---") {
                        var isoResponse = "Common isotopes for $elementName include $iso1."
                        if (half1.isNotEmpty() && half1 != "---") isoResponse += " It has a half-life of $half1 and decays via $type1."
                        
                        val iso2 = element.optString("iso_2", "")
                        if (iso2.isNotEmpty() && iso2 != "---") isoResponse += " Another isotope is $iso2."
                        
                        isoResponse
                    } else {
                        "$elementName has various isotopes. You can check the detailed isotope table for a full list!"
                    }
                }

                // Radius properties
                hasKeyword(lowerQuery, listOf("radius", "size", "big", "small")) -> {
                    val prop = "atomic radius"
                    val valE = element.optString("element_atomic_radius_e", "")
                    val valC = element.optString("element_covalent_radius", "")
                    val valV = element.optString("element_van_der_waals", "")
                    
                    var radiusInfo = "Here are the radius details for $elementName:"
                    if (valE.isNotEmpty() && valE != "---") radiusInfo += "\n• Empirical Atomic Radius: $valE"
                    if (valC.isNotEmpty() && valC != "---") radiusInfo += "\n• Covalent Radius: $valC"
                    if (valV.isNotEmpty() && valV != "---") radiusInfo += "\n• Van der Waals Radius: $valV"
                    
                    if (radiusInfo.length > 50) radiusInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }

                // Thermal and Heat
                hasKeyword(lowerQuery, listOf("heat", "thermal", "conductivity", "fusion", "vaporization", "specific")) -> {
                    val sh = element.optString("element_specific_heat_capacity", "")
                    val fh = element.optString("element_fusion_heat", "")
                    val vh = element.optString("element_vaporization_heat", "")
                    
                    var heatInfo = "Thermal properties of $elementName:"
                    if (sh.isNotEmpty() && sh != "---") heatInfo += "\n• Specific Heat Capacity: $sh"
                    if (fh.isNotEmpty() && fh != "---") heatInfo += "\n• Heat of Fusion: $fh"
                    if (vh.isNotEmpty() && vh != "---") heatInfo += "\n• Heat of Vaporization: $vh"
                    
                    if (heatInfo.length > 40) heatInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }

                // Electrical and Magnetic
                hasKeyword(lowerQuery, listOf("conductor", "magnetic", "resistivity", "electricity", "superconducting")) -> {
                    val et = element.optString("electrical_type", "")
                    val mt = element.optString("magnetic_type", "")
                    val sp = element.optString("superconducting_point", "")
                    val res = element.optString("resistivity", "")
                    
                    var elecInfo = "Electrical & Magnetic properties of $elementName:"
                    if (et.isNotEmpty() && et != "---") elecInfo += "\n• Electrical Type: $et"
                    if (mt.isNotEmpty() && mt != "---") elecInfo += "\n• Magnetic Type: $mt"
                    if (sp.isNotEmpty() && sp != "---") elecInfo += "\n• Superconducting Point: $sp K"
                    if (res.isNotEmpty() && res != "---") elecInfo += "\n• Resistivity: $res"
                    
                    if (elecInfo.length > 50) elecInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }

                // Identifiers
                hasKeyword(lowerQuery, listOf("cas", "eg", "number", "id", "identification")) -> {
                    val cas = element.optString("cas_number", "")
                    val eg = element.optString("eg_number", "")
                    
                    var idInfo = "Identifiers for $elementName:"
                    if (cas.isNotEmpty() && cas != "---") idInfo += "\n• CAS Number: $cas"
                    if (eg.isNotEmpty() && eg != "---") idInfo += "\n• EG Number: $eg"
                    
                    if (idInfo.length > 30) idInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }
                
                // Block
                hasKeyword(lowerQuery, listOf("block")) -> {
                    val block = element.optString("element_block", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, "block", block)
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
                            AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, "atomic number", atomicNum, sharedProperties.contains("atomic number")).also { sharedProperties.add("atomic number") }
                        }
                        hasKeyword(lowerQuery, listOf("mass", "weight")) -> {
                            val mass = element.optString("element_atomicmass", "")
                            AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, "atomic mass", mass, sharedProperties.contains("atomic mass")).also { sharedProperties.add("atomic mass") }
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
            AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
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
        val ctx = localizedContext ?: context!!

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
                    ctx.getString(R.string.ai_discovered_by, elementName, discoverer, year)
                year.isNotEmpty() ->
                    ctx.getString(R.string.ai_discovered_in, elementName, year)
                discoverer.isNotEmpty() -> {
                    val label = ctx.getString(R.string.discovered_by_colon).replace(":", "").trim()
                    "$elementName $label $discoverer."
                }
                else -> ""
            }

            var response = AIPersonality.formatElementOverview(
                ctx,
                activeLanguage,
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

            // AI learns to provide more detail if user shows technical interest
            if (learningManager?.isTechnicalPreferred() == true) {
                val weight = element.optString("element_atomicmass", "")
                if (weight.isNotEmpty() && weight != "---") {
                    response += ctx.getString(R.string.ai_technical_mass, weight)
                }
                val configuration = element.optString("element_electron_config", "")
                if (configuration.isNotEmpty() && configuration != "---") {
                    response += ctx.getString(R.string.ai_technical_config, configuration)
                }
            }
            response
        } else {
            // We've already shared the overview, give a lighter summary or a new fact
            val summaryData = mutableMapOf<String, String>()
            if (symbol.isNotEmpty()) summaryData["symbol"] = symbol
            if (atomicNum.isNotEmpty()) summaryData["atomic number"] = atomicNum
            if (type.isNotEmpty()) summaryData["category"] = type

            if (summaryData.size > 1) {
                AIPersonality.formatComprehensiveResponse(ctx, activeLanguage, elementName, summaryData)
            } else if (description.isNotEmpty()) {
                "${AIPersonality.getEncouragement(ctx, activeLanguage)} $description"
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
            "ionization energy" to "element_ionization_energy1",
            "magnetic type" to "magnetic_type",
            "electrical type" to "electrical_type",
            "block" to "element_block",
            "protons" to "element_protons",
            "neutrons" to "element_neutron_common",
            "electrons" to "element_electrons"
        )
        
        // Find properties we haven't shared yet
        val availableNewFacts = potentialProperties.filter { !sharedProperties.contains(it.first) }
        
        return if (availableNewFacts.isNotEmpty()) {
            val (label, jsonKey) = availableNewFacts.random()
            sharedProperties.add(label)
            val value = element.optString(jsonKey, "")
            if (value.isNotEmpty() && value != "---") {
                AIPersonality.formatElementResponse(context!!, activeLanguage, elementName, label, value)
            } else {
                // If the selected property is empty, try again recursively once
                provideNewInformation(element, elementName)
            }
        } else {
            // If everything is shared, give a fun fact instead
            "${AIPersonality.getEncouragement(context!!, activeLanguage)} We've covered a lot! Did you know? ${AIPersonality.getRandomFact(context!!, activeLanguage)}"
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
                AIPersonality.getNoDataResponse(context!!, activeLanguage, query)
            }
        } catch (e: Exception) {
            AIPersonality.getNoDataResponse(context!!, activeLanguage, query)
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

        // 1. Check cross-language map first for better translation support
        val queryWords = lowerQuery.split(Regex("[^\\p{L}0-9]+")).filter { it.length >= 1 }
            .map { it.removeSuffix("s") }
        
        for (word in queryWords) {
            val englishKey = localizedElementMap[word]
            if (englishKey != null) {
                val element = data.optJSONObject(englishKey)
                if (element != null) return element
            }
        }
        
        // 2. Original fuzzy matching logic as fallback
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
                
                // Special case for common nicknames or variations
                if (lowerQuery.contains(name) || (symbol.length > 1 && lowerQuery.contains(symbol))) {
                    // But check if it's a whole word match to avoid "in" matching "Indium"
                    if (lowerQuery.contains("\\b$name\\b".toRegex()) || lowerQuery.contains("\\b$symbol\\b".toRegex())) {
                        return element
                    }
                }

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
        val greetings = listOf("hi", "hello", "hey", "start", "begin", "help", "what can you do", "hej", "hallå", "starta", "hjälp", "vad kan du göra")
        return hasKeyword(message, greetings)
    }
    
    /**
     * Check if message is asking for a fact
     */
    private fun isFactRequest(message: String): Boolean {
        val factKeywords = listOf("fact", "fun fact", "did you know", "interesting", "cool", "fakta", "kul fakta", "visste du", "intressant", "häftigt")
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
        val words = lowerQuery.split(Regex("[^\\p{L}0-9]+")).filter { it.length > 3 }
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

    private fun matchLabel(query: String, resourceId: Int): Boolean {
        val ctx = localizedContext ?: context ?: return false
        val label = ctx.getString(resourceId).lowercase().replace(":", "").trim()
        return query.contains(label)
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
