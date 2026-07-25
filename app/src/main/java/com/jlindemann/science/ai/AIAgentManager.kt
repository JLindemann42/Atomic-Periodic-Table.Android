package com.jlindemann.science.ai

import android.content.Context
import android.util.Log
import com.jlindemann.science.R
import com.jlindemann.science.ai.compose.ChatActionCodec
import com.jlindemann.science.ai.core.AiEngine
import com.jlindemann.science.ai.core.AndroidStrings
import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.retrieval.RetrievalService
import com.jlindemann.science.ai.retrieval.TextMatching
import com.jlindemann.science.ai.retrieval.RetrievedRef
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.utils.ElementDataLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** Orchestrates AI responses: element lookup, context tracking, language handling, RAG. */
class AIAgentManager(private val context: Context?) {

    companion object {
        private const val TAG = "AIAgentManager"
        private const val PREFS_NAME = "ai_agent_settings"
        private const val PREF_LANGUAGE = "ai_agent_language"
        private const val DEFAULT_LANGUAGE = "en"
    }

    private var elementData: JSONObject? = null
    private var isDataLoaded = false
    private var conversationHistory = mutableListOf<ChatMessage>()
    private var currentElement: String? = null
    private var currentTopic: String? = null
    private val sharedProperties = mutableSetOf<String>()
    private var currentQuizAnswer: String? = null
    private var lastSuggestionType: String? = null
    private var lastFormulaTarget: String? = null
    private var activeLanguage: String = DEFAULT_LANGUAGE
    private var localizedContext: Context? = null

    private val rateLimiter: AIRateLimiter? by lazy { context?.let { AIRateLimiter(it) } }
    private val learningManager: AILearningManager? by lazy { context?.let { AILearningManager(it) } }
    private val localKnowledgeManager: LocalKnowledgeManager? by lazy { context?.let { LocalKnowledgeManager(it) } }

    private var molarMassCalculator: MolarMassCalculator? = null
    private var retrievalService: RetrievalService? = null
    private val dialogueState = DialogueState()
    private var cachedEngine: AiEngine? = null
    private var cachedEngineLanguage: String? = null

    // Multi-language keywords for rich section extraction
    private val sectionKeywords = mapOf(
        "abundance" to listOf(
            "find", "found", "abundance", "crust", "universe", "nature", "meteorite",
            "förekomst", "finns", "natur", "universum",
            "vorkommen", "existiert", "natur", "universum",
            "trouver", "abondance", "nature", "univers",
            "encontrar", "abundancia", "naturaleza", "universo",
            "encontrar", "abundância", "natureza", "universo",
            "trovare", "abbondanza", "natura", "universo",
            "matatagpuan", "abundance", "kalikasan", "sansinukob",
            "gevind", "vorkoms", "natuur", "heelal",
            "kahan", "paaya", "prachurta", "prakriti", "brahmand",
            "jali", "maujood", "fitrat", "kayinat",
            "yongtu", "cunzai", "ziran", "yuzhou"
        ),
        "usage" to listOf(
            "use", "industry", "application", "medicine", "important", "human",
            "användning", "används", "industri", "medicin", "viktig", "människa",
            "industrie", "anwendung", "medizin", "wichtig", "mensch",
            "industrie", "utilisation", "médecine", "important", "humain",
            "aplicación", "industria", "medicina", "importante", "humano",
            "aplicação", "indústria", "medicina", "importante", "humano",
            "applicazione", "industria", "medicina", "importante", "umano",
            "gamit", "industriya", "medisina", "importante", "tao",
            "gebruik", "industrie", "medisyne", "belangrik", "mens",
            "upayog", "udyog", "dava", "mahatvapurna", "manav",
            "istimal", "sanat", "tibb", "aham", "insan",
            "yongtu", "gongye", "yingyong", "yiyao", "zhongyao", "renlei"
        ),
        "history" to listOf(
            "name", "greek", "latin", "discover", "history", "origin",
            "namn", "upptäck", "historia", "ursprung",
            "herkunft", "geschichte", "entdeckt",
            "nom", "origine", "histoire", "découvert",
            "nombre", "origen", "historia", "descubierto",
            "nome", "origem", "história", "descoberto",
            "nome", "origine", "storia", "scoperto",
            "pangalan", "pinagmulan", "kasaysayan", "natuklasan",
            "naam", "oorsprong", "geskiedenis", "ontdek",
            "naam", "utpatti", "itihas", "khoj",
            "naam", "tareekh", "asl", "daryaft",
            "mingzi", "lishi", "yuanyuan", "fajian"
        ),
        "biological" to listOf(
            "body", "blood", "living", "essential", "organism", "biology", "health",
            "kropp", "blod", "levande", "organism", "biologi", "hälsa",
            "körper", "blut", "lebewesen", "biologisch", "gesundheit",
            "corps", "sang", "vivant", "biologie", "santé",
            "cuerpo", "sangre", "vivo", "biología", "salud",
            "corpo", "sangue", "vivo", "biologia", "saúde",
            "corpo", "sangue", "vivente", "biologia", "salute",
            "katawan", "dugo", "buhay", "biyolohiya", "kalusugan",
            "liggaam", "bloed", "lewendige", "biologiese", "gesondheid",
            "sharir", "rakta", "jeev", "jaivik", "swasthya",
            "jism", "khoon", "zinda", "hayatati", "sehat",
            "shenti", "xueye", "shengwu", "shengwuxue", "jiankang"
        )
    )

    private val overviewKeywords = listOf(
        "overview", "summary", "tell me about", "what is", "describe", "profile", "info", "brief", "details", "information",
        "översikt", "berätta om", "vad är", "beskriv", "profil", "sammanfattning", "info", "detaljer", "information",
        "überblick", "erzähl mir", "was ist", "beschreibe", "zusammenfassung", "details", "informationen",
        "aperçu", "parle-moi", "qu'est-ce que", "décris", "résumé", "détails", "informations",
        "resumen", "cuéntame", "qué es", "describe", "perfil", "detalles", "información",
        "resumo", "conta-me", "o que é", "descreve", "detalhes", "informação",
        "panoramica", "dimmi di", "cos'è", "descrivi", "dettagli", "informazioni",
        "pangkalahatang-ideya", "sabihin sa akin", "ano ang", "ilarawan", "detalye", "impormasyon",
        "oorsig", "vertel my", "wat is", "beskryf", "besonderhede", "inligting",
        "vivran", "bataye", "kya hai", "varnan", "vivaran", "jankari",
        "khulasa", "batao", "kya hai", "biyaan", "tafseelat", "maloomat",
        "gaikuang", "gaishu", "gaolan", "jieshao", "xiangqing", "xinxi"
    )

    private val fullRequestKeywords = listOf(
        "all", "everything", "complete", "full", "detailed", "whole", "total",
        "allt", "hela", "komplett", "fullständig", "detaljerad",
        "alles", "komplett", "vollständig", "detailliert",
        "tout", "complet", "détaillé", "entier",
        "todo", "completo", "detallado", "entero",
        "tutto", "completo", "dettagliato",
        "lahat", "kumpleto", "detalyado",
        "alles", "volledig", "volledige",
        "sab", "sab kuch", "purn", "vistrit",
        "sab", "mukammal", "tafseeli",
        "suoyou", "quanbu", "xiangxi", "quanmian"
    )

    // Maps localized element name → JSON key, built across all languages once
    private val localizedElementMap = mutableMapOf<String, String>()
    // Maps localized element name → set of languages it appears in
    private val localizedElementLanguageMap = mutableMapOf<String, MutableSet<String>>()
    private var availableElementLanguages = emptySet<String>()

    fun getActiveLanguage(): String = activeLanguage

    fun setCurrentElement(element: String?) {
        currentElement = element
    }

    fun getCurrentElement(): String? = currentElement

    fun getCurrentTopic(): String? = currentTopic

    fun getSuggestedQuestions(): List<String> {
        val localizedElementName = currentElement?.let { key ->
            val element = elementData?.optJSONObject(key.lowercase())
            element?.optString("element")?.ifEmpty { null } ?: key
        }
        
        return AIPersonality.getSuggestedQuestions(
            context ?: return emptyList(),
            activeLanguage,
            localizedElementName,
            currentTopic
        )
    }

    fun isHistoryEnabled(): Boolean {
        val limiter = rateLimiter ?: return false
        return limiter.isPro() || limiter.isProPlus()
    }

    fun shouldShowMessageLimit(): Boolean {
        val limiter = rateLimiter ?: return false
        return !limiter.isProPlus()
    }

    fun getMessageLimitDisplay(): String {
        val limiter = rateLimiter ?: return "0/0"
        val total = limiter.getDailyLimit()
        if (total == Int.MAX_VALUE) return "∞/∞"
        val left = limiter.getRemainingMessages().coerceAtLeast(0)
        return "$left/$total"
    }

    suspend fun refreshLanguage() {
        val ctx = context ?: return
        val newLang = ElementDataLoader.getAppLanguage(ctx)
        if (newLang != activeLanguage) {
            setLanguage(newLang)
        }
    }

    suspend fun setLanguage(language: String) {
        activeLanguage = language
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(PREF_LANGUAGE, activeLanguage)?.apply()
        updateLocalizedContext()
        // The engine caches localized labels and aliases, so it has to be rebuilt.
        cachedEngine = null
        cachedEngineLanguage = null
        withContext(Dispatchers.IO) {
            elementData = getElementDataByLanguage(activeLanguage)
            molarMassCalculator = MolarMassCalculator(elementData)
            ensureCrossLanguageMapLoaded()
        }
    }

    private fun updateLocalizedContext() {
        context?.let {
            val localeTag = toBcp47Tag(activeLanguage)
            val locale = Locale.forLanguageTag(localeTag).let { parsed ->
                if (parsed.language.isNullOrBlank()) Locale(resolveBaseLanguage(activeLanguage)) else parsed
            }
            // No Locale.setDefault: see AIPersonality.getLocalizedContext. The auto-detected chat
            // language must not become the process-wide default.
            val config = android.content.res.Configuration(it.resources.configuration)
            config.setLocale(locale)
            localizedContext = it.createConfigurationContext(config)
        }
    }

    private fun toBcp47Tag(language: String): String {
        val trimmed = language.trim()
        if (trimmed.isEmpty()) return "en"
        val normalized = trimmed.replace('_', '-')
        val legacyRegion = Regex("^([a-zA-Z]{2,3})-r([a-zA-Z]{2})$")
        val match = legacyRegion.matchEntire(normalized)
        return if (match != null) "${match.groupValues[1]}-${match.groupValues[2]}" else normalized
    }

    private fun resolveBaseLanguage(language: String?): String {
        val raw = language?.trim().orEmpty()
        if (raw.isBlank()) return "en"

        val tag = toBcp47Tag(raw)
        val parsedLanguage = Locale.forLanguageTag(tag).language.lowercase()
        if (parsedLanguage.isNotBlank()) return parsedLanguage

        return raw
            .substringBefore("-r")
            .substringBefore('-')
            .substringBefore('_')
            .lowercase()
            .ifBlank { "en" }
    }

    private fun normalizeForLookup(text: String): String = TextMatching.normalizeForLookup(text)

    private fun splitQueryTokens(query: String): List<String> = TextMatching.splitQueryTokens(query)

    /**
     * Delegates to the shared implementation, which uses a Unicode-aware word boundary.
     *
     * The copy that lived here used a plain `\b`, which Java defines over ASCII only, so every
     * accented letter counted as a boundary: a two-letter symbol matched inside an ordinary word
     * in any accented language — "sm" and "f" were both found in the Swedish "smältpunkten för".
     * Every handler that resolves an element from the query was affected.
     */
    private fun containsElementToken(rawQuery: String, normalizedQuery: String, token: String): Boolean =
        TextMatching.containsToken(rawQuery, normalizedQuery, token)

    private fun isCommonWordCollision(token: String, lowerQuery: String, queryWords: List<String>): Boolean {
        if (token.length > 2) return false
        
        val isCommon = when (resolveBaseLanguage(activeLanguage)) {
            "en" -> token in listOf("in", "as", "at", "be", "he", "am", "i", "no", "ar")
            "sv" -> token in listOf("ar", "i", "se", "ne", "na", "be", "es") // "ar" for "är"
            "de" -> token in listOf("as", "be", "er", "es", "in", "se", "am", "zu", "an", "um", "du", "so", "da")
            "fr" -> token in listOf("au", "as", "y", "la", "ca", "ce", "es", "en", "de", "le", "un", "et", "il", "se", "ne")
            "es" -> token in listOf("y", "la", "ca", "se", "no", "si", "as", "es", "de", "el", "en", "un", "al", "su", "lo", "ya")
            "it" -> token in listOf("i", "la", "se", "si", "ne", "in", "di", "il", "ed", "un", "ad", "al", "lo", "su", "ma")
            "hi", "ur" -> token in listOf("se", "ne", "na", "pa", "ka", "ki", "ke", "ko", "jo", "to", "ab", "vo", "hi")
            "pt" -> token in listOf("o", "as", "os", "se", "em", "um", "ou", "ao", "do", "da")
            "af" -> token in listOf("na", "te", "op", "in", "om", "by", "sy", "as", "hy", "is")
            "fil" -> token in listOf("ng", "sa", "na", "at", "ay", "ko", "mo", "ba", "ka", "pa")
            else -> false
        }
        
        if (isCommon) {
            // If it's a common word, only match if it's the ONLY word or clearly a symbol
            if (queryWords.size > 1 && !lowerQuery.contains("element") && !lowerQuery.contains("symbol")) return true
        }
        return false
    }

