package com.jlindemann.science.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jlindemann.science.R
import com.jlindemann.science.ai.cards.ChatCardCodec
import com.jlindemann.science.ai.cards.ChatCardPolicy
import com.jlindemann.science.ai.chat.ChatOrchestrator
import com.jlindemann.science.ai.chat.ChatReply
import com.jlindemann.science.ai.chat.ReplySource
import com.jlindemann.science.ai.compose.ChatAction
import com.jlindemann.science.ai.compose.ChatActionCodec
import com.jlindemann.science.ai.data.DeepLinkTarget
import com.jlindemann.science.ai.core.AndroidStrings
import com.jlindemann.science.ai.core.Entitlements
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.retrieval.RetrievalService
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.utils.ElementDataLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * The Android half of the assistant: everything [ChatOrchestrator] deliberately does not know.
 *
 * The orchestrator answers; this decides whether it may (the daily allowance), what it may say
 * (entitlements, the offline setting), in which resources (a locale-wrapped Context per language),
 * and what gets reported afterwards ([AiTurnStats]). It also keeps the interest counts the
 * returning-user greeting reads, in the preferences `AILearningManager` used, so nobody's greeting
 * is lost in the move.
 *
 * Replaces `AIAgentManager`, keeping the members the chat panel calls.
 */
class AiChatAgent(private val context: Context) {

    private val rateLimiter = AIRateLimiter(context)
    private val gson = Gson()

    private var retrieval: RetrievalService? = null
    private var orchestrator: ChatOrchestrator? = null

    /** Locale-wrapped contexts, one per language the conversation has used. */
    private val localizedContexts = HashMap<String, Context>()

    /** The chips under the latest reply; before any reply, the opening ones. */
    private var lastSuggestions: List<String> = emptyList()

    /** The language before [initialize] has run, and the one a reset returns to. */
    private var appLanguage: String = DEFAULT_LANGUAGE

    /** What the last [generateResponse] did, for the caller to report. */
    @Volatile
    var lastTurnStats: AiTurnStats? = null
        private set

    /** How long [initialize] took, in milliseconds. */
    @Volatile
    var initDurationMs: Long = 0
        private set

    /**
     * The exception class that stopped [initialize], or null if it succeeded. The failure itself is
     * swallowed so the panel degrades rather than crashes, but it is reportable.
     */
    @Volatile
    var initError: String? = null
        private set

