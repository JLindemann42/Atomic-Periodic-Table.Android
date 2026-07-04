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
    private val rateLimiter: AIRateLimiter? by lazy {
        context?.let { AIRateLimiter(it) }
    }
    private val learningManager: AILearningManager? by lazy { 
        context?.let { AILearningManager(it) } 
    }
    private var molarMassCalculator: MolarMassCalculator? = null

    private val localizedElementMap = mutableMapOf<String, String>()

    fun getActiveLanguage(): String = activeLanguage

    suspend fun setLanguage(language: String) {
        activeLanguage = language
        updateLocalizedContext()
        withContext(Dispatchers.IO) {
            elementData = getElementDataByLanguage(language)
            molarMassCalculator = MolarMassCalculator(elementData)
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
                molarMassCalculator = MolarMassCalculator(elementData)
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
     * Reset the conversation context
     */
    fun clearConversation() {
        conversationHistory.clear()
        currentElement = null
        sharedProperties.clear()
        currentQuizAnswer = null
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
        contextElement: String? = null,
    ): ChatMessage {
        return withContext(Dispatchers.Default) {
            val ctx = context ?: return@withContext ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "Context lost",
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )

            // Check Rate Limit
            if (rateLimiter?.canSendMessage() == false) {
                return@withContext ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "You've reached your daily limit of ${rateLimiter?.getDailyLimit()} messages. Upgrade to PRO or PRO+ for more!",
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // Increment message count
            rateLimiter?.incrementMessageCount()

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
                val text = handleComparison(elementsInQuery, lowerQuery)
                return@withContext ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = text,
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

            // 3. Handle complex / multi-part queries
            val responseParts = mutableListOf<String>()

            when {
                userMessage.isBlank() -> responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                isQuizQuery(lowerQuery) -> responseParts.add(handleQuizQuery(lowerQuery))
                isTrendsQuery(lowerQuery) -> responseParts.add(handleTrendsQuery(lowerQuery))
                isMolarMassQuery(lowerQuery) -> responseParts.add(handleMolarMassQuery(userMessage))
                isSuperlativeQuery(lowerQuery) -> responseParts.add(handleSuperlativeQuery(lowerQuery))
                isSafetyQuery(lowerQuery) -> {
                    val element = targetElement ?: inferElementFromContext(userMessage)
                    if (element != null) responseParts.add(handleSafetyQuery(element))
                    else responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                }
                isFormulaQuery(userMessage) -> responseParts.add(handleFormulaQuery(userMessage))
                targetElement != null -> {
                    currentElement = targetElement
                    responseParts.add(handleElementContextQuery(userMessage, targetElement))
                }
                isSeriesQuery(lowerQuery) -> responseParts.add(handleSeriesQuery(lowerQuery))
                isBlockQuery(lowerQuery) -> responseParts.add(handleBlockQuery(lowerQuery))
                isGeneralGreeting(userMessage) -> responseParts.add(learningManager?.getPersonalizedGreeting() ?: AIPersonality.getGreeting(ctx, activeLanguage))
                isFactRequest(userMessage) -> responseParts.add(AIPersonality.getRandomFact(ctx, activeLanguage))
                else -> responseParts.add(handleElementQuery(userMessage))
            }

            // Check if there's a second part in the query (e.g. "and also tell me its mass")
            if (targetElement != null && (lowerQuery.contains("and ") || lowerQuery.contains("och ") || lowerQuery.contains("also ") || lowerQuery.contains("också "))) {
                // Look for properties mentioned after the conjunction
                val secondPart = lowerQuery.split("and ", "och ", "also ", "också ").last()
                if (secondPart.length > 3) {
                    val additionalInfo = handleElementContextQuery(secondPart, targetElement)
                    // Only add if it's not the same response and not a "no data" response
                    if (additionalInfo != responseParts.firstOrNull() && !additionalInfo.contains(ctx.getString(R.string.ai_no_data).take(10))) {
                        responseParts.add(additionalInfo)
                    }
                }
            }
            
            ChatMessage(
                id = UUID.randomUUID().toString(),
                text = responseParts.distinct().joinToString("\n\n"),
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Find multiple elements mentioned in a query for comparison
     */
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
        
        // Detect "which is [superlative]" vs "compare [element] and [element]"
        val findExtremum = lowerQuery.contains("which") || lowerQuery.contains("who") || lowerQuery.contains("vilken") || lowerQuery.contains("vem")
        val findDifference = lowerQuery.contains("diff") || lowerQuery.contains("skillnad") || lowerQuery.contains("gap")
        
        // 1. Check for specific property comparison request
        val propertyResult = when {
            hasKeyword(lowerQuery, listOf("density", "dense", "densitet", "tätare", "heavy", "light")) -> 
                compareProperty(compareList, "element_density", ctx.getString(R.string.density_colon).replace(":",""), "g/cm³", findExtremum, findMax = !lowerQuery.contains("light"), findDiff = findDifference)
            hasKeyword(lowerQuery, listOf("boiling", "boil", "kokpunkt")) -> 
                compareProperty(compareList, "element_boiling_celsius", ctx.getString(R.string.boiling_point_colon).replace(":",""), "°C", findExtremum, findMax = true, findDiff = findDifference)
            hasKeyword(lowerQuery, listOf("melting", "melt", "smältpunkt")) -> 
                compareProperty(compareList, "element_melting_celsius", ctx.getString(R.string.melting_point_colon).replace(":",""), "°C", findExtremum, findMax = true, findDiff = findDifference)
            hasKeyword(lowerQuery, listOf("electronegativity", "negative", "elektronegativitet")) -> 
                compareProperty(compareList, "element_electronegativty", ctx.getString(R.string.electronegativity_colon).replace(":",""), "", findExtremum, findMax = true, findDiff = findDifference)
            hasKeyword(lowerQuery, listOf("radius", "size", "radie", "storlek", "större", "mindre")) -> 
                compareProperty(compareList, "element_atomic_radius_e", ctx.getString(R.string.atomic_radius_empirical_colon).replace(":",""), "pm", findExtremum, findMax = lowerQuery.contains("big") || lowerQuery.contains("större") || lowerQuery.contains("large"), findDiff = findDifference)
            hasKeyword(lowerQuery, listOf("mass", "weight", "massa", "vikt", "tyngre", "lättare")) -> 
                compareProperty(compareList, "element_atomicmass", ctx.getString(R.string.atomic_mass_colon).replace(":",""), "u", findExtremum, findMax = !lowerQuery.contains("light") && !lowerQuery.contains("lättare"), findDiff = findDifference)
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

    private fun compareProperty(elements: List<JSONObject>, jsonKey: String, label: String, unit: String, findExtremum: Boolean = false, findMax: Boolean = true, findDiff: Boolean = false): String {
        val names = elements.map { it.optString("element", "") }
        val values = elements.map { it.optString(jsonKey, "---").replace(unit, "").trim() }
        val ctx = localizedContext ?: context!!
        
        var response = ctx.getString(R.string.ai_comparing_title, label) + "\n"
        for (i in elements.indices) {
            response += "• ${names[i]}: ${values[i]}$unit\n"
        }
        
        // Try to find the "winner" if there are numeric values
        try {
            val numericValues = values.map { it.filter { c -> c.isDigit() || c == '.' || c == '-' }.toDoubleOrNull() }
            
            if (numericValues.filterNotNull().size >= 2) {
                val bestIdx = if (findMax) {
                    numericValues.indices.filter { numericValues[it] != null }.maxByOrNull { numericValues[it]!! }
                } else {
                    numericValues.indices.filter { numericValues[it] != null }.minByOrNull { numericValues[it]!! }
                }
                
                if (bestIdx != null) {
                    val bestName = names[bestIdx]
                    
                    if (findExtremum) {
                        return if (findMax) ctx.getString(R.string.ai_higher_than, bestName, label, "the others")
                        else ctx.getString(R.string.ai_lower_than, bestName, label, "the others")
                    }
                    
                    if (elements.size == 2) {
                        val otherIdx = if (bestIdx == 0) 1 else 0
                        val v1 = numericValues[bestIdx] ?: 0.0
                        val v2 = numericValues[otherIdx] ?: 0.0
                        val diff = Math.abs(v1 - v2)
                        
                        if (diff == 0.0) {
                            response += "\n" + ctx.getString(R.string.ai_same_value, label)
                        } else {
                            if (findDiff) {
                                val formattedDiff = String.format(java.util.Locale.US, "%.3f", diff)
                                response += "\nThere is a difference of **$formattedDiff$unit** between them."
                            } else {
                                response += "\n" + (if (findMax) ctx.getString(R.string.ai_higher_than, bestName, label, names[otherIdx])
                                else ctx.getString(R.string.ai_lower_than, bestName, label, names[otherIdx]))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { /* ignore non-numeric */ }
        
        return response.trim()
    }

    private fun isTrendsQuery(query: String): Boolean {
        return query.contains("trend") || (query.contains("periodic table") || query.contains("periodiska systemet")) && (query.contains("how") || query.contains("change") || query.contains("hur") || query.contains("ändras")) || query.contains("periodiska trender")
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
        val isProPlus = rateLimiter?.isProPlus() ?: false
        
        // Extract the target (formula or name) after keywords while preserving casing
        val lower = query.lowercase()
        val keywords = listOf("of ", "for ", "för ", "på ", "på:")
        var targetIndex = -1
        for (kw in keywords) {
            val idx = lower.indexOf(kw)
            if (idx != -1) {
                targetIndex = idx + kw.length
                break
            }
        }
        
        val target = if (targetIndex != -1) query.substring(targetIndex).trim().replace("?", "") else query.trim()
        
        // 1. Try to find if the target is a specific element first (fuzzy matching handles case)
        val element = findElementByQuery(target)
        if (element != null) {
            val foundSymbol = element.optString("short", "")
            val foundName = element.optString("element", "").lowercase()
            
            // If target matches found symbol EXACTLY in casing, or matches name
            // This prevents "NH" from matching "Nh" (Nihonium) as a single element
            if (target == foundSymbol || target.lowercase() == foundName) {
                val name = element.optString("element", "")
                val massStr = element.optString("element_atomicmass", "").replace("(u)", "").trim()
                val mass = massStr.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                if (mass != null) {
                    val formattedMass = String.format(java.util.Locale.US, "%.3f", mass)
                    return ctx.getString(R.string.ai_molar_mass_of, name, "$formattedMass g/mol")
                }
            }
        }

        // 2. Otherwise treat it as a formula (preserving original casing for strict parsing)
        // Restricted to PRO+ only
        if (!isProPlus) {
            return "Calculating molar mass for compounds is a **PRO+** feature. As a ${if (rateLimiter?.isPro() == true) "PRO" else "Free"} user, you can still get molar mass for individual elements!"
        }

        val formula = target.replace(" ", "")
        val calculatedMass = molarMassCalculator?.calculate(formula)
        if (calculatedMass != null && calculatedMass > 0.0) {
            val formattedMass = String.format(java.util.Locale.US, "%.3f", calculatedMass)
            return ctx.getString(R.string.ai_molar_mass_of, formula, "$formattedMass g/mol")
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

        // Check for series filter (e.g. "heaviest noble gas")
        val seriesFilter = when {
            query.contains("noble gas") || query.contains("ädelgas") -> "Noble Gas"
            query.contains("alkali") -> "Alkali Metal"
            query.contains("halogen") -> "Halogen"
            query.contains("lanthanide") || query.contains("lantanid") -> "Lanthanide"
            query.contains("actinide") || query.contains("aktinid") -> "Actinide"
            query.contains("transition metal") || query.contains("övergångsmetall") -> "Transition Metal"
            else -> null
        }

        var targetValue = if (findMax) Double.MIN_VALUE else Double.MAX_VALUE
        var targetElement = ""
        var targetValueStr = ""

        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val element = data.optJSONObject(key) ?: continue
            
            if (seriesFilter != null) {
                val group = element.optString("element_group", "")
                if (!group.contains(seriesFilter, ignoreCase = true)) continue
            }

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
            val seriesText = if (seriesFilter != null) " **$seriesFilter**" else ""
            val result = ctx.getString(R.string.ai_superlative_result, adj, label, targetElement, targetValueStr)
            if (seriesFilter != null) "Among the$seriesText, $result" else result
        } else {
            ctx.getString(R.string.ai_superlative_no_calculate)
        }
    }

    private fun isFormulaQuery(query: String): Boolean {
        // Only consider it a formula if it contains:
        // 1. Numbers (H2O)
        // 2. Brackets (NH4)2SO4
        // 3. Multiple capital letters in a single word (NaCl, KOH)
        
        // If it's a known element name in the map, it's NOT a formula
        if (localizedElementMap.containsKey(query.lowercase().trim())) return false
        
        val words = query.split(" ")
        for (word in words) {
            val clean = word.lowercase().replace("?", "")
            if (clean.length < 2) continue
            
            // If the specific word is an element, ignore it as a formula trigger
            if (localizedElementMap.containsKey(clean)) continue

            val hasDigitsOrBrackets = word.any { it.isDigit() || it == '(' || it == ')' }
            val upperCount = word.count { it.isUpperCase() }
            
            if (hasDigitsOrBrackets || upperCount >= 2) return true
        }
        return false
    }

    private fun isSafetyQuery(query: String): Boolean {
        val keywords = listOf("safety", "hazard", "dangerous", "flammable", "toxic", "poison", "nfpa", "fara", "farlig", "giftig", "brännbar", "brandfarlig")
        return keywords.any { query.contains(it) }
    }

    private fun handleSafetyQuery(elementName: String): String {
        val ctx = localizedContext ?: context!!
        val element = elementData?.optJSONObject(elementName.lowercase()) ?: return AIPersonality.getNoDataResponse(ctx, activeLanguage, "")
        
        val health = element.optInt("health", -1)
        val flammability = element.optInt("flammability", -1)
        val instability = element.optInt("instability", -1)
        val special = element.optString("special", "")
        
        if (health == -1 && flammability == -1) {
            val radio = element.optString("radioactive", "")
            return if (radio.isNotEmpty() && radio != "no") {
                "$elementName is radioactive ($radio), which is its primary safety concern. Handle with extreme caution!"
            } else {
                "I don't have specific NFPA hazard ratings for $elementName, but always follow standard laboratory safety protocols."
            }
        }

        var safetyInfo = "Safety profile for $elementName (NFPA 704):"
        safetyInfo += "\n• **Health:** $health/4 (${getNFPAHealthDesc(health)})"
        safetyInfo += "\n• **Flammability:** $flammability/4 (${getNFPAFlammableDesc(flammability)})"
        safetyInfo += "\n• **Instability:** $instability/4"
        if (special.isNotEmpty() && special != "---") safetyInfo += "\n• **Special:** $special"
        
        val desc = when {
            health >= 3 -> "Caution: This element is highly hazardous to health!"
            flammability >= 3 -> "Caution: This element is extremely flammable!"
            else -> ""
        }
        
        return if (desc.isNotEmpty()) "$safetyInfo\n\n$desc" else safetyInfo
    }

    private fun getNFPAHealthDesc(level: Int) = when(level) {
        0 -> "Normal material"
        1 -> "Slightly hazardous"
        2 -> "Hazardous"
        3 -> "Extreme danger"
        4 -> "Deadly"
        else -> "Unknown"
    }

    private fun getNFPAFlammableDesc(level: Int) = when(level) {
        0 -> "Will not burn"
        1 -> "Must be preheated to burn"
        2 -> "Ignites when moderately heated"
        3 -> "Ignites at normal temperatures"
        4 -> "Extremely flammable"
        else -> "Unknown"
    }

    private fun handleFormulaQuery(query: String): String {
        val ctx = localizedContext ?: context!!
        val lowerQuery = query.lowercase()

        // 1. Check for specific hardcoded descriptions
        val specificResponse = when {
            lowerQuery.contains("h2o") -> ctx.getString(R.string.ai_formula_h2o)
            lowerQuery.contains("co2") -> ctx.getString(R.string.ai_formula_co2)
            lowerQuery.contains("nacl") -> ctx.getString(R.string.ai_formula_nacl)
            lowerQuery.contains("o2") -> ctx.getString(R.string.ai_formula_o2)
            lowerQuery.contains("ch4") -> ctx.getString(R.string.ai_formula_ch4)
            lowerQuery.contains("nh3") -> ctx.getString(R.string.ai_formula_nh3)
            lowerQuery.contains("c6h12o6") -> ctx.getString(R.string.ai_formula_c6h12o6)
            lowerQuery.contains("h2so4") -> ctx.getString(R.string.ai_formula_h2so4)
            lowerQuery.contains("hcl") -> ctx.getString(R.string.ai_formula_hcl)
            else -> null
        }
        
        if (specificResponse != null && !lowerQuery.contains("composition") && !lowerQuery.contains("contains")) return specificResponse

        // 2. Dynamic analysis of the formula
        val isProPlus = rateLimiter?.isProPlus() ?: false
        val words = query.split(" ")
        for (word in words) {
            val clean = word.replace("?", "").trim()
            if (clean.length < 2) continue
            
            val elementCounts = molarMassCalculator?.getElementCounts(clean)
            if (elementCounts != null && elementCounts.isNotEmpty()) {
                // If it's a compound (more than 1 element or multiple of same), check PRO+
                val isCompound = elementCounts.size > 1 || elementCounts.values.any { it > 1.0 }
                if (isCompound && !isProPlus) {
                    return "Analyzing chemical compounds like **$clean** is a **PRO+** feature. As a ${if (rateLimiter?.isPro() == true) "PRO" else "Free"} user, I can only help you with individual elements!"
                }

                val mass = molarMassCalculator?.calculate(clean)
                val formattedMass = String.format(java.util.Locale.US, "%.3f", mass ?: 0.0)
                
                var response = "I recognize **$clean** as a chemical compound with a molar mass of **$formattedMass g/mol**."
                
                if (lowerQuery.contains("composition") || lowerQuery.contains("breakdown") || lowerQuery.contains("contain") || lowerQuery.contains("made of")) {
                    response += "\n\nIt consists of:"
                    for ((symbol, count) in elementCounts) {
                        val name = molarMassCalculator?.getElementName(symbol)
                        val countStr = if (count % 1.0 == 0.0) count.toInt().toString() else count.toString()
                        response += "\n• $countStr × $name ($symbol)"
                    }
                } else {
                    response += " Would you like me to break down its elemental composition?"
                }
                return response
            }
        }

        return ctx.getString(R.string.ai_formula_generic)
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
        val lower = currentQuery.lowercase()
        // Check if query references "it", "that", "this element" etc.
        val contextKeywords = listOf("it", "that", "this", "its", "the element", "den", "det", "denna", "denne")
        val hasContextReference = contextKeywords.any { 
            lower == it || lower.startsWith("$it ") || lower.contains(" $it ") || lower.endsWith(" $it") || lower.endsWith("?")
        }
        
        // If it's a very short query like "mass?" or "density?", also use context
        val isPropertyOnly = currentQuery.length < 15 && hasKeyword(lower, listOf("mass", "weight", "density", "boil", "melt", "atomic", "number", "symbol", "discovered", "discoverer", "year", "radioactive", "radiation", "decay", "phase", "state", "color", "colour", "appearance", "protons", "electrons", "neutrons", "config", "shell", "oxidation", "charge", "electronegativity", "block", "radius", "size", "abundance", "crystal", "cas", "eg"))
        
        if (hasContextReference || isPropertyOnly || currentElement != null) {
            if (currentElement != null) return currentElement

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
                hasKeyword(lowerQuery, listOf("atomic number", "proton", "number", "atomnummer", "atomnummeret", "ordnungszahl", "número atómico", "numéro atomique", "numbro")) || matchLabel(lowerQuery, R.string.atomic_number_label) -> {
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
                    val group = element.optString("element_group", "")
                    val combined = if (group.isNotEmpty() && group != "---") "$type ($group)" else type
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, prop, combined, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("group", "column", "grupp", "kolumn", "gruppe", "spalte", "grupo", "columna", "groupe", "colonne")) || matchLabel(lowerQuery, R.string.element_groups) -> {
                    val prop = "group"
                    val label = ctx.getString(R.string.element_groups).trim()
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val group = element.optString("element_group", "")
                    val groupNum = element.optString("element_group_number", "")
                    val value = if (groupNum.isNotEmpty() && groupNum != "---") "$group (Group $groupNum)" else group
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, value, isRepeat)
                }
                hasKeyword(lowerQuery, listOf("period", "row", "rad", "periode", "zeile", "periodo", "fila", "rangée")) || matchLabel(lowerQuery, R.string.element_number) -> {
                    val prop = "period"
                    learningManager?.trackPropertyInterest(prop, isTechnical = false)
                    val isRepeat = sharedProperties.contains(prop)
                    sharedProperties.add(prop)
                    val period = element.optString("element_period", "")
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, prop, period, isRepeat)
                }
                
                // Appearance and physical properties
                hasKeyword(lowerQuery, listOf("appear", "look", "color", "colour", "physical", "visual", "utseende", "färg", "aussehen", "farbe", "apariencia", "apparence", "couleur")) || matchLabel(lowerQuery, R.string.appearance_colon) -> {
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
                hasKeyword(lowerQuery, listOf("boiling", "boil", "kokpunkt", "kokar", "siedepunkt", "kocht", "ebullición", "hierve", "ébullition")) || matchLabel(lowerQuery, R.string.boiling_point_colon) -> {
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
                    val volume = element.optString("molar_volume", "")
                    var resp = AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, density, isRepeat)
                    if (volume.isNotEmpty() && volume != "---") {
                        resp += "\n" + AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, "molar volume", volume)
                    }
                    resp
                }
                
                // Mechanical properties
                hasKeyword(lowerQuery, listOf("modulus", "elastic", "stiff", "strong", "brittle", "malleable", "tough", "poisson", "ratio", "young", "shear", "bulk", "hårdhet", "elastisk")) -> {
                    val ym = element.optString("young_modulus", "")
                    val sm = element.optString("shear_modulus", "")
                    val bm = element.optString("bulk_modulus", "")
                    val pr = element.optString("poisson_ratio", "")
                    
                    var mechInfo = ctx.getString(R.string.element_elastic_modulus).replace(":", "").trim() + " for $elementName:"
                    if (ym.isNotEmpty() && ym != "---") mechInfo += "\n• Young's Modulus: $ym"
                    if (sm.isNotEmpty() && sm != "---") mechInfo += "\n• Shear Modulus: $sm"
                    if (bm.isNotEmpty() && bm != "---") mechInfo += "\n• Bulk Modulus: $bm"
                    if (pr.isNotEmpty() && pr != "---") mechInfo += "\n• Poisson's Ratio: $pr"
                    
                    if (mechInfo.length > 30) mechInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }

                // Hardness
                hasKeyword(lowerQuery, listOf("hardness", "hard", "soft", "mohs", "vickers", "brinell")) -> {
                    val mohs = element.optString("mohs_hardness", "")
                    val vick = element.optString("vickers_hardness", "")
                    val brin = element.optString("brinell_hardness", "")
                    
                    var hardInfo = "Hardness properties of $elementName:"
                    if (mohs.isNotEmpty() && mohs != "---") hardInfo += "\n• Mohs Hardness: $mohs"
                    if (vick.isNotEmpty() && vick != "---") hardInfo += "\n• Vickers Hardness: $vick"
                    if (brin.isNotEmpty() && brin != "---") hardInfo += "\n• Brinell Hardness: $brin"
                    
                    if (hardInfo.length > 30) hardInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }

                // Abundance
                hasKeyword(lowerQuery, listOf("abundance", "common", "rare", "find", "found", "where", "ocean", "crust", "universe", "sun", "solar")) -> {
                    val crust = element.optString("earth_crust", "")
                    val ocean = element.optString("sea_water", "")
                    val sun = element.optString("sun", "")
                    val solar = element.optString("solar_system", "")
                    
                    var abInfo = ctx.getString(R.string.abundance_title) + " of $elementName:"
                    if (crust.isNotEmpty() && crust != "---") abInfo += "\n• " + ctx.getString(R.string.abundance_earth_crust) + ": $crust mg/kg"
                    if (ocean.isNotEmpty() && ocean != "---") abInfo += "\n• " + ctx.getString(R.string.abundance_sea_water) + ": $ocean mg/L"
                    if (sun.isNotEmpty() && sun != "---") abInfo += "\n• " + ctx.getString(R.string.abundance_sun) + ": $sun (relative to H=10¹²)"
                    if (solar.isNotEmpty() && solar != "---") abInfo += "\n• " + ctx.getString(R.string.abundance_solar_system) + ": $solar (relative to H=10¹²)"
                    
                    val desc = element.optString("description", "")
                    if (abInfo.length < 35 && desc.isNotEmpty()) {
                        "${AIPersonality.getEncouragement(ctx, activeLanguage)} $desc"
                    } else if (abInfo.length > 35) {
                        abInfo
                    } else {
                        AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                    }
                }
                
                // Advanced Atomic
                hasKeyword(lowerQuery, listOf("affinity", "work function", "refractive", "space group", "lattice")) -> {
                    val ea = element.optString("electron_affinity", "")
                    val wf = element.optString("work_function", "")
                    val ri = element.optString("refractive_index", "")
                    val sgN = element.optString("space_group_name", "")
                    val sgNum = element.optString("space_group_number", "")
                    
                    var advInfo = "Advanced properties of $elementName:"
                    if (ea.isNotEmpty() && ea != "---") advInfo += "\n• Electron Affinity: $ea"
                    if (wf.isNotEmpty() && wf != "---") advInfo += "\n• Work Function: $wf"
                    if (ri.isNotEmpty() && ri != "---") advInfo += "\n• Refractive Index: $ri"
                    if (sgN.isNotEmpty() && sgN != "---") advInfo += "\n• Space Group: $sgN (#$sgNum)"
                    
                    if (advInfo.length > 30) advInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
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
                    if (radioactive.isNotEmpty() && radioactive != "no") {
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

                // Block
                hasKeyword(lowerQuery, listOf("block")) || matchLabel(lowerQuery, R.string.block) -> {
                    val block = element.optString("element_block", "")
                    val blockLabel = ctx.getString(R.string.block).trim()
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, blockLabel, block)
                }

                // Oxidation States
                hasKeyword(lowerQuery, listOf("oxidation", "valence", "outer", "bond")) || matchLabel(lowerQuery, R.string.oxidation_states_colon) -> {
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
                    if (pos.isNotEmpty() && pos != "---") stateInfo += "\n• Positive: $pos"
                    if (neg.isNotEmpty() && neg != "---") stateInfo += "\n• Negative: $neg"
                    
                    val config = element.optString("element_electron_config", "")
                    if (config.isNotEmpty()) stateInfo += "\n\nIts electron configuration is $config."
                    
                    stateInfo
                }

                // Isotopes
                hasKeyword(lowerQuery, listOf("isotope", "half-life", "stable")) || matchLabel(lowerQuery, R.string.isotopes_colon) -> {
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
                        "$elementName has various isotopes. You can check the detailed isotope table in the app for a full list!"
                    }
                }

                // Radius properties
                hasKeyword(lowerQuery, listOf("radius", "size", "big", "small")) || matchLabel(lowerQuery, R.string.atomic_radius_empirical_colon) -> {
                    val prop = "atomic radius"
                    val valE = element.optString("element_atomic_radius_e", "")
                    val valC = element.optString("element_covalent_radius", "")
                    val valV = element.optString("element_van_der_waals", "")
                    
                    var radiusInfo = "Here are the radius details for $elementName:"
                    if (valE.isNotEmpty() && valE != "---") radiusInfo += "\n• Empirical Atomic Radius: $valE pm"
                    if (valC.isNotEmpty() && valC != "---") radiusInfo += "\n• Covalent Radius: $valC pm"
                    if (valV.isNotEmpty() && valV != "---") radiusInfo += "\n• Van der Waals Radius: $valV pm"
                    
                    if (radiusInfo.length > 50) radiusInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }

                // Thermal and Heat
                hasKeyword(lowerQuery, listOf("heat", "thermal", "conductivity", "fusion", "vaporization", "specific")) || matchLabel(lowerQuery, R.string.thermal_conductivity_colon) -> {
                    val sh = element.optString("element_specific_heat_capacity", "")
                    val fh = element.optString("element_fusion_heat", "")
                    val vh = element.optString("element_vaporization_heat", "")
                    val tc = element.optString("element_thermal_conductivity", "")
                    
                    var heatInfo = "Thermal properties of $elementName:"
                    if (sh.isNotEmpty() && sh != "---") heatInfo += "\n• Specific Heat Capacity: $sh J/(g·K)"
                    if (fh.isNotEmpty() && fh != "---") heatInfo += "\n• Heat of Fusion: $fh kJ/mol"
                    if (vh.isNotEmpty() && vh != "---") heatInfo += "\n• Heat of Vaporization: $vh kJ/mol"
                    if (tc.isNotEmpty() && tc != "---") heatInfo += "\n• Thermal Conductivity: $tc W/(m·K)"
                    
                    if (heatInfo.length > 40) heatInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }

                // Electrical and Magnetic
                hasKeyword(lowerQuery, listOf("conductor", "magnetic", "resistivity", "electricity", "superconducting")) || matchLabel(lowerQuery, R.string.electrical_resistivity_colon) -> {
                    val et = element.optString("electrical_type", "")
                    val mt = element.optString("magnetic_type", "")
                    val sp = element.optString("superconducting_point", "")
                    val res = element.optString("resistivity", "")
                    
                    var elecInfo = "Electrical & Magnetic properties of $elementName:"
                    if (et.isNotEmpty() && et != "---") elecInfo += "\n• Electrical Type: $et"
                    if (mt.isNotEmpty() && mt != "---") elecInfo += "\n• Magnetic Type: $mt"
                    if (sp.isNotEmpty() && sp != "---") elecInfo += "\n• Superconducting Point: $sp K"
                    if (res.isNotEmpty() && res != "---") elecInfo += "\n• Resistivity: $res Ω·m"
                    
                    if (elecInfo.length > 50) elecInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }
                
                // Crystal Structure
                hasKeyword(lowerQuery, listOf("crystal", "structure", "lattice", "kristall")) || matchLabel(lowerQuery, R.string.crystal_structure) -> {
                    val structure = element.optString("element_crystal_structure", "")
                    val label = ctx.getString(R.string.crystal_structure).trim()
                    AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, structure)
                }

                // Identifiers
                hasKeyword(lowerQuery, listOf("cas", "eg", "identification")) || matchLabel(lowerQuery, R.string.cas_number) -> {
                    val cas = element.optString("cas_number", "")
                    val eg = element.optString("eg_number", "")
                    
                    val title = ctx.getString(R.string.ai_identifiers_for, elementName)
                    val casLabel = ctx.getString(R.string.cas_number).trim()
                    val egLabel = ctx.getString(R.string.eg_number).trim()
                    
                    var idInfo = title
                    if (cas.isNotEmpty() && cas != "---") idInfo += "\n• $casLabel: $cas"
                    if (eg.isNotEmpty() && eg != "---") idInfo += "\n• $egLabel: $eg"
                    
                    if (idInfo.length > title.length + 5) idInfo else AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
                }
                
                // Deep-dive or tell me more
                hasKeyword(lowerQuery, listOf("additional", "extra", "further", "tell me more", "what else", "deep dive", "keep going", "next", "continue", "anything else", "more info")) -> {
                    provideNewInformation(element, elementName)
                }

                // Default overview or summary
                else -> {
                    if (lowerQuery.contains("overview") || lowerQuery.contains("tell me about") || lowerQuery.length < 3 || !sharedProperties.contains("overview")) {
                        provideOverview(element, elementName)
                    } else {
                        provideNewInformation(element, elementName)
                    }
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
                    response += "\n\n" + ctx.getString(R.string.ai_technical_mass, weight)
                }
                val configuration = element.optString("element_electron_config", "")
                if (configuration.isNotEmpty() && configuration != "---") {
                    response += "\n" + ctx.getString(R.string.ai_technical_config, configuration)
                }
                val density = element.optString("element_density", "")
                if (density.isNotEmpty() && density != "---") {
                    response += "\n• Density: $density"
                }
                val block = element.optString("element_block", "")
                if (block.isNotEmpty() && block != "---") {
                    response += "\n• Block: $block"
                }
            }
            response
        } else {
            // We've already shared the overview, give a lighter summary or a new fact
            val summaryData = mutableMapOf<String, String>()
            if (symbol.isNotEmpty()) summaryData["symbol"] = symbol
            if (atomicNum.isNotEmpty()) summaryData["atomic number"] = atomicNum
            if (type.isNotEmpty()) summaryData["category"] = type
            
            val melting = element.optString("element_melting_celsius", "")
            if (melting.isNotEmpty() && melting != "---") summaryData["melting point"] = "$melting°C"
            
            val boiling = element.optString("element_boiling_celsius", "")
            if (boiling.isNotEmpty() && boiling != "---") summaryData["boiling point"] = "$boiling°C"

            if (summaryData.size > 2) {
                AIPersonality.formatComprehensiveResponse(ctx, activeLanguage, elementName, summaryData)
            } else if (description.isNotEmpty()) {
                "${AIPersonality.getEncouragement(ctx, activeLanguage)} $description"
            } else {
                provideNewInformation(element, elementName)
            }
        }
    }

    /**
     * Provide a piece of information that hasn't been shared yet in this conversation.
     * Can provide multiple related facts if Technical Preference is high.
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
            "electrons" to "element_electrons",
            "Young's modulus" to "young_modulus",
            "Poisson's ratio" to "poisson_ratio",
            "abundance in crust" to "earth_crust",
            "crystal structure" to "crystal_structure",
            "CAS number" to "cas_number",
            "molar volume" to "molar_volume",
            "thermal conductivity" to "element_thermal_conductivity",
            "discovery year" to "element_year"
        )
        
        // Find properties we haven't shared yet
        val availableNewFacts = potentialProperties.filter { !sharedProperties.contains(it.first) }.toMutableList()
        
        if (availableNewFacts.isEmpty()) {
            // If everything is shared, give a fun fact instead
            return "${AIPersonality.getEncouragement(context!!, activeLanguage)} We've covered a lot! Did you know? ${AIPersonality.getRandomFact(context!!, activeLanguage)}"
        }

        val results = mutableListOf<String>()
        val numToProvide = if (learningManager?.isTechnicalPreferred() == true) 2 else 1
        
        repeat(numToProvide) {
            if (availableNewFacts.isNotEmpty()) {
                val randomIndex = (0 until availableNewFacts.size).random()
                val (label, jsonKey) = availableNewFacts.removeAt(randomIndex)
                sharedProperties.add(label)
                val value = element.optString(jsonKey, "")
                if (value.isNotEmpty() && value != "---") {
                    results.add(AIPersonality.formatElementResponse(context!!, activeLanguage, elementName, label, value))
                }
            }
        }

        return if (results.isNotEmpty()) {
            results.joinToString("\n\n")
        } else {
            // Fallback: try one more time or give fact
            provideNewInformation(element, elementName)
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
     * Helper to check if query contains any of the keywords, with fuzzy matching for spelling mistakes.
     * Improved to avoid false positives with short keywords (like French 'bout' matching 'about').
     */
    private fun hasKeyword(query: String, keywords: List<String>): Boolean {
        val lowerQuery = query.lowercase()
        
        for (keyword in keywords) {
            // Use word boundary regex for short keywords (length < 5) to avoid false matches like "about" -> "bout"
            if (keyword.length < 5) {
                val regex = "\\b${Regex.escape(keyword)}\\b".toRegex()
                if (regex.containsMatchIn(lowerQuery)) return true
            } else {
                if (lowerQuery.contains(keyword)) return true
            }
        }
        
        // Split query into words and check each against keywords for fuzzy matching
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