    private fun findCrossLanguageElementKey(lowerQuery: String, queryWords: List<String>): String? {
        val normalizedQuery = normalizeForLookup(lowerQuery)

        // Try exact match first for high precision
        elementData?.let { data ->
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val element = data.optJSONObject(key)
                val localizedName = element?.optString("element", "")?.lowercase()
                if (localizedName == lowerQuery) return key
            }
        }

        val allMatches = mutableListOf<Pair<String, Int>>() // Key to Score

        // 1. Check mapped entries (O(1) lookups)
        for (word in queryWords) {
            val normalizedWord = normalizeForLookup(word)
            
            // Skip common word collisions
            if (isCommonWordCollision(word, lowerQuery, queryWords)) continue
            
            // Collect all possible matches for this word
            localizedElementMap[word]?.let { key ->
                val score = if (word.length > 2) 100 + word.length else 10 + word.length
                allMatches.add(key to score)
            }
            if (normalizedWord != word) {
                localizedElementMap[normalizedWord]?.let { key ->
                    val score = if (normalizedWord.length > 2) 90 + normalizedWord.length else 5 + normalizedWord.length
                    allMatches.add(key to score)
                }
            }
        }
        
        // 2. Fallback to contains check (O(N) search) if no word match or to catch multi-word names
        localizedElementMap.entries.forEach { (localizedName, key) ->
            if (localizedName.length < 3) return@forEach // Symbols handled by word split
            
            if (containsElementToken(lowerQuery, normalizedQuery, localizedName)) {
                // If the element name is found in the sentence
                val score = 50 + localizedName.length
                allMatches.add(key to score)
            }
        }
        