    /** Build the index and the conversation. Runs on IO while the panel shows its spinner. */
    suspend fun initialize() {
        val startedAt = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            try {
                appLanguage = baseLanguage(ElementDataLoader.getAppLanguage(context))
                val service = RetrievalService.get(context.assets)
                    ?: throw IllegalStateException("element data unavailable")
                retrieval = service
                orchestrator = ChatOrchestrator(
                    retrieval = service,
                    stringsFor = ::stringsFor,
                    entitlements = ::currentEntitlements,
                    cardPolicy = ::currentCardPolicy,
                    language = appLanguage,
                    // A right answer in a chat quiz run earns XP like the Learning Games (roadmap 2.5).
                    onXp = { com.jlindemann.science.util.XpManager.addGameXp(context, it) },
                    // The study coach's deck, shared with the Learning Games (roadmap 2.6).
                    study = com.jlindemann.science.study.StudyStore.deck(context).let { deck ->
                        com.jlindemann.science.study.StudyAccess(deck, save = {
                            com.jlindemann.science.study.StudyStore.save(context, deck)
                        })
                    }
                ).also { it.profile.load(loadInterests()) }
                lastSuggestions = orchestrator?.suggestions().orEmpty()
                initError = null
            } catch (e: Exception) {
                initError = e.javaClass.simpleName
                Log.w(TAG, "AI agent failed to initialize", e)
            }
        }
        initDurationMs = System.currentTimeMillis() - startedAt
    }

    /** Answer one message, or say why it cannot be answered. */
    suspend fun generateResponse(userMessage: String, contextElement: String? = null): ChatMessage =
        withContext(Dispatchers.Default) {
            lastTurnStats = null
            val chat = orchestrator ?: return@withContext reply(
                stringsFor(appLanguage).get(R.string.ai_context_lost), userMessage, AiTurnStats.Path.CONTEXT_LOST
            )
            val strings = stringsFor(chat.language)

            if (!rateLimiter.canSendMessage()) {
                // "You have used all 16 messages today" is a dead end on its own; the one thing the
                // user needs next is when they can carry on, and that is rarely 24 hours away.
                val reset = getLimitResetTime()?.let { "\n" + strings.get(R.string.ai_rate_limit_resets, it) }.orEmpty()
                return@withContext reply(
                    strings.get(R.string.ai_rate_limit_reached, rateLimiter.getDailyLimit()) + reset,
                    userMessage, AiTurnStats.Path.RATE_LIMITED, dailyLimit = rateLimiter.getDailyLimit()
                )
            }
            rateLimiter.incrementMessageCount()

            // Opened from an element's page: the page is the subject until the user names another.
            if (chat.state.focusElement == null) chat.focusOn(contextElement)

            val before = chat.language
            val answer = try {
                chat.ask(userMessage)
            } catch (e: Exception) {
                Log.w(TAG, "assistant failed on a turn", e)
                return@withContext reply(
                    stringsFor(chat.language).get(R.string.ai_no_data), userMessage, AiTurnStats.Path.FALLBACK,
                    engineError = e.javaClass.simpleName
                )
            }
            lastSuggestions = answer.suggestions
            saveInterests(chat.profile.elements)

            message(answer, userMessage, chat.language)
                .also { recordTurn(answer, userMessage, it, languageSwitched = chat.language != before) }
        }

    // ---- What the panel shows ---------------------------------------------------------------

    fun getActiveLanguage(): String = orchestrator?.language ?: appLanguage

    fun setCurrentElement(element: String?) {
        orchestrator?.focusOn(element)
        orchestrator?.let { lastSuggestions = it.suggestions() }
    }

    fun getSuggestedQuestions(): List<String> = lastSuggestions

    fun isHistoryEnabled(): Boolean = rateLimiter.isPro() || rateLimiter.isProPlus()

    /** True for everyone: a PRO+ user reads "Unlimited" rather than the badge disappearing. */
    fun shouldShowMessageLimit(): Boolean = true

    fun getMessageLimitDisplay(): String {
        val total = rateLimiter.getDailyLimit()
        if (total == Int.MAX_VALUE) {
            return runCatching { localized(getActiveLanguage()).getString(R.string.ai_limit_unlimited) }.getOrDefault("∞")
        }
        return "${rateLimiter.getRemainingMessages().coerceAtLeast(0)}/$total"
    }

    /**
     * The clock time the daily allowance comes back, in the user's own 12/24-hour format and the
     * conversation's language.
     */
    fun getLimitResetTime(): String? = runCatching {
        android.text.format.DateFormat.getTimeFormat(localized(getActiveLanguage()))
            .format(java.util.Date(rateLimiter.resetTimeMillis()))
    }.getOrNull()

    /** The reset badge's text, or null for PRO+, which has no limit to reset. */
    fun getMessageLimitResetDisplay(): String? {
        if (rateLimiter.isProPlus()) return null
        val time = getLimitResetTime() ?: return null
        return localized(getActiveLanguage()).getString(R.string.ai_limit_reset_badge, time)
    }

    /** Follow a change of the app's language setting. */
    suspend fun refreshLanguage() {
        val language = baseLanguage(ElementDataLoader.getAppLanguage(context))
        if (language != appLanguage) {
            appLanguage = language
            setLanguage(language)
        }
    }

    /** Continue in [language], as a restored thread was written in. */
    suspend fun setLanguage(language: String) {
        val chat = orchestrator ?: return
        chat.state.activeLanguage = baseLanguage(language)
        lastSuggestions = chat.suggestions()
    }

    /** Start a new thread in the app's language. */
    suspend fun resetSession() {
        val chat = orchestrator ?: return
        chat.reset()
        chat.state.activeLanguage = baseLanguage(ElementDataLoader.getAppLanguage(context))
        lastSuggestions = chat.suggestions()
    }

    // ---- What the card binder needs ---------------------------------------------------------

    /** An element as the typed index holds it, for a card binder that needs its numbers. */
    fun elementRecord(key: String): ElementRecord? = runCatching { retrieval?.store?.element(key) }.getOrNull()

    /** The typed index itself, for a card that ranks an element against all the others. */
    fun knowledgeStore(): KnowledgeStore? = retrieval?.store

    /** Strings in the language the conversation is currently in, for labelling a card. */
    fun cardStrings(): StringProvider? = orchestrator?.let { stringsFor(it.language) }

    // ---- Internals --------------------------------------------------------------------------

    private fun message(answer: ChatReply, userMessage: String, language: String) = ChatMessage(
        id = UUID.randomUUID().toString(),
        text = answer.text,
        isFromUser = false,
        timestamp = System.currentTimeMillis(),
        actions = ChatActionCodec.encode(answer.actions + listOfNotNull(reportAction(answer, userMessage, language))),
        cards = ChatCardCodec.encode(answer.card)
    )

    /**
     * On a decline, a chip that offers to send the question to the developer (roadmap 0.7). Tapping
     * it only opens a dialog saying what would be sent; nothing leaves the device unless the user
     * confirms there.
     */
    private fun reportAction(answer: ChatReply, userMessage: String, language: String): ChatAction? {
        if (answer.source != ReplySource.DECLINED || userMessage.isBlank()) return null
        return ChatAction(
            label = stringsFor(language).get(R.string.ai_send_question),
            target = DeepLinkTarget.REPORT_QUESTION,
            args = mapOf(
                "q" to UnansweredQuestions.clean(userMessage),
                "lang" to language,
                "reason" to (answer.declineReason?.name?.lowercase() ?: "unknown")
            )
        )
    }

    /** A reply that did not come from the orchestrator — a limit, a lost context, a failure. */
    private fun reply(
        text: String,
        userMessage: String,
        path: String,
        dailyLimit: Int? = null,
        engineError: String? = null
    ): ChatMessage = ChatMessage(UUID.randomUUID().toString(), text, false, System.currentTimeMillis()).also {
        lastTurnStats = stats(path, userMessage, it, dailyLimit = dailyLimit, engineError = engineError)
    }

    private fun recordTurn(answer: ChatReply, userMessage: String, message: ChatMessage, languageSwitched: Boolean) {
        val path = when (answer.source) {
            ReplySource.ENGINE -> AiTurnStats.Path.ENGINE
            ReplySource.PERSONALITY -> AiTurnStats.Path.PERSONALITY
            ReplySource.REACTIVITY -> AiTurnStats.Path.REACTIVITY
            ReplySource.DECLINED -> AiTurnStats.Path.FALLBACK
        }
        lastTurnStats = stats(
            path, userMessage, message,
            intent = answer.plan?.intent?.name,
            confidence = answer.plan?.confidence,
            languageSwitched = languageSwitched,
            elementResolved = orchestrator?.state?.focusElement != null,
            declineReason = answer.declineReason?.name?.lowercase()
        )
    }

    /**
     * Reads the shape of the query rather than the query: a question is free text and could hold
     * anything, so only its size and whether it resolved to an element leave this class.
     */
    private fun stats(
        path: String,
        userMessage: String,
        message: ChatMessage,
        intent: String? = null,
        confidence: Double? = null,
        languageSwitched: Boolean = false,
        elementResolved: Boolean = false,
        dailyLimit: Int? = null,
        engineError: String? = null,
        declineReason: String? = null
    ): AiTurnStats {
        val trimmed = userMessage.trim()
        return AiTurnStats(
            path = path,
            intent = intent,
            confidence = confidence,
            language = getActiveLanguage(),
            languageSwitched = languageSwitched,
            elementResolved = elementResolved,
            hasCard = message.cards != null,
            cardKind = ChatCardCodec.kindOf(message.cards)?.name,
            actionCount = ChatActionCodec.decode(message.actions).size,
            queryChars = trimmed.length,
            queryWords = if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size,
            engineError = engineError,
            dailyLimit = dailyLimit,
            declineReason = declineReason
        )
    }

    private fun stringsFor(language: String): StringProvider = AndroidStrings(localized(language), language)

    /**
     * A Context whose resources resolve in [language]. Built with a configuration rather than
     * `Locale.setDefault`, so the auto-detected chat language never becomes the process default.
     */
    private fun localized(language: String): Context = synchronized(localizedContexts) {
        localizedContexts.getOrPut(language) {
            val config = android.content.res.Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(language))
            context.createConfigurationContext(config)
        }
    }

    /** The agent honours the same paywall as the rest of the app. */
    private fun currentEntitlements() = Entitlements(isPro = rateLimiter.isPro(), isProPlus = rateLimiter.isProPlus())

    /** Offline mode withholds the cards that need the network, as the element screen does. */
    private fun currentCardPolicy(): ChatCardPolicy {
        val offline = runCatching { com.jlindemann.science.preferences.offlinePreference(context).getValue() }
            .getOrDefault(0)
        return ChatCardPolicy(allowNetworkCards = offline != 1)
    }

    /**
     * "sv", not "sv-rSE" or "sv_SE": the alias table and the element files are keyed by base
     * language.
     */
    private fun baseLanguage(language: String?): String =
        language.orEmpty().trim().substringBefore("-r").substringBefore('-').substringBefore('_')
            .lowercase().ifBlank { DEFAULT_LANGUAGE }

    private fun loadInterests(): Map<String, Int>? = runCatching {
        val json = context.getSharedPreferences(INTEREST_PREFS, Context.MODE_PRIVATE).getString(INTEREST_KEY, null)
        gson.fromJson<Map<String, Int>>(json ?: return null, object : TypeToken<Map<String, Int>>() {}.type)
    }.getOrNull()

    private fun saveInterests(counts: Map<String, Int>) {
        context.getSharedPreferences(INTEREST_PREFS, Context.MODE_PRIVATE)
            .edit().putString(INTEREST_KEY, gson.toJson(counts)).apply()
    }

    private companion object {
        const val TAG = "AiChatAgent"
        const val DEFAULT_LANGUAGE = "en"

        /** Where `AILearningManager` kept the counts, so existing users keep their greeting. */
        const val INTEREST_PREFS = "ai_learning_prefs"
        const val INTEREST_KEY = "element_interests"
    }
}