        // 3. Heuristic: If we found "Gold" and "In" (Indium), pick "Gold" because it's longer/higher score
        // Sort by score descending and take top
        return allMatches.maxByOrNull { it.second }?.first
    }

    private suspend fun ensureCrossLanguageMapLoaded() {
        if (localizedElementMap.isNotEmpty()) return
        loadCrossLanguageElementMap()
    }

    private suspend fun loadCrossLanguageElementMap() {
        val ctx = context ?: return
        withContext(Dispatchers.IO) {
            val languages = ElementDataLoader.getAvailableLanguages(ctx.assets)
            availableElementLanguages = languages.toSet()
            for (lang in languages) {
                try {
                    val inputStream = ctx.assets.open("elements_$lang.json")
                    val data = JSONObject(inputStream.bufferedReader().use { it.readText() })
                    val keys = data.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val element = data.optJSONObject(key)
                        val rawName = element?.optString("element") ?: continue
                        val symbol = element.optString("short", "").lowercase()
                        val normalized = normalizeForLookup(rawName)
                        
                        // Map name and symbol to the English key
                        localizedElementMap[normalized] = key
                        localizedElementMap[rawName.lowercase()] = key
                        if (symbol.isNotEmpty()) {
                            localizedElementMap[symbol] = key
                        }
                        
                        localizedElementLanguageMap.getOrPut(normalized) { mutableSetOf() }.add(lang)
                        localizedElementLanguageMap.getOrPut(rawName.lowercase()) { mutableSetOf() }.add(lang)
                        if (symbol.isNotEmpty()) {
                            localizedElementLanguageMap.getOrPut(symbol) { mutableSetOf() }.add(lang)
                        }
                    }
                } catch (e: Exception) {
                    // File missing for this language — skip
                }
            }
        }
    }

    /**
     * Detect which language the query is most likely in, based on element-name matches.
     * Returns the detected language code, or the current active language if ambiguous.
     */
    private fun detectResponseLanguage(query: String): String {
        val lowerQuery = query.lowercase()
        val normalizedQuery = normalizeForLookup(lowerQuery)
        val words = splitQueryTokens(lowerQuery).filter { it.isNotEmpty() }
        val scores = mutableMapOf<String, Int>()
        for (word in words) {
            val normalized = normalizeForLookup(word)
            val langs = localizedElementLanguageMap[normalized]
                ?: localizedElementLanguageMap[word]
                ?: continue
            for (lang in langs) scores[lang] = (scores[lang] ?: 0) + 1
        }
        if (scores.isEmpty()) {
            for ((localizedName, langs) in localizedElementLanguageMap) {
                if (!containsElementToken(lowerQuery, normalizedQuery, localizedName)) continue
                for (lang in langs) scores[lang] = (scores[lang] ?: 0) + 1
            }
        }
        if (scores.isEmpty()) return activeLanguage
        val maxScore = scores.values.max()
        val topLangs = scores.filter { it.value == maxScore }.keys
        val activeBaseLanguage = resolveBaseLanguage(activeLanguage)
        // Keep current language on tie to avoid spurious switches
        return if (activeBaseLanguage in topLangs) activeLanguage
        else topLangs.firstOrNull { it != "en" } ?: topLangs.first()
    }

    /** Initialize AI agent with element data */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                val ctx = context ?: return@withContext
                activeLanguage = ElementDataLoader.getAppLanguage(ctx)
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_LANGUAGE, activeLanguage)
                    .apply()
                updateLocalizedContext()
                
                elementData = getElementDataByLanguage(activeLanguage)

                molarMassCalculator = MolarMassCalculator(elementData)

                // Typed index + BM25 over the app's own data. Built here, on the IO dispatcher,
                // while the panel spinner is already showing; cached process-wide afterwards.
                retrievalService = RetrievalService.get(ctx.assets)

                // loadCrossLanguageElementMap() is now lazy-loaded via ensureCrossLanguageMapLoaded()

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
     * Reset the conversation context and re-initialize language to app default
     */
    suspend fun resetSession() {
        clearConversation()
        
        // Ensure language is reset to app default for the new session
        val ctx = context
        if (ctx != null) {
            val appLang = ElementDataLoader.getAppLanguage(ctx)
            if (activeLanguage != appLang) {
                setLanguage(appLang)
            }
        }
    }

    /**
     * Reset the conversation context
     */
    fun clearConversation() {
        conversationHistory.clear()
        currentElement = null
        currentTopic = null
        sharedProperties.clear()
        currentQuizAnswer = null
        lastSuggestionType = null
        lastFormulaTarget = null
        dialogueState.clear()
    }
    
    /**
     * Load element data for the specified language, sharing ElementDataLoader's cache so the
     * ~1 MB parse is not duplicated per language across the app.
     */
    private fun getElementDataByLanguage(language: String): JSONObject? {
        val ctx = context ?: return null
        return ElementDataLoader.getAllElements(ctx.assets, language)
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
                text = localizedContext?.getString(R.string.ai_context_lost) ?: "Context lost",
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )

        // Detect language of the query and switch temporarily if needed
        ensureCrossLanguageMapLoaded()
        val detectedLang = detectResponseLanguage(userMessage)
            if (detectedLang != activeLanguage) {
                activeLanguage = detectedLang
                updateLocalizedContext()
                elementData = getElementDataByLanguage(activeLanguage)
                molarMassCalculator = MolarMassCalculator(elementData)
                // The engine caches localized labels and aliases, so it has to be rebuilt.
                cachedEngine = null
                cachedEngineLanguage = null
            }
            
            val localizedCtx = localizedContext ?: ctx

            // Check Rate Limit
            if (rateLimiter?.canSendMessage() == false) {
                return@withContext ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = localizedCtx.getString(R.string.ai_rate_limit_reached, rateLimiter?.getDailyLimit() ?: 0),
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // Increment message count
            rateLimiter?.incrementMessageCount()

            val lowerQuery = userMessage.lowercase().trim()
            val isAffirmative = isAffirmative(lowerQuery)

            // Clear suggestion if NOT an affirmative response
            if (!isAffirmative) {
                lastSuggestionType = null
            }
            if (isMolarMassQuery(lowerQuery)) {
                lastFormulaTarget = null
            }

            // Update Topic Memory
            updateTopicFromIntent(lowerQuery)

            // Handle active Quiz answer
            if (currentQuizAnswer != null && !isQuizQuery(lowerQuery)) {
                return@withContext ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = handleQuizAnswer(lowerQuery),
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            }

            // Structured engine. It answers questions the keyword handlers below cannot express
            // — filters, rankings, aggregates, unit conversions, follow-ups — and returns null
            // for everything else, so unclaimed queries reach the existing handlers unchanged.
            structuredAnswer(userMessage)?.let { return@withContext it }

            // 1. Check if it's a numeric atomic number request (e.g. "element 79" or "what is 79?")
            val numberMatch = Regex("\\b(\\d+)\\b").find(lowerQuery)
            if (numberMatch != null && (lowerQuery.contains("element") || lowerQuery.contains("atomic number") || lowerQuery.contains("atomnummer") || lowerQuery.split(" ").size <= 3)) {
                val numStr = numberMatch.groupValues[1]
                elementData?.let { data ->
                    val keys = data.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val element = data.optJSONObject(key) ?: continue
                        if (element.optString("element_atomic_number") == numStr) {
                            currentElement = key
                            sharedProperties.clear()
                            return@withContext handleElementContextQuery(userMessage, key).let {
                                ChatMessage(UUID.randomUUID().toString(), it, false, System.currentTimeMillis())
                            }
                        }
                    }
                }
            }

            // Comparisons are handled by the structured engine above, including the
            // "compare with X" form that pairs a named element with the one in focus.

            // 1. Explicitly requested element in query
            val foundElementKey = findElementKeyByQuery(userMessage)
            
            if (foundElementKey != null) {
                if (foundElementKey != currentElement) {
                    currentElement = foundElementKey
                    sharedProperties.clear()
                }
            }
            
            // 2. Use context if no new element is found
            val targetElementKey = currentElement ?: contextElement ?: inferElementFromContext(userMessage)
            
            // Track element interest if found
            targetElementKey?.let { learningManager?.trackElementInterest(it) }

            // 3. Handle complex / multi-part queries
            val responseParts = mutableListOf<String>()

            // 4. Add Conversational Connector sometimes
            if (conversationHistory.size >= 2 && (0..2).random() == 0) {
                getConversationalConnector(lowerQuery)?.let { responseParts.add(it) }
            }

            when {
                userMessage.isBlank() -> responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                isQuizQuery(lowerQuery) -> responseParts.add(handleQuizQuery(lowerQuery))
                isTrendsQuery(lowerQuery) -> responseParts.add(handleTrendsQuery(lowerQuery))
                isMolarMassQuery(lowerQuery) -> responseParts.add(handleMolarMassQuery(userMessage))
                // Reactivity has no field in the element data — it is scored from group and
                // position — so the structured engine cannot express it and it is routed here.
                isReactivityQuery(lowerQuery) -> responseParts.add(handleReactivityQuery(lowerQuery))
                // Reactions and similarity are chemistry judgements rather than field lookups,
                // so the engine declines them and they are answered here.
                isReactionQuery(lowerQuery) -> responseParts.add(handleReactionQuery(lowerQuery))
                isSimilarityQuery(lowerQuery) -> responseParts.add(handleSimilarityQuery(lowerQuery))
                isMultiPropertyQuery(lowerQuery) -> {
                    val key = targetElementKey ?: inferElementFromContext(userMessage)
                    if (key != null) responseParts.add(handleMultiPropertyQuery(key, lowerQuery))
                    else responseParts.add(handleElementQuery(userMessage))
                }
                isSafetyQuery(lowerQuery) -> {
                    val key = targetElementKey ?: inferElementFromContext(userMessage)
                    if (key != null) responseParts.add(handleSafetyQuery(key))
                    else responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                }
                isBiologicalQuery(lowerQuery) -> {
                    val key = targetElementKey ?: inferElementFromContext(userMessage)
                    if (key != null) responseParts.add(handleBiologicalQuery(key))
                    else responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                }
                isIsotopeQuery(lowerQuery) -> {
                    val key = targetElementKey ?: inferElementFromContext(userMessage)
                    if (key != null) responseParts.add(handleIsotopeQuery(key))
                    else responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                }
                isUsageQuery(lowerQuery) -> {
                    val key = targetElementKey ?: inferElementFromContext(userMessage)
                    if (key != null) responseParts.add(handleUsageQuery(key))
                    else responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                }
                isAbundanceQuery(lowerQuery) -> {
                    val key = targetElementKey ?: inferElementFromContext(userMessage)
                    if (key != null) responseParts.add(handleAbundanceQuery(key))
                    else responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                }
                isPhysicalPropertyQuery(lowerQuery) -> {
                    val key = targetElementKey ?: inferElementFromContext(userMessage)
                    if (key != null) responseParts.add(handlePhysicalPropertyQuery(key))
                    else responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                }
                isEtymologyQuery(lowerQuery) -> {
                    val key = targetElementKey ?: inferElementFromContext(userMessage)
                    if (key != null) responseParts.add(handleEtymologyQuery(key))
                    else responseParts.add(AIPersonality.getNoDataResponse(ctx, activeLanguage, userMessage))
                }
                isAffirmative && lastFormulaTarget != null -> {
                    responseParts.add(handleFormulaQuery("$lastFormulaTarget composition"))
                    lastFormulaTarget = null
                }
                isAffirmative && targetElementKey != null && lastSuggestionType != null -> {
                    val response = when (lastSuggestionType) {
                        "bio" -> handleBiologicalQuery(targetElementKey)
                        "iso" -> handleIsotopeQuery(targetElementKey)
                        "safety" -> handleSafetyQuery(targetElementKey)
                        "abundance" -> handleAbundanceQuery(targetElementKey)
                        else -> provideNewInformation(elementData!!.getJSONObject(targetElementKey), targetElementKey)
                    }
                    responseParts.add(response)
                    lastSuggestionType = null
                }
                isAffirmative && targetElementKey != null && sharedProperties.isNotEmpty() -> {
                    responseParts.add(provideNewInformation(elementData!!.getJSONObject(targetElementKey), targetElementKey))
                }
                isPropertyOnlyQuery(lowerQuery) && targetElementKey != null -> responseParts.add(handleElementContextQuery(userMessage, targetElementKey))
                isConceptQuery(lowerQuery) -> responseParts.add(handleConceptQuery(lowerQuery))
                isHelpQuery(lowerQuery) -> responseParts.add(handleHelpQuery())
                isFormulaQuery(userMessage) -> responseParts.add(handleFormulaQuery(userMessage))
                isIdentityQuery(lowerQuery) -> responseParts.add(handleIdentityQuery(lowerQuery))
                targetElementKey != null -> {
                    currentElement = targetElementKey
                    val response = handleElementContextQuery(userMessage, targetElementKey)
                    responseParts.add(response)
                    
                    // Add a tiny context-aware hint sometimes
                    if (responseParts.size == 1 && (0..3).random() == 0 && !response.contains("?")) {
                        val localizedHint = ctx.getString(R.string.ai_pro_tip_hint)
                        responseParts.add("💡 *$localizedHint*")
                    }
                }
                isGeneralGreeting(userMessage) -> {
                    val greeting = learningManager?.getPersonalizedGreeting(localizedCtx, activeLanguage) 
                        ?: AIPersonality.getGreeting(localizedCtx, activeLanguage)
                    responseParts.add(greeting)
                }
                isFactRequest(userMessage) -> responseParts.add(AIPersonality.getRandomFact(ctx, activeLanguage))
                else -> {
                    val localMatch = localKnowledgeManager?.resolve(userMessage, activeLanguage)
                    if (localMatch != null) {
                        responseParts.add(localMatch.response)
                    } else {
                        responseParts.add(
                            retrieveAnswer(userMessage) ?: handleElementQuery(userMessage)
                        )
                    }
                }
            }

            // 5. Proactively suggest a follow-up sometimes
            if (responseParts.size == 1 && (0..3).random() == 0 && targetElementKey != null) {
                suggestFollowUp(targetElementKey)?.let { responseParts.add(it) }
            }

            // Check if there's a second part in the query (e.g. "and also tell me its mass")
            val conjunctions = listOf("and ", "och ", "also ", "också ", "und ", "auch ", "et ", "aussi ", "y ", "también ", "e ", "anche ")
            if (targetElementKey != null && conjunctions.any { lowerQuery.contains(it) }) {
                // Look for properties mentioned after the conjunction
                val secondPart = lowerQuery.split(*conjunctions.toTypedArray()).last()
                if (secondPart.length > 3) {
                    val additionalInfo = handleElementContextQuery(secondPart, targetElementKey)
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
     * Run the structured engine, or return null so the legacy handlers take the query.
     *
     * The engine keeps its own dialogue state, which is kept in step with the fields the rest of
     * this class uses so the two views of the conversation cannot diverge.
     */
    private fun structuredAnswer(userMessage: String): ChatMessage? {
        val engine = engineFor(activeLanguage) ?: return null

        dialogueState.focusElement = currentElement
        dialogueState.activeLanguage = activeLanguage

        val answer = try {
            engine.answer(userMessage, dialogueState)
        } catch (e: Exception) {
            Log.w(TAG, "structured engine failed, falling back", e)
            null
        } ?: return null

        dialogueState.focusElement?.let { key ->
            if (key != currentElement) {
                currentElement = key
                sharedProperties.clear()
            }
            learningManager?.trackElementInterest(key)
        }

        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = answer.text,
            isFromUser = false,
            timestamp = System.currentTimeMillis(),
            actions = ChatActionCodec.encode(answer.actions)
        )
    }

    /** The engine for a language, rebuilt only when the active language changes. */
    private fun engineFor(language: String): AiEngine? {
        cachedEngine?.let { if (cachedEngineLanguage == language) return it }
        val service = retrievalService ?: return null
        val ctx = localizedContext ?: context ?: return null
        val engine = AiEngine(
            store = service.store,
            datasets = service.datasets,
            localized = service.localized(language),
            strings = AndroidStrings(ctx, language),
            entities = service.resolver(language),
            retriever = service.retriever(language)
        )
        cachedEngine = engine
        cachedEngineLanguage = language
        return engine
    }

    /**
     * Last-resort lookup over the app's own content when no intent handler claimed the query.
     *
     * Uses BM25 plus exact entity resolution over the element table and every in-app dataset.
     * Returns null rather than a weak guess, so the caller falls through to the element handler
     * instead of answering with something unrelated.
     */
    private fun retrieveAnswer(userMessage: String): String? {
        val service = retrievalService ?: return null
        val retriever = service.retriever(activeLanguage)
        // Recent turns give the retriever the context a bare follow-up leaves implicit.
        val context = conversationHistory.takeLast(2).joinToString(" ") { it.text }
        val hit = retriever.best("$userMessage $context".take(300)) ?: return null

        return when (val ref = hit.ref) {
            is RetrievedRef.Element -> {
                currentElement = ref.key
                handleElementContextQuery(userMessage.lowercase(), ref.key)
            }
            is RetrievedRef.Dataset -> {
                val row = service.datasets.row(ref.dataset, ref.id) ?: return null
                "**${row.title}**\n${row.detail}"
            }
        }
    }

    private fun updateTopicFromIntent(lowerQuery: String) {
        currentTopic = when {
            isQuizQuery(lowerQuery) -> "quiz"
            isTrendsQuery(lowerQuery) -> "trends"
            isMolarMassQuery(lowerQuery) -> "molar_mass"
            isSafetyQuery(lowerQuery) -> "safety"
            isBiologicalQuery(lowerQuery) -> "biological"
            isReactionQuery(lowerQuery) -> "reaction"
            else -> currentTopic
        }
    }

    private fun getConversationalConnector(lowerQuery: String): String? {
        val ctx = localizedContext ?: context ?: return null
        val lastTurn = conversationHistory.lastOrNull { !it.isFromUser } ?: return null
        
        return when {
            lowerQuery.contains("why") || lowerQuery.contains("how") || lowerQuery.contains("hur") || lowerQuery.contains("varfor") || lowerQuery.contains("wie") || lowerQuery.contains("warum") || lowerQuery.contains("porque") || lowerQuery.contains("como") -> {
                listOf(
                    ctx.getString(R.string.ai_connector_why_1),
                    ctx.getString(R.string.ai_connector_why_2),
                    ctx.getString(R.string.ai_connector_why_3)
                ).random()
            }
            lastTurn.text.contains("?") -> {
                listOf(
                    ctx.getString(R.string.ai_connector_question_1),
                    ctx.getString(R.string.ai_connector_question_2),
                    ctx.getString(R.string.ai_connector_question_3)
                ).random()
            }
            else -> null
        }
    }

    private fun suggestFollowUp(elementKey: String): String? {
        val ctx = localizedContext ?: context ?: return null
        
        // Define suggestions with their internal types
        val suggestions = listOf(
            ctx.getString(R.string.ai_suggestion_bio) to "bio",
            ctx.getString(R.string.ai_suggestion_iso) to "iso",
            ctx.getString(R.string.ai_suggestion_compare) to "compare",
            ctx.getString(R.string.ai_suggestion_safety) to "safety",
            ctx.getString(R.string.ai_suggestion_abundance) to "abundance"
        )
        
        // Filter out what we already covered
        val available = suggestions.filter { (text, type) ->
            when (type) {
                "bio" -> !sharedProperties.contains("biological")
                "iso" -> !sharedProperties.contains("isotopes")
                "safety" -> !sharedProperties.contains("safety")
                "abundance" -> !sharedProperties.contains("abundance")
                else -> true
            }
        }
        
        return if (available.isNotEmpty()) {
            val picked = available.random()
            lastSuggestionType = picked.second
            "\n\n*${picked.first}*"
        } else null
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
            val normalizedWord = normalizeForLookup(word)
            
            // Skip common word collisions
            if (isCommonWordCollision(word, query, words) || isCommonWordCollision(normalizedWord, query, words)) continue

            // Check cross-language map with normalization
            val englishKey = localizedElementMap[word]
                ?: localizedElementMap[normalizedWord]
            if (englishKey != null && !seenKeys.contains(englishKey)) {
                val element = data.optJSONObject(englishKey)
                if (element != null) {
                    found.add(element)
                    seenKeys.add(englishKey)
                    continue
                }
            }
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



    private fun isAffirmative(query: String): Boolean {
        val keywords = listOf(
            // English
            "yes", "sure", "ok", "okay", "yep", "yeah", "absolutely", "please", "do it", "more", "next", "tell me more",
            // Swedish
            "ja", "visst", "okej", "gärna", "absolut", "snälla", "gör det", "mer", "nästa", "berätta mer", "annat", "något annat", "nåt annat", "vidare",
            // German
            "jawohl", "sicher", "gerne", "bitte", "mehr", "erzähl mir mehr", "erzählen sie mir mehr",
            // French
            "oui", "bien sûr", "d'accord", "s'il vous plaît", "plus", "dis-m'en plus", "dites-m'en plus",
            // Spanish
            "sí", "claro", "por supuesto", "por favor", "más", "cuéntame más", "cuénteme más",
            // Portuguese
            "sim", "com certeza", "por favor", "mais", "conta-me mais", "me conte mais",
            // Hindi / Urdu
            "हाँ", "ज़रूर", "ठीक है", "ji", "ha", "zaroor", "thik hai", "aur", "zhada", "mujhe aur batao", "mujhe aur batayein", "mazeed", "mazeed batayein",
            // Chinese
            "是", "是的", "当然", "好的", "shi", "shide", "dangran", "haode", "geng duo", "jixu", "更多", "继续", "告诉我更多", "多跟我说说",
            // Filipino
            "oo", "sige", "oo naman", "walang anuman", "pa", "dagdag", "magkwento ka pa", "sabihin mo pa",
            // Italian
            "ancora", "di piu", "altro", "continua", "dimmi di più", "mi dica di più",
            // Afrikaans
            "meer", "vertel my meer", "sekerlik", "asseblief",
            // Extra English
            "something else", "anything else", "other", "another"
        )
        // Check if it's a very short response or exact phrase
        return keywords.any { query == it || query.startsWith("$it ") || query.endsWith(" $it") } && query.length < 30
    }

    private fun isTrendsQuery(query: String): Boolean {
        val keywords = listOf(
            "trend", "pattern", "change", "variation", "tendance", "tendência", "tendencia", "趋势", "qushi", "rujhan", "tendens", "periodiska trender",
            "mönster", "förändring", "variations", "comportement", "comportamento", "padrão", "patrón", "propriedades periódicas", "propiedades periódicas",
            "periodicidade", "periodicidad", "periodicity", "periodicità", "andamento", "verlauf", "änderung", "periodische", "प्रवृत्ति", "पैटर्न", "बदलाव",
            "رجحان", "تبدیلی", "karakteristik", "kenang", "pagbabago", "uri", "neigong", "patroon"
        )
        // If query is short, only match if it clearly means trends or if element context is missing
        if (query.length < 5 && currentElement != null) return false
        return keywords.any { query.contains(it) } || 
               ((query.contains("periodic table") || query.contains("periodiska systemet") || query.contains("periodensystem") || query.contains("tableau périodique") || query.contains("tabla periódica") || query.contains("आवर्त सारणी")) && 
                (query.contains("how") || query.contains("change") || query.contains("hur") || query.contains("ändras") || query.contains("wie") || query.contains("comment") || query.contains("cómo") || query.contains("कैसे")))
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
        val keywords = listOf(
            "molar mass", "molecular weight", "atomic mass of", "mass of", "molmassa", "atommassa", 
            "molare masse", "masse molaire", "masa molar", "massa molar", "मोलर द्रव्यमान", "摩尔质量", "moer zhiliang", "moler mass",
            "poids moléculaire", "poids atomique", "peso molecolare", "peso atomico", "peso molecular", "peso atómico", "atomgewicht",
            "molekulargewicht", "molmassa", "molmassa", "vikt", "massa", "massa atomica", "massa molecolare", "moleweight", "atomweight",
            "atomaire massa", "molêre massa", "molêre gewig", "atomaire gewig", "molar na timbang", "molar na masa", "atomikong masa"
        )
        val formulaKeywords = listOf("compound", "formula", "formel", "pormula", "fórmula", "formule", "molecule", "molekyl", "molekül", "composto", "composto", "fórmulas", "molecula", "molécula", "compuesto", "samenstelling")
        return keywords.any { query.contains(it) } || (query.contains("mass") && formulaKeywords.any { query.contains(it) })
    }

    private fun handleMolarMassQuery(query: String): String {
        val ctx = localizedContext ?: context!!
        val isProPlus = rateLimiter?.isProPlus() ?: false
        
        // Extract the target (formula or name) after keywords while preserving casing
        val lower = query.lowercase()
        val keywords = listOf("calculate atomic mass of ", "atomic mass of ", "molar mass of ", "molecular weight of ", "mass of ", "molmassa på ", "för ", "på:", "of ")
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
            val userType = if (rateLimiter?.isPro() == true) ctx.getString(R.string.ai_pro_user) else ctx.getString(R.string.ai_free_user)
            return ctx.getString(R.string.ai_molar_mass_pro_plus, userType)
        }

        val formula = target.replace(" ", "")
        val calculatedMass = molarMassCalculator?.calculate(formula)
        if (calculatedMass != null && calculatedMass > 0.0) {
            lastFormulaTarget = formula
            val formattedMass = String.format(java.util.Locale.US, "%.3f", calculatedMass)
            var resp = ctx.getString(R.string.ai_molar_mass_of, formula, "$formattedMass g/mol")
            resp += ctx.getString(R.string.ai_formula_composition_offer)
            return resp
        }

        return ctx.getString(R.string.ai_molar_mass_generic)
    }

    private fun isQuizQuery(query: String): Boolean {
        val keywords = listOf(
            "quiz", "question", "test", "frågesport", "tävling", "rätsel", "examen", "प्रश्नोttari", "测验", "ceyan", "sawal", "toets",
            "pagsusulit", "larong pang-edukasyon", "tanong", "preguntas", "pregunta", "juego", "domanda", "quesito", "interrogazione",
            "frage", "prüfung", "testa mig", "interroger", "questionnaire", "concours", "pergunta", "questão", "desafio", "testar",
            "fråga", "सवाल", "امتحان", "vrae", "vraag", "toetsie"
        )
        return keywords.any { query.contains(it) }
    }

    private fun handleQuizAnswer(query: String): String {
        val answer = currentQuizAnswer ?: return localizedContext?.getString(R.string.ai_quiz_not_active) ?: "Oops!"
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
        val data = elementData ?: return localizedContext?.getString(R.string.ai_quiz_no_start) ?: "Oops!"
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



    /**
     * Reactivity questions, in either the superlative or comparison form.
     *
     * Reactivity is not a field in the element data; it is derived from group and position by
     * [handleReactivitySuperlative] and [compareReactivity]. The structured engine only plans
     * over fields that exist, so these are routed here rather than through it.
     */
    private fun isReactivityQuery(query: String): Boolean =
        hasKeyword(
            query,
            listOf(
                "reactive", "reactivity", "reaktiv", "reaktivitet", "reaktivaste",
                "reaktivität", "reactivo", "reactividad", "réactif", "réactivité",
                "reattivo", "reattività", "reativo", "reatividade", "reaktief",
                "reaktibo", "sakriya", "fa'aal", "活泼", "反应性"
            )
        )

    private fun handleReactivityQuery(query: String): String {
        val elements = findMultipleElements(query)
        if (elements.size >= 2) return compareReactivity(elements)
        val wantsLeast = hasKeyword(
            query,
            listOf("least", "lowest", "minst", "lägst", "wenigste", "menos", "moins", "meno", "最不")
        )
        return handleReactivitySuperlative(query, findMax = !wantsLeast)
    }

    private fun handleReactivitySuperlative(query: String, findMax: Boolean = true): String {
        val data = elementData ?: return "---"
        val ctx = localizedContext ?: context!!
        
        // Find series filter
        val seriesId = when {
            query.contains("noble gas") -> "Noble Gas"
            query.contains("alkali") -> "Alkali Metal"
            query.contains("halogen") -> "Halogen"
            else -> null
        }
        
        val seriesLabel = when (seriesId) {
            "Noble Gas" -> ctx.getString(R.string.ai_series_noble_gas_label)
            "Alkali Metal" -> ctx.getString(R.string.ai_series_alkali_metal_label)
            "Halogen" -> ctx.getString(R.string.ai_series_halogen_label)
            else -> ctx.getString(R.string.ai_element)
        }

        var bestScore = if (findMax) -1 else Int.MAX_VALUE
        var bestName = ""

        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val element = data.optJSONObject(key) ?: continue
            val name = element.optString("element", "")
            val group = element.optString("element_group", "")
            val number = element.optInt("element_atomic_number", 0)

            if (seriesId != null && !group.contains(seriesId, ignoreCase = true)) continue

            // Reuse existing reactivity score logic
            val score = when {
                name.lowercase() == "fluorine" -> 200
                name.lowercase() == "cesium" || name.lowercase() == "francium" -> 190
                group == "Alkali Metal" -> 100 + number
                group == "Alkaline Earth Metal" -> 80 + number
                group == "Halogen" -> 100 - number
                group == "Noble Gas" -> 0
                else -> 50
            }

            if (findMax) {
                if (score > bestScore) {
                    bestScore = score
                    bestName = name
                }
            } else {
                if (score < bestScore) {
                    bestScore = score
                    bestName = name
                }
            }
        }

        return if (bestName.isNotEmpty()) {
            if (findMax) ctx.getString(R.string.ai_reactivity_result, seriesLabel, bestName)
            else ctx.getString(R.string.ai_reactivity_result, seriesLabel, bestName).replace(ctx.getString(R.string.highest), ctx.getString(R.string.lowest)) // Heuristic for "least" if no specific string
        } else ctx.getString(R.string.ai_reactivity_no_result)
    }

    private fun isMultiPropertyQuery(query: String): Boolean {
        // Look for multiple property keywords or conjunctions like "and", "plus", "also"
        val count = propertyKeywords.count { query.contains(it) }
        return count >= 2 || (count >= 1 && (query.contains(" and ") || query.contains(" & ") || query.contains(" och ")))
    }

    private val propertyKeywords = listOf(
        "mass", "weight", "density", "boil", "melt", "atomic", "number", "symbol", "discovered", "discoverer", "year", 
        "radioactive", "radiation", "phase", "state", "color", "appearance", "proton", "electron", "neutron", "config", 
        "shell", "oxidation", "charge", "electronegativity", "block", "radius", "abundance", "crystal", "thermal", 
        "conduct", "hardness", "modulus", "usage", "origin", "biological", "ionization", "ionisation", "heat", "superconductivity",
        "massa", "vikt", "densit", "kokpunkt", "smältpunkt", "atomnummer", "upptäckt", "färg", "utseende", "jonisering", "radioaktivitet", "radioaktivt",
        "värmekapacitet", "smältvärme", "ångbildningsvärme", "supraledning", "supraledare", "supraledande", "hårdhet", "användning",
        "poids", "masse", "densité", "ébullition", "fusion", "découverte", "couleur", "apparence",
        "peso", "densidad", "ebullición", "descubrimiento", "color", "apariencia", "número", "símbolo", "ionización",
        "massa", "peso", "densità", "ebollizione", "fusione", "scoperta", "colore", "apparenza",
        "gewicht", "dichte", "siedepunkt", "schmelzpunkt", "ordnungszahl", "entdeckung", "farbe", "aussehen", "ionisierung",
        "द्रव्यमान", "वजन", "घनत्व", "परमाणु", "नंबर", "प्रतीक", "खोज", "रंग", "उपस्थिति",
        "massa", "timbang", "densidad", "pormula", "simbolo", "natuklasan", "hitsura", "kulay"
    )

    private fun handleMultiPropertyQuery(elementName: String, query: String): String {
        val element = elementData?.optJSONObject(elementName.lowercase()) ?: return AIPersonality.getNoDataResponse(context!!, activeLanguage, "")
        val results = mutableListOf<String>()
        val ctx = localizedContext ?: context!!

        // Specifically check for each property mentioned in the query
        if (query.contains("mass") || query.contains("weight")) {
            val mass = element.optString("element_atomicmass", "---")
            if (mass != "---") results.add("• **${ctx.getString(R.string.atomic_mass_colon).replace(":","")}**: $mass")
        }
        if (query.contains("density")) {
            val dens = element.optString("element_density", "---")
            if (dens != "---") results.add("• **${ctx.getString(R.string.density_colon).replace(":","")}**: $dens")
        }
        if (query.contains("number") || query.contains("proton")) {
            val num = element.optString("element_atomic_number", "---")
            if (num != "---") results.add("• **${ctx.getString(R.string.atomic_number_label).replace(":","")}**: $num")
        }
        if (query.contains("boil")) {
            val boil = element.optString("element_boiling_celsius", "---")
            if (boil != "---") results.add("• **${ctx.getString(R.string.boiling_point_colon).replace(":","")}**: $boil°C")
        }
        if (query.contains("melt")) {
            val melt = element.optString("element_melting_celsius", "---")
            if (melt != "---") results.add("• **${ctx.getString(R.string.melting_point_colon).replace(":","")}**: $melt°C")
        }
        if (query.contains("config") || query.contains("shell")) {
            val conf = element.optString("element_electron_config", "---")
            if (conf != "---") results.add("• **${ctx.getString(R.string.electron_configuration_colon).replace(":","")}**: $conf")
        }
        if (query.contains("discover") || query.contains("history")) {
            val disc = element.optString("element_discovered_name", "---")
            val year = element.optString("element_year", "---")
            if (disc != "---") results.add("• **${ctx.getString(R.string.discovered_by_colon).replace(":","")}**: $disc ($year)")
        }
        if (query.contains("radius")) {
            val rad = element.optString("element_atomic_radius_e", "---")
            if (rad != "---") results.add("• **${ctx.getString(R.string.atomic_radius_empirical_colon).replace(":","")}**: $rad pm")
        }
        if (query.contains("negativity")) {
            val neg = element.optString("element_electronegativty", "---")
            if (neg != "---") results.add("• **${ctx.getString(R.string.electronegativity_colon).replace(":","")}**: $neg")
        }

        return if (results.isNotEmpty()) {
            val header = ctx.getString(R.string.ai_list_header, elementName)
            "${AIPersonality.getEncouragement(ctx, activeLanguage)} $header\n\n" + results.joinToString("\n")
        } else {
            handleElementContextQuery(query, elementName)
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
        val keywords = listOf(
            "safety", "hazard", "dangerous", "flammable", "toxic", "poison", "nfpa", 
            "fara", "risker", "farlig", "giftig", "brännbar", "brandfarlig",
            "gefahr", "gefährlich", "giftig", "brennbar", "toxisch", "sicherheit",
            "sécurité", "danger", "toxique", "inflammable", "poison",
            "seguridad", "peligro", "tóxico", "veneno", "inflamable",
            "sicurezza", "pericolo", "tossico", "veleno", "infiammabile",
            "segurança", "perigo", "tóxico", "veneno", "inflamável",
            "सुरक्षा", "खतरा", "जहरीला", "ज्वलनशील",
            "安全", "危险", "毒", "易燃", "anquan", "weixian", "du", "yiran",
            "hifazat", "khatra", "zeher", "veiligheid", "panganib", "kaligtasan"
        )
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
            val radio = element.optString("radioactive", "").lowercase().trim()
            val isActuallyRadioactive = radio.isNotEmpty() && radio != "no" && radio != "none" && radio != "false" && radio != "---" && !radio.contains("non-radioactive")
            
            return if (isActuallyRadioactive) {
                ctx.getString(R.string.ai_safety_radioactive, elementName, radio)
            } else {
                ctx.getString(R.string.ai_safety_no_data, elementName)
            }
        }

        var safetyInfo = ctx.getString(R.string.ai_safety_header, elementName)
        safetyInfo += "\n" + ctx.getString(R.string.ai_safety_health, health, getNFPAHealthDesc(health))
        safetyInfo += "\n" + ctx.getString(R.string.ai_safety_flammability, flammability, getNFPAFlammableDesc(flammability))
        safetyInfo += "\n" + ctx.getString(R.string.ai_safety_instability, instability)
        if (special.isNotEmpty() && special != "---") safetyInfo += "\n" + ctx.getString(R.string.ai_safety_special, special)
        
        val desc = when {
            health >= 3 -> ctx.getString(R.string.ai_safety_caution_health)
            flammability >= 3 -> ctx.getString(R.string.ai_safety_caution_flammable)
            else -> ""
        }
        
        return if (desc.isNotEmpty()) "$safetyInfo\n\n$desc" else safetyInfo
    }

    private fun getNFPAHealthDesc(level: Int): String {
        val ctx = localizedContext ?: context!!
        return when(level) {
            0 -> ctx.getString(R.string.ai_nfpa_health_0)
            1 -> ctx.getString(R.string.ai_nfpa_health_1)
            2 -> ctx.getString(R.string.ai_nfpa_health_2)
            3 -> ctx.getString(R.string.ai_nfpa_health_3)
            4 -> ctx.getString(R.string.ai_nfpa_health_4)
            else -> ctx.getString(R.string.unknown)
        }
    }

    private fun getNFPAFlammableDesc(level: Int): String {
        val ctx = localizedContext ?: context!!
        return when(level) {
            0 -> ctx.getString(R.string.ai_nfpa_flammable_0)
            1 -> ctx.getString(R.string.ai_nfpa_flammable_1)
            2 -> ctx.getString(R.string.ai_nfpa_flammable_2)
            3 -> ctx.getString(R.string.ai_nfpa_flammable_3)
            4 -> ctx.getString(R.string.ai_nfpa_flammable_4)
            else -> ctx.getString(R.string.unknown)
        }
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
                    val userType = if (rateLimiter?.isPro() == true) ctx.getString(R.string.ai_pro_user) else ctx.getString(R.string.ai_free_user)
                    return ctx.getString(R.string.ai_formula_pro_plus, clean, userType)
                }

                val mass = molarMassCalculator?.calculate(clean)
                val formattedMass = String.format(java.util.Locale.US, "%.3f", mass ?: 0.0)
                
                var response = ctx.getString(R.string.ai_formula_recognize, clean, "$formattedMass g/mol")
                
                if (lowerQuery.contains("composition") || lowerQuery.contains("breakdown") || lowerQuery.contains("contain") || lowerQuery.contains("made of")) {
                    response += "\n\n" + ctx.getString(R.string.ai_formula_consists_of)
                    for ((symbol, count) in elementCounts) {
                        val name = molarMassCalculator?.getElementName(symbol)
                        val countStr = if (count % 1.0 == 0.0) count.toInt().toString() else count.toString()
                        response += "\n" + ctx.getString(R.string.ai_formula_item, countStr, name ?: symbol, symbol)
                    }
                } else {
                    response += " Would you like me to break down its elemental composition?"
                }
                return response
            }
        }

        return ctx.getString(R.string.ai_formula_generic)
    }

    private fun isIdentityQuery(query: String): Boolean {
        val keywords = listOf(
            "which element has", "which element is", "identify element", "identify the element",
            "vilket grundämne har", "vad har atomnummer", "vilket ämne",
            "welches element hat", "welches element ist", "quel élément",
            "qué elemento tiene", "qual elemento tem", "pertsono"
        )
        return keywords.any { query.contains(it) } && (query.contains("mass") || query.contains("atomic number") || query.contains("electronegativity") || query.contains("density") || query.contains("boiling") || query.contains("melting"))
    }

    private fun handleIdentityQuery(query: String): String {
        val data = elementData ?: return "---"
        val ctx = localizedContext ?: context!!
        
        // Extract numeric value from query
        val numberMatch = Regex("(\\d+\\.?\\d*)").find(query) ?: return ctx.getString(R.string.ai_structural_specify_number)
        val targetValue = numberMatch.groupValues[1].toDoubleOrNull() ?: return ctx.getString(R.string.ai_structural_specify_number)
        
        var bestMatchKey: String? = null
        var minDiff = Double.MAX_VALUE
        var matchedProp = ""

        val props = listOf(
            "element_atomic_number" to "",
            "element_atomicmass" to "u",
            "element_density" to "g/cm³",
            "element_electronegativty" to "",
            "element_boiling_celsius" to "°C",
            "element_melting_celsius" to "°C"
        )

        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val element = data.optJSONObject(key) ?: continue
            
            for ((jsonKey, unit) in props) {
                val rawValue = element.optString(jsonKey, "---").replace(unit, "").trim()
                val value = rawValue.toDoubleOrNull() ?: continue
                
                val diff = Math.abs(value - targetValue)
                if (diff < minDiff) {
                    minDiff = diff
                    bestMatchKey = key
                    matchedProp = jsonKey
                }
            }
        }

        return if (bestMatchKey != null && minDiff < 1.0) {
            val element = data.optJSONObject(bestMatchKey)
            val name = element?.optString("element", bestMatchKey) ?: bestMatchKey
            val actualValue = element?.optString(matchedProp, "---")
            ctx.getString(R.string.ai_identity_result, targetValue.toString(), name, actualValue)
        } else {
            ctx.getString(R.string.ai_no_data)
        }
    }





    private fun isReactionQuery(query: String): Boolean {
        val keywords = listOf(
            "react", "mix", "combine", "together", "reaction",
            "reagera", "blanda", "tillsammans", "reaktion",
            "reagieren", "mischen", "zusammen", "reaktion",
            "réagir", "mélanger", "ensemble", "réaction",
            "reaccionar", "mezclar", "juntos", "reacción",
            "reagire", "mescolare", "insieme", "reazione",
            "reagir", "misturar", "junto", "reação",
            "प्रतिक्रिया", "अभिक्रिया", "मिलाएं", "साथ",
            "反应", "混合", "结合", "fanying", "hunhe",
            "radd-e-amal", "milana", "reageer", "reaksyon",
            "paggawa", "pag-react"
        )
        val reactants = listOf(
            "water", "vatten", "wasser", "eau", "agua", "acqua", "água",
            "acid", "syra", "säure", "acide", "ácido", "acido", "ácido",
            "air", "luft", "luft", "aire", "aria", "ar",
            "oxygen", "syre", "sauerstoff", "oxygène", "oxígeno", "ossigeno", "oxigênio",
            "tubig", "asido", "hangin", "oxyheno"
        )
        return keywords.any { query.contains(it) } && (findMultipleElements(query).isNotEmpty() || reactants.any { query.contains(it) })
    }

    private fun handleReactionQuery(query: String): String {
        val elements = findMultipleElements(query)
        val ctx = localizedContext ?: context!!
        
        if (elements.size < 2) {
            // Check for element + common substances (water, air, acid)
            val lower = query.lowercase()
            val element = elements.firstOrNull() ?: return AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
            val name = element.optString("element", "")
            val group = element.optString("element_group", "")
            
            return when {
                lower.contains("water") || lower.contains("vatten") || lower.contains("wasser") || lower.contains("eau") || lower.contains("agua") -> {
                    when (group) {
                        "Alkali Metal" -> ctx.getString(R.string.ai_reaction_alkali_water, name)
                        "Alkaline Earth Metal" -> {
                            if (name.lowercase() == "magnesium") ctx.getString(R.string.ai_reaction_magnesium_water, name)
                            else ctx.getString(R.string.ai_reaction_alkaline_earth_water, name)
                        }
                        else -> ctx.getString(R.string.ai_reaction_generic_water, name)
                    }
                }
                lower.contains("oxygen") || lower.contains("air") || lower.contains("syre") || lower.contains("luft") || lower.contains("sauerstoff") || lower.contains("oxygène") || lower.contains("oxígeno") -> {
                    ctx.getString(R.string.ai_reaction_oxygen, name)
                }
                lower.contains("acid") || lower.contains("syra") || lower.contains("säure") || lower.contains("acide") || lower.contains("ácido") -> {
                    ctx.getString(R.string.ai_reaction_acid, name)
                }
                else -> ctx.getString(R.string.ai_reaction_complex, name, group)
            }
        }

        val e1 = elements[0].optString("element", "")
        val g1 = elements[0].optString("element_group", "")
        val e2 = elements[1].optString("element", "")
        val g2 = elements[1].optString("element_group", "")

        return when {
            (g1 == "Alkali Metal" && g2 == "Halogen") || (g2 == "Alkali Metal" && g1 == "Halogen") -> 
                ctx.getString(R.string.ai_reaction_alkali_halogen, e1, e2)
            (g1 == "Noble Gas" || g2 == "Noble Gas") -> 
                ctx.getString(R.string.ai_reaction_noble_gas, if (g1 == "Noble Gas") e1 else e2)
            else -> ctx.getString(R.string.ai_reaction_generic_elements, e1, e2)
        }
    }








    private fun isSimilarityQuery(query: String): Boolean {
        val keywords = listOf(
            "similar", "like", "related", "equivalent", "analogy",
            "liknar", "liknande", "ähnlich", "verwandt",
            "similaire", "proche", "pareil", "similar", "parecido",
            "somigliante", "simile", "simili", "semelhante", "igual",
            "समान", "जैसा", "मिलता-जुलता", "ek jaisa", "muqabla",
            "相似", "类似", "xiangsi", "leishi", "soortgelyk", "katulad", "pareho"
        )
        return keywords.any { query.contains(it) } && (currentElement != null || findElementByQuery(query) != null)
    }

    private fun handleSimilarityQuery(query: String): String {
        val target = currentElement
        val ctx = localizedContext ?: context!!
        val element = findElementByQuery(query) ?: (if (target != null) elementData?.optJSONObject(target.lowercase()) else null)
            ?: return ctx.getString(R.string.ai_similarity_no_element)
        
        val name = element.optString("element", "")
        val group = element.optString("element_group", "")
        val groupNum = element.optString("element_group_number", "")
        
        val similar = mutableListOf<String>()
        val data = elementData ?: return "---"
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val el = data.optJSONObject(key) ?: continue
            if (el.optString("element") == name) continue
            
            if (el.optString("element_group_number") == groupNum && groupNum != "---") {
                similar.add(el.optString("element"))
            }
        }

        return if (similar.isNotEmpty()) {
            ctx.getString(R.string.ai_similarity_result_header, name, group) + similar.joinToString(", ") + ctx.getString(R.string.ai_similarity_result_footer)
        } else {
            ctx.getString(R.string.ai_similarity_generic, name, group)
        }
    }

    private fun isUsageQuery(query: String): Boolean {
        val keywords = listOf(
            "use", "used", "application", "where to find", "function", "utility", "industry", "medicine",
            "används", "nytta", "funktion", "verwendung", "nutzen", "industrie", "medizin",
            "utilisation", "usage", "industrie", "médecine",
            "uso", "utilidad", "industria", "medicina",
            "uso", "impiego", "utilidade", "indústria",
            "उपयोग", "प्रयोग", "काम", "istimal",
            "用途", "应用", "使用", "yongtu", "yingyong", "shiyong", "gebruik", "gamit", "paggamit"
        )
        return hasKeyword(query, keywords) && 
               (query.contains("what") || query.contains("how") || query.contains("vad") || query.contains("hur") || 
                query.contains("was") || query.contains("wie") || query.contains("quel") || query.contains("comment") || 
                query.contains("qué") || query.contains("cómo") || query.contains("cosa") || query.contains("come") ||
                query.contains("क्या") || query.contains("कैसे") || 
                query.contains("什么") || query.contains("shenme") || query.contains("kya") || query.contains("kaise") || query.contains("ano") || query.contains("paano"))
    }

    private fun handleUsageQuery(elementKey: String): String {
        val element = elementData?.optJSONObject(elementKey.lowercase()) ?: return AIPersonality.getNoDataResponse(context!!, activeLanguage, "")
        val elementName = element.optString("element", "---")
        val desc = element.optString("description", "")
        val ctx = localizedContext ?: context!!
        
        // Find sentences about usages
        val sentences = desc.split(".", "!", "?")
        val usageSentences = sentences.filter { s ->
            s.contains("use", ignoreCase = true) || s.contains("industry", ignoreCase = true) || s.contains("medicine", ignoreCase = true) || s.contains("application", ignoreCase = true) || s.contains("important", ignoreCase = true) || s.contains("användning", ignoreCase = true) || s.contains("viktig", ignoreCase = true)
        }

        return if (usageSentences.isNotEmpty()) {
            ctx.getString(R.string.ai_usage_header, elementName) + "\n\n" + usageSentences.joinToString(". ").trim() + "."
        } else {
            ctx.getString(R.string.ai_no_usage_data, elementName)
        }
    }

    private fun isAbundanceQuery(query: String): Boolean {
        val keywords = listOf(
            "abundance", "common", "rare", "find", "found", "crust", "universe", "ocean", "sun", "solar", "body", "earth",
            "förekomst", "vanlig", "sällsynt", "vorkommen", "häufig", "selten", "erdoberfläche", "weltall",
            "abondance", "commun", "rare", "trouver", "croûte", "univers",
            "abundancia", "común", "raro", "encontrar", "corteza", "universo",
            "abbondanza", "comune", "raro", "trovare", "crosta", "universo",
            "abundância", "comum", "raro", "encontrar", "crosta", "universo",
            "प्रचुरता", "सामान्य", "दुर्लभ", "मिलता", "पाया", "ब्रह्मांड",
            "丰度", "常见", "稀o", "发现", "地壳", "宇宙", "fengdu", "changjian", "xiyou", "fazhan", "dike", "yuzhou",
            "kashrat", "aam", "nadir", "milta", "paaya", "kainat", "hoeveelheid", "kasaganaan"
        )
        return hasKeyword(query, keywords) && 
               (query.contains("how") || query.contains("where") || query.contains("what") || query.contains("hur") || query.contains("var") || query.contains("vad") || 
                query.contains("wie") || query.contains("wo") || query.contains("was") || query.contains("où") || query.contains("dónde") || 
                query.contains("dove") || query.contains("onde") ||
                query.contains("कहाँ") || query.contains("कहां") || query.contains("कितna") || query.contains("kahan") || query.contains("kitna") || 
                query.contains("哪里") || query.contains("nali") || query.contains("waar") || query.contains("saan") || query.contains("gaano"))
    }

    private fun handleAbundanceQuery(elementKey: String): String {
        val element = elementData?.optJSONObject(elementKey.lowercase()) ?: return AIPersonality.getNoDataResponse(context!!, activeLanguage, "")
        val elementName = element.optString("element", "---")
        val ctx = localizedContext ?: context!!
        
        val crust = element.optString("earth_crust", "---")
        val sea = element.optString("sea_water", "---")
        val sun = element.optString("sun", "---")
        val universe = element.optString("solar_system", "---")
        
        val lines = mutableListOf<String>()
        if (crust != "---") lines.add("• **${ctx.getString(R.string.abundance_earth_crust)}**: $crust mg/kg")
        if (sea != "---") lines.add("• **${ctx.getString(R.string.abundance_sea_water)}**: $sea mg/L")
        if (sun != "---") lines.add("• **${ctx.getString(R.string.abundance_sun)}**: $sun ${ctx.getString(R.string.ai_abundance_relative)}")
        if (universe != "---") lines.add("• **${ctx.getString(R.string.abundance_solar_system)}**: $universe ${ctx.getString(R.string.ai_abundance_relative)}")

        return if (lines.isNotEmpty()) {
            ctx.getString(R.string.ai_abundance_header, elementName) + "\n\n" + lines.joinToString("\n")
        } else {
            val shortDesc = element.optString("description").split(".").firstOrNull() ?: ""
            ctx.getString(R.string.ai_no_abundance_data, elementName, shortDesc)
        }
    }

    private fun isPhysicalPropertyQuery(query: String): Boolean {
        val keywords = listOf(
            "thermal", "mechanical", "magnetic", "electrical", "conduct", "resistivity", "hardness", "modulus", "expansion",
            "värme", "hårdhet", "ledförmåga", "thermisch", "mechanisch", "magnetisch", "elektrisch", "leitfähigkeit", "härte",
            "thermique", "mécanique", "magnétique", "électrique", "conductivité", "dureté",
            "térmico", "mecánico", "magnético", "eléctrico", "conductividad", "dureza",
            "termico", "meccanico", "magnetico", "elettrico", "conducibilità", "durezza",
            "termico", "mecânico", "magnético", "elétrico", "condutividade", "dureza",
            "तापीय", "यांत्रिक", "चुंबकीय", "विद्युत", "चालकता", "कठोरता",
            "热", "力学", "磁", "电", "导电", "硬度", "re", "lixue", "ci", "dian", "daodian", "yingdu",
            "hararati", "maqnatisi", "barqi", "sakhti", "termiese", "katigasan"
        )
        return hasKeyword(query, keywords)
    }

    private fun handlePhysicalPropertyQuery(elementKey: String): String {
        val element = elementData?.optJSONObject(elementKey.lowercase()) ?: return AIPersonality.getNoDataResponse(context!!, activeLanguage, "")
        val elementName = element.optString("element", "Element")
        val ctx = localizedContext ?: context!!
        val lines = mutableListOf<String>()
        
        // Thermal
        val tc = element.optString("element_thermal_conductivity", "---")
        val fh = element.optString("element_fusion_heat", "---")
        if (tc != "---") lines.add("• ${ctx.getString(R.string.thermal_conductivity_colon).replace(":","")}: $tc W/(m·K)")
        if (fh != "---") lines.add("• ${ctx.getString(R.string.fusion_heat_colon).replace(":","")}: $fh kJ/mol")
        
        // Mechanical
        val mohs = element.optString("mohs_hardness", "---")
        val ym = element.optString("young_modulus", "---")
        if (mohs != "---") lines.add("• ${ctx.getString(R.string.mohs_hardness_colon).replace(":","")}: $mohs")
        if (ym != "---") lines.add("• ${ctx.getString(R.string.element_young_modulus).replace(":","")}: $ym GPa")
        
        // Electromagnetic
        val et = element.optString("electrical_type", "---")
        val mt = element.optString("magnetic_type", "---")
        if (et != "---") lines.add("• ${ctx.getString(R.string.electrical_type_colon).replace(":","")}: $et")
        if (mt != "---") lines.add("• ${ctx.getString(R.string.magnetic_type_colon).replace(":","")}: $mt")

        return if (lines.isNotEmpty()) {
            ctx.getString(R.string.ai_physical_header, elementName) + "\n\n" + lines.joinToString("\n")
        } else {
            ctx.getString(R.string.ai_no_physical_data, elementName)
        }
    }

    private fun isIsotopeQuery(query: String): Boolean {
        val keywords = listOf(
            "isotope", "half-life", "stable", "decay", "radiation",
            "isotop", "halveringstid", "stabil", "sönderfall", "strahlung",
            "isotoop", "halbwertszeit", "zerfall", "strahlung",
            "isotope", "demi-vie", "stable", "désintégration", "rayonnement",
            "isótopo", "vida media", "estable", "desintegración", "radiación",
            "isotopo", "emivita", "stabile", "decadimento", "radiazione",
            "isótopo", "meia-vida", "estável", "decaimento", "radiação",
            "समस्थानिक", "अर्ध-आयु", "स्थिर", "क्षय", "विकिरण",
            "同位素", "半衰期", "稳定", "衰变", "辐射", "tongweisu", "banshuaiqi", "wending", "shuaibian", "fushe",
            "humsaja", "aadha dor", "mustahkam", "isotoop"
        )
        return keywords.any { query.contains(it) }
    }

    private fun handleIsotopeQuery(elementKey: String): String {
        val element = elementData?.optJSONObject(elementKey.lowercase()) ?: return AIPersonality.getNoDataResponse(context!!, activeLanguage, "")
        val elementName = element.optString("element", "---")
        val ctx = localizedContext ?: context!!
        val isotopes = mutableListOf<String>()
        // Iterate through all possible isotopes (JSON has up to ~45 for some elements)
        for (i in 1..100) {
            val name = element.optString("iso_$i", "---")
            if (name != "---" && name.isNotEmpty()) {
                val halfLife = element.optString("iso_half_$i", "---")
                val decay = element.optString("decay_type_$i", "---")
                val decayText = ctx.getString(R.string.ai_isotope_decay, decay)
                isotopes.add("• **$name**: ${ctx.getString(R.string.iso_half_life_colon)} $halfLife $decayText")
            } else if (i > 7) {
                // If we hit a gap after the first 7, assume we're done
                break
            }
        }

        return if (isotopes.isNotEmpty()) {
            ctx.getString(R.string.ai_isotope_header, elementName) + "\n\n" + isotopes.joinToString("\n")
        } else {
            ctx.getString(R.string.ai_no_isotope_data, elementName)
        }
    }

    private fun isBiologicalQuery(query: String): Boolean {
        val keywords = listOf(
            "body", "blood", "health", "toxic", "poison", "biological", "medicine", "essential", "diet", "nutrition",
            "kropp", "hälsa", "giftig", "biologisk", "körper", "blut", "gesundheit", "medizin",
            "corps", "sang", "santé", "toxique", "biologique", "médecine", "essentiel",
            "cuerpo", "sangre", "salud", "tóxico", "biológico", "medicina", "esencial",
            "corpo", "sangue", "salute", "tossico", "biologico", "medicina", "essenziale",
            "corpo", "sangue", "saúde", "tóxico", "biológico", "medicina", "essencial",
            "शरीर", "रक्त", "स्वास्थ्य", "जहरीला", "जैविक", "दवा",
            "身体", "血液", "健康", "毒", "生物", "医学", "shenti", "xueye", "jiankang", "du", "shengwu", "yixue",
            "jism", "khoon", "sehat", "zeher", "biologiese", "kalusugan", "katawan", "dugo"
        )
        return hasKeyword(query, keywords)
    }

    private fun handleBiologicalQuery(elementName: String): String {
        val element = elementData?.optJSONObject(elementName.lowercase()) ?: return AIPersonality.getNoDataResponse(context!!, activeLanguage, "")
        val desc = element.optString("description", "")
        val ctx = localizedContext ?: context!!
        
        // Use heuristics to find biological info in description
        val sentences = desc.split(".", "!", "?")
        val bioSentences = sentences.filter { s ->
            isBiologicalQuery(s.lowercase()) || s.contains("living", ignoreCase = true) || s.contains("organism", ignoreCase = true) || s.contains("role", ignoreCase = true) || s.contains("body", ignoreCase = true)
        }

        return if (bioSentences.isNotEmpty()) {
            ctx.getString(R.string.ai_bio_role_header, elementName) + "\n\n" + bioSentences.joinToString(". ").trim() + "."
        } else {
            ctx.getString(R.string.ai_no_bio_data, elementName) + " " + handleSafetyQuery(elementName)
        }
    }

    private fun isEtymologyQuery(query: String): Boolean {
        val keywords = listOf(
            "name", "called", "named", "origin", "etymology", "greek", "latin", "word", "meaning",
            "namn", "ursprung", "betydelse", "name", "herkunft", "etymologie", "griechisch", "latein", "wort", "bedeutung",
            "nom", "origine", "étymologie", "grec", "latin", "mot", "signification",
            "nombre", "origen", "etimología", "griego", "latín", "palabra", "significado",
            "nome", "origine", "etimologia", "greco", "latino", "parola", "significato",
            "nome", "origem", "etimologia", "grego", "latim", "palavra", "significado",
            "नाम", "उत्पत्ति", "व्युत्पत्ति", "यूनानी", "लैटिन", "शब्द", "अर्थ",
            "名字", "叫", "起源", "语源", "希腊", "拉丁", "词", "意思", "mingzi", "jiao", "qiyuan", "yuyuan", "xila", "lading", "ci", "yisi",
            "naam", "kaha jata", "asl", "itimaloji", "naam", "pangalan", "pinagmulan", "kahulugan"
        )
        return hasKeyword(query, keywords) && 
               (query.contains("why") || query.contains("how") || query.contains("where") || query.contains("hur") || query.contains("varför") || 
                query.contains("warum") || query.contains("wie") || query.contains("wo") || query.contains("pourquoi") || query.contains("comment") || 
                query.contains("por qué") || query.contains("cómo") || query.contains("क्यों") || query.contains("कैसे") || 
                query.contains("为什么") || query.contains("weishenme") || query.contains("kyun") || query.contains("kaise") || query.contains("hoekom") || query.contains("bakit") || query.contains("paano"))
    }

    private fun handleEtymologyQuery(elementName: String): String {
        val ctx = localizedContext ?: context!!
        val element = elementData?.optJSONObject(elementName.lowercase()) ?: return AIPersonality.getNoDataResponse(ctx, activeLanguage, "")
        val desc = element.optString("description", "")
        
        // Find sentences about naming
        val sentences = desc.split(".", "!", "?")
        val namingSentences = sentences.filter { s ->
            s.contains("name", ignoreCase = true) || s.contains("greek", ignoreCase = true) || s.contains("latin", ignoreCase = true) || s.contains("word", ignoreCase = true) || s.contains("from", ignoreCase = true) || s.contains("namn", ignoreCase = true) || s.contains("grek", ignoreCase = true) || s.contains("latin", ignoreCase = true) || s.contains("ord", ignoreCase = true)
        }

        return if (namingSentences.isNotEmpty()) {
            ctx.getString(R.string.ai_etymology_header, elementName) + "\n\n" + namingSentences.joinToString(". ").trim() + "."
        } else {
            val discoverer = element.optString("element_discovered_name")
            val year = element.optString("element_year")
            ctx.getString(R.string.ai_no_etymology_data, elementName, discoverer, year)
        }
    }

    private fun isConceptQuery(query: String): Boolean {
        val keywords = listOf(
            "what is", "define", "explain", "meaning of", "describe", "tell me about",
            "vad är", "definiera", "förklara", "was ist", "definieren", "erklären",
            "c\'est quoi", "définir", "expliquer", "qué es", "definir", "explicar",
            "cos\'è", "che cosa è", "cos è", "o que é", "o que e",
            "क्या है", "परिभाषा", "समझाएं",
            "什么是", "定义", "解释", "shenme shi", "dingyi", "jieshi",
            "kya hai", "wazahat", "bayan", "wat is", "ano ang"
        )
        val concepts = listOf(
            "radioactivity", "isotope", "period", "group", "shell", "orbital", "valence", "proton", "neutron", "electron", 
            "electronegativity", "ionization", "density", "mass", "radioaktiv", "isotop", "skal", "valens", "elektron", "atomnummer",
            "radioactivité", "électronégativité", "ionisation", "électron", "proton", "neutron", "période", "groupe",
            "radioattività", "elettronegatività", "ionizzazione", "elettrone", "protone", "neutrone", "periodo", "gruppo",
            "radioactividad", "electronegatividad", "ionización", "electrón", "protón", "neutrón", "periodo", "grupo",
            "radioatividade", "eletronegatividade", "ionização", "elétron", "próton", "nêutron", "período", "grupo",
            "radioaktivität", "elektronegativität", "ionisierung", "elektron", "proton", "neutron", "periode", "gruppe",
            "radyaktibidad", "isotopo", "proton", "elektron", "neutron", "masa", "densidad"
        )
        return keywords.any { query.contains(it) } || (query.split(" ").size < 4 && concepts.any { query.contains(it) })
    }

    private fun isPropertyOnlyQuery(query: String): Boolean {
        return query.length < 25 && hasKeyword(query, propertyKeywords)
    }

    private fun handleConceptQuery(query: String): String {
        val lower = query.lowercase()
        val ctx = localizedContext ?: context!!
        
        return when {
            lower.contains("isotope") || lower.contains("isotop") -> ctx.getString(R.string.ai_concept_isotope)
            lower.contains("electronegativity") || lower.contains("elektronegativitet") -> ctx.getString(R.string.ai_concept_electronegativity)
            lower.contains("ionization") || lower.contains("jonisering") -> ctx.getString(R.string.ai_concept_ionization)
            lower.contains("radioactiv") || lower.contains("radioaktiv") -> ctx.getString(R.string.ai_concept_radioactivity)
            lower.contains("valence") || lower.contains("valens") -> ctx.getString(R.string.ai_concept_valence)
            lower.contains("shell") || lower.contains("skal") -> ctx.getString(R.string.ai_concept_shell)
            lower.contains("orbital") -> ctx.getString(R.string.ai_concept_orbital)
            lower.contains("proton") -> ctx.getString(R.string.ai_concept_proton)
            lower.contains("neutron") -> ctx.getString(R.string.ai_concept_neutron)
            lower.contains("electron") || lower.contains("elektron") -> ctx.getString(R.string.ai_concept_electron)
            lower.contains("atomic number") || lower.contains("atomnummer") -> ctx.getString(R.string.ai_concept_atomic_number)
            lower.contains("period") -> ctx.getString(R.string.ai_concept_period)
            lower.contains("group") || lower.contains("grupp") -> ctx.getString(R.string.ai_concept_group)
            else -> ctx.getString(R.string.ai_concept_generic)
        }
    }

    private fun isHelpQuery(query: String): Boolean {
        val keywords = listOf(
            "help", "what can you do", "commands", "how to use", "options", 
            "hjälp", "vad kan du göra", "hilfe", "was kannst du tun",
            "aide", "que peux-tu faire", "ayuda", "qué puedes hacer",
            "मदद", "सहायता", "तुम क्या कर सकते हो",
            "帮助", "你能做什么", "bangzhu", "madad", "hulp"
        )
        return keywords.any { query.contains(it) }
    }

    private fun handleHelpQuery(): String {
        val ctx = localizedContext ?: context!!
        return ctx.getString(R.string.ai_help_response)
    }

    private fun compareReactivity(elements: List<JSONObject>): String {
        val ctx = localizedContext ?: context!!
        if (elements.size < 2) return ctx.getString(R.string.ai_reactivity_need_two)
        
        val e1 = elements[0].optString("element", "")
        val g1 = elements[0].optString("element_group", "")
        val n1 = elements[0].optInt("element_atomic_number", 0)
        
        val e2 = elements[1].optString("element", "")
        val g2 = elements[1].optString("element_group", "")
        val n2 = elements[1].optInt("element_atomic_number", 0)

        // Heuristics for reactivity
        fun getReactivityScore(group: String, number: Int, name: String): Int {
            if (name.lowercase() == "fluorine") return 200 // The king of reactivity
            if (name.lowercase() == "cesium" || name.lowercase() == "francium") return 190

            return when (group) {
                "Alkali Metal" -> 100 + number // Increases down the group
                "Alkaline Earth Metal" -> 80 + number
                "Halogen" -> 100 - number // Decreases down the group
                "Noble Gas" -> 0
                else -> 50
            }
        }

        val s1 = getReactivityScore(g1, n1, e1)
        val s2 = getReactivityScore(g2, n2, e2)

        return when {
            s1 > s2 -> ctx.getString(R.string.ai_reactivity_more, e1, e2)
            s2 > s1 -> ctx.getString(R.string.ai_reactivity_more, e2, e1)
            else -> ctx.getString(R.string.ai_reactivity_similar, e1, e2)
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
    
    /**
     * Answer a question about a specific element.
     *
     * Field lookups - a single property, or a whole family of them - are handled by the
     * structured engine, which resolves them from [com.jlindemann.science.ai.data.FieldRegistry]
     * with typed values, unit awareness, citations and an honest answer when the value is
     * absent. This used to be roughly thirty hand-written keyword branches mapping to JSON keys;
     * the registry covers those and about fifty more fields, in every shipped language.
     *
     * What remains here is narrative rather than data: the overview, the "tell me more" pacing
     * that avoids repeating what was already said, and the no-data reply.
     */
    private fun handleElementContextQuery(query: String, elementKey: String): String {
        val ctx = localizedContext ?: context!!
        return try {
            val element = elementData?.optJSONObject(elementKey.lowercase())
                ?: return AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
            val lowerQuery = query.lowercase()
            val elementName = element.optString("element", "Element")

            val isMoreRequest = hasKeyword(
                lowerQuery,
                listOf(
                    "additional", "extra", "further", "tell me more", "what else", "deep dive",
                    "keep going", "next", "continue", "anything else", "more info", "mer info",
                    "berätta mer", "plus", "mehr", "más", "mais", "altro", "karagdagan", "aur"
                )
            )
            if (isMoreRequest) return provideNewInformation(element, elementName)

            structuredFieldAnswer(query, elementKey)?.let { return it }

            val isOverview = hasKeyword(lowerQuery, overviewKeywords) ||
                    lowerQuery.contains("tell me about") || lowerQuery.length < 3 ||
                    !sharedProperties.contains("overview")
            val isFull = hasKeyword(lowerQuery, fullRequestKeywords)
            if (isOverview || isFull) return provideOverview(element, elementName, isFull)

            if (isAffirmative(lowerQuery) || hasKeyword(
                    lowerQuery,
                    listOf("more", "further", "additional", "mer", "vidare", "plus", "annat",
                        "other", "mehr", "más", "mais", "altro")
                )
            ) {
                return provideNewInformation(element, elementName)
            }
            AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
        } catch (e: Exception) {
            AIPersonality.getNoDataResponse(ctx, activeLanguage, query)
        }
    }

    /**
     * Ask the engine for a field or field-family answer about a known element.
     * Returns null when the query is not a field lookup, so the caller falls back to narrative.
     */
    private fun structuredFieldAnswer(query: String, elementKey: String): String? {
        val engine = engineFor(activeLanguage) ?: return null
        dialogueState.focusElement = elementKey
        dialogueState.activeLanguage = activeLanguage
        val plan = engine.plan(query, dialogueState)
        if (plan.intent != Intent.PROPERTY_LOOKUP && plan.intent != Intent.CATEGORY_LOOKUP) return null
        return try {
            engine.answer(query, dialogueState)?.text
        } catch (e: Exception) {
            Log.w(TAG, "structured field answer failed", e)
            null
        }
    }

    private fun provideOverview(element: JSONObject, elementName: String, isFull: Boolean = false): String {
        val symbol = element.optString("short", "")
        val atomicNum = element.optString("element_atomic_number", "")
        val type = element.optString("element_type", "")
        val mass = element.optString("element_atomicmass", "")
        val phase = element.optString("element_phase", "")
        val description = element.optString("description", "")
        val ctx = localizedContext ?: context!!

        val sections = mutableListOf<Pair<Int, String>>()

        // 1. Basic Facts (Always included first)
        val basicFacts = StringBuilder()
        basicFacts.append("• **${ctx.getString(R.string.atomic_number_label).replace(":","")}**: $atomicNum\n")
        if (symbol.isNotEmpty()) basicFacts.append("• **${ctx.getString(R.string.element_symbols)}**: $symbol\n")
        if (mass.isNotEmpty() && mass != "---") basicFacts.append("• **${ctx.getString(R.string.atomic_mass_colon).replace(":","")}**: $mass\n")
        if (phase.isNotEmpty() && phase != "---") basicFacts.append("• **${ctx.getString(R.string.phase_stp_colon).replace(":","")}**: $phase\n")
        if (type.isNotEmpty() && type != "---") basicFacts.append("• **${ctx.getString(R.string.type_label).replace(":","")}**: $type")
        sections.add(R.string.ai_rich_overview_basic_facts to basicFacts.toString())

        // 2. Extra Sections Pool
        val sentences = description.split(Regex("(?<=[.!?])\\s+"))
        val extraPool = mutableListOf<Pair<Int, String>>()
        
        // --- Where Found / Abundance ---
        val foundInfo = StringBuilder()
        val crust = element.optString("earth_crust", "---")
        if (crust != "---") foundInfo.append("• **${ctx.getString(R.string.abundance_earth_crust)}**: $crust mg/kg\n")
        val foundSentences = sentences.filter { s ->
            sectionKeywords["abundance"]?.any { s.contains(it, true) } == true
        }
        if (foundSentences.isNotEmpty()) foundInfo.append("• ${foundSentences.first()}")
        if (foundInfo.isNotEmpty()) extraPool.add(R.string.ai_rich_overview_where_found to foundInfo.toString())

        // --- Applications ---
        val appSentences = sentences.filter { s ->
            sectionKeywords["usage"]?.any { s.contains(it, true) } == true
        }
        if (appSentences.isNotEmpty()) extraPool.add(R.string.ai_rich_overview_applications to "• ${appSentences.take(if (isFull) 4 else 2).joinToString("\n• ")}")

        // --- Safety & Hazards ---
        val health = element.optInt("health", -1)
        val flame = element.optInt("flammability", -1)
        if (health != -1 || flame != -1) {
            val safetyInfo = StringBuilder()
            if (health != -1) safetyInfo.append("• **Health**: $health/4 (${getNFPAHealthDesc(health)})\n")
            if (flame != -1) safetyInfo.append("• **Flammability**: $flame/4")
            extraPool.add(R.string.ai_rich_overview_safety to safetyInfo.toString().trim())
        }

        // --- Advanced Atomic Data ---
        val ea = element.optString("electron_affinity", "---")
        val wf = element.optString("work_function", "---")
        val configuration = element.optString("element_electron_config", "---")
        if (ea != "---" || configuration != "---") {
            val advInfo = StringBuilder()
            if (configuration != "---") advInfo.append("• **Config**: $configuration\n")
            if (ea != "---") advInfo.append("• **Electron Affinity**: $ea")
            extraPool.add(R.string.ai_rich_overview_advanced to advInfo.toString().trim())
        }

        // --- Origin & History ---
        val etySentences = sentences.filter { s ->
            sectionKeywords["history"]?.any { s.contains(it, true) } == true
        }
        if (etySentences.isNotEmpty()) extraPool.add(R.string.ai_rich_overview_etymology to "• ${etySentences.first()}")

        // --- Biological Role ---
        val bioSentences = sentences.filter { s ->
            sectionKeywords["biological"]?.any { s.contains(it, true) } == true
        }
        if (bioSentences.isNotEmpty()) extraPool.add(R.string.ai_rich_overview_biological to "• ${bioSentences.first()}")

        // --- Common Compounds ---
        val formulaRegex = Regex("\\b[A-Z][a-z]?\\d*[A-Z][a-z]?\\d*\\b")
        val compounds = formulaRegex.findAll(description).map { it.value }.distinct().filter { it.length > 2 }.toList()
        if (compounds.isNotEmpty()) {
            extraPool.add(R.string.ai_rich_overview_compounds to "• ${compounds.take(if (isFull) 10 else 5).joinToString(", ")}")
        }
        
        // --- Notable Isotopes (Pick up to 3 or 5 if full) ---
        val isotopes = mutableListOf<String>()
        val numIsotopes = if (isFull) 5 else 3
        for (i in 1..numIsotopes) {
            val isoName = element.optString("iso_$i", "---")
            if (isoName != "---" && isoName.isNotEmpty()) {
                val halfLife = element.optString("iso_half_$i", "")
                isotopes.add("• **$isoName** ($halfLife)")
            }
        }
        if (isotopes.isNotEmpty()) {
            extraPool.add(R.string.ai_rich_overview_isotopes to isotopes.joinToString("\n"))
        }

        // Pick 3-5 extra sections randomly, or include all if isFull is true
        if (isFull) {
            sections.addAll(extraPool)
        } else {
            // If we've already given an overview before, try to give a different mix of facts
            extraPool.shuffle()
            val numToTake = if (sharedProperties.contains("overview")) (4..6).random() else (3..5).random()
            sections.addAll(extraPool.take(numToTake.coerceAtMost(extraPool.size)))
        }

        // Mark overview as shared
        sharedProperties.add("overview")
        sharedProperties.add("symbol")
        sharedProperties.add("atomic number")

        var response = AIPersonality.formatRichOverview(ctx, activeLanguage, elementName, sections)
        
        // Add a randomized conversational ending or fun fact for variety
        if (!isFull) {
            val randomFact = AIPersonality.getRandomFact(ctx, activeLanguage)
            response += "\n\n💡 **${ctx.getString(R.string.ai_pro_tip_hint)}**: $randomFact"
        }
        
        // Add a context-aware follow-up suggestion at the end
        val suggestion = suggestFollowUp(elementName.lowercase())?.let { "\n\n$it" } ?: ""
        
        return response + suggestion
    }

    /**
     * Provide a piece of information that hasn't been shared yet in this conversation.
     * Can provide multiple related facts if Technical Preference is high.
     */
    private fun provideNewInformation(element: JSONObject, elementName: String): String {
        val ctx = localizedContext ?: context!!
        val potentialProperties = listOf(
            ctx.getString(R.string.atomic_mass_colon).replace(":","") to "element_atomicmass",
            ctx.getString(R.string.boiling_point_colon).replace(":","") to "element_boiling_celsius",
            ctx.getString(R.string.melting_point_colon).replace(":","") to "element_melting_celsius",
            ctx.getString(R.string.density_colon).replace(":","") to "element_density",
            ctx.getString(R.string.electron_configuration_colon).replace(":","") to "element_electron_config",
            ctx.getString(R.string.electronegativity_colon).replace(":","") to "element_electronegativty",
            ctx.getString(R.string.ion_charge_colon).replace(":","") to "element_ion_charge",
            ctx.getString(R.string.ionization_energies_colon).replace(":","") to "element_ionization_energy1",
            ctx.getString(R.string.magnetic_type_colon).replace(":","") to "magnetic_type",
            ctx.getString(R.string.electrical_type_colon).replace(":","") to "electrical_type",
            ctx.getString(R.string.block_colon).replace(":","") to "element_block",
            ctx.getString(R.string.protons).replace(":","") to "element_protons",
            ctx.getString(R.string.neutrons).replace(":","") to "element_neutron_common",
            ctx.getString(R.string.element_electrons).replace(":","") to "element_electrons",
            ctx.getString(R.string.element_young_modulus).replace(":","") to "young_modulus",
            ctx.getString(R.string.element_poisson_ratio).replace(":","") to "poisson_ratio",
            ctx.getString(R.string.abundance_earth_crust).replace(":","") to "earth_crust",
            ctx.getString(R.string.crystal_structure).replace(":","") to "crystal_structure",
            ctx.getString(R.string.cas_number).replace(":","") to "cas_number",
            ctx.getString(R.string.molar_volume_colon).replace(":","") to "molar_volume",
            ctx.getString(R.string.thermal_conductivity_colon).replace(":","") to "element_thermal_conductivity",
            ctx.getString(R.string.year_discovered_colon).replace(":","") to "element_year"
        )
        
        // Find properties we haven't shared yet
        val availableNewFacts = potentialProperties.filter { !sharedProperties.contains(it.first) }.toMutableList()
        
        if (availableNewFacts.isEmpty()) {
            // If everything is shared, give a fun fact instead
            return "${AIPersonality.getEncouragement(ctx, activeLanguage)} ${ctx.getString(R.string.ai_covered_all, elementName)} Did you know? ${AIPersonality.getRandomFact(ctx, activeLanguage)}"
        }

        // Try to pick a "logical next step" if possible
        val lastProp = sharedProperties.lastOrNull()
        val preferredIdx = when (lastProp) {
            ctx.getString(R.string.boiling_point_colon).replace(":","") -> availableNewFacts.indexOfFirst { it.first == ctx.getString(R.string.melting_point_colon).replace(":","") }
            ctx.getString(R.string.melting_point_colon).replace(":","") -> availableNewFacts.indexOfFirst { it.first == ctx.getString(R.string.boiling_point_colon).replace(":","") || it.first == ctx.getString(R.string.density_colon).replace(":","") }
            ctx.getString(R.string.protons).replace(":","") -> availableNewFacts.indexOfFirst { it.first == ctx.getString(R.string.neutrons).replace(":","") || it.first == ctx.getString(R.string.element_electrons).replace(":","") }
            ctx.getString(R.string.atomic_mass_colon).replace(":","") -> availableNewFacts.indexOfFirst { it.first == ctx.getString(R.string.density_colon).replace(":","") || it.first == ctx.getString(R.string.abundance_earth_crust).replace(":","") }
            else -> -1
        }

        val results = mutableListOf<String>()
        val numToProvide = if (learningManager?.isTechnicalPreferred() == true) 2 else 1
        
        repeat(numToProvide) {
            if (availableNewFacts.isNotEmpty()) {
                val idx = if (preferredIdx != -1 && preferredIdx < availableNewFacts.size) preferredIdx else (0 until availableNewFacts.size).random()
                val (label, jsonKey) = availableNewFacts.removeAt(idx)
                sharedProperties.add(label)
                val value = element.optString(jsonKey, "")
                if (value.isNotEmpty() && value != "---") {
                    results.add(AIPersonality.formatElementResponse(ctx, activeLanguage, elementName, label, value))
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
            val elementKey = findElementKeyByQuery(query)
            
            if (elementKey != null) {
                // Delegate to the more detailed context handler using the KEY
                handleElementContextQuery(query, elementKey)
            } else {
                AIPersonality.getNoDataResponse(context!!, activeLanguage, query)
            }
        } catch (e: Exception) {
            AIPersonality.getNoDataResponse(context!!, activeLanguage, query)
        }
    }
    
    /**
     * Find element key by name or symbol in the data with fuzzy matching support.
     * Always returns the English database key.
     */
    private fun findElementKeyByQuery(query: String): String? {
        val lowerQuery = query.lowercase().trim()
        if (lowerQuery.isEmpty()) return null
        
        val data = elementData ?: return null

        // 1. Check if it's a numeric atomic number request (e.g. "element 79" or "what is 79?")
        val numberMatch = Regex("\\b(\\d+)\\b").find(lowerQuery)
        if (numberMatch != null && (lowerQuery.contains("element") || lowerQuery.contains("atomic number") || lowerQuery.split(" ").size <= 3)) {
            val numStr = numberMatch.groupValues[1]
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val element = data.optJSONObject(key) ?: continue
                if (element.optString("element_atomic_number") == numStr) return key
            }
        }

        // 2. Check cross-language map first for better translation support
        val queryWords = splitQueryTokens(lowerQuery)
            .map { it.removeSuffix("s") }
        
        // Filter out technical terms that might contain symbols like "S" or "H"
        val ignoreSymbols = listOf("euler", "poisson", "boltzmann", "planck", "avogadro", "celsius", "fahrenheit", "kelvin", "geology", "mineral", "rock")
        val isTechnicalTerm = ignoreSymbols.any { lowerQuery.contains(it) }

        val mappedElementKey = if (isTechnicalTerm && !lowerQuery.contains("element") && !lowerQuery.contains("symbol")) null 
                               else findCrossLanguageElementKey(lowerQuery, queryWords)
        if (mappedElementKey != null) return mappedElementKey
        
        // 3. Original fuzzy matching logic as fallback
        var bestKey: String? = null
        var minDistance = Int.MAX_VALUE
        
        try {
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val element = data.optJSONObject(key) ?: continue
                val name = element.optString("element", "").lowercase()
                val symbol = element.optString("short", "").lowercase()
                
                // 1. Check if name or symbol is explicitly mentioned as a word in the query
                if (queryWords.contains(name) || queryWords.contains(symbol)) {
                    // Check for symbol collision with common words
                    if (isCommonWordCollision(symbol, lowerQuery, queryWords)) continue
                    return key
                }
                
                // Special case for common nicknames or variations
                if (lowerQuery.contains(name) || (symbol.length > 1 && lowerQuery.contains(symbol))) {
                    // But check if it's a whole word match to avoid "in" matching "Indium"
                    if (lowerQuery.contains("\\b$name\\b".toRegex()) || lowerQuery.contains("\\b$symbol\\b".toRegex())) {
                        return key
                    }
                }

                // 2. Exact match of the whole query
                if (name == lowerQuery || symbol == lowerQuery) return key
                
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
                        bestKey = key
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        return bestKey
    }

    private fun findElementByQuery(query: String): JSONObject? {
        val key = findElementKeyByQuery(query) ?: return null
        return elementData?.optJSONObject(key)
    }

    /**
     * Check if message is a greeting
     */
    private fun isGeneralGreeting(message: String): Boolean {
        val greetings = listOf(
            "hi", "hello", "hey", "start", "begin", "help", "what can you do", 
            "hej", "hallå", "starta", "hjälp", "vad kan du göra",
            "hallo", "hi", "bonjour", "salut", "hola", "hola",
            "ciao", "ehi", "oi", "ola", "olá",
            "नमस्ते", "हैलो", "ही", "ہیلو", "سلام",
            "你好", "嗨", "nihao", "heej", "hallo", "kumusta"
        )
        return hasKeyword(message, greetings)
    }
    
    /**
     * Check if message is asking for a fact
     */
    private fun isFactRequest(message: String): Boolean {
        val factKeywords = listOf(
            "fact", "fun fact", "did you know", "interesting", "cool", 
            "fakta", "kul fakta", "visste du", "intressant", "häftigt",
            "faktum", "interessant", "fait", "curiosité", "dato curioso", "curiosidad",
            "curiosità", "interessante", "fatti", "curiosidade", "curiosidades", "fatos",
            "तथ्य", "क्या आप जानते हैं", "दिलचस्प",
            "事实", "你知道吗", "有趣", "shishi", "haqiqat", "feit", "katotohanan"
        )
        return hasKeyword(message, factKeywords)
    }

    /**
     * Helper to check if query contains any of the keywords, with fuzzy matching for spelling mistakes.
     * Improved to avoid false positives with short keywords (like French 'bout' matching 'about').
     */
    private fun hasKeyword(query: String, keywords: List<String>): Boolean {
        val lowerQuery = query.lowercase()
        
        // Prioritize exact keyword matches with word boundaries
        for (keyword in keywords) {
            val regex = "\\b${Regex.escape(keyword)}\\b".toRegex()
            if (regex.containsMatchIn(lowerQuery)) return true
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
