package com.jlindemann.science.utils

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.ChipGroup
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.activities.UserActivity
import com.jlindemann.science.adapter.ChatHistoryAdapter
import com.jlindemann.science.adapter.ChatCardBinder
import com.jlindemann.science.adapter.ChatMessageAdapter
import com.jlindemann.science.ai.AIAgentManager
import com.jlindemann.science.ai.AIPersonality
import com.jlindemann.science.ai.ChatHistoryManager
import com.jlindemann.science.ai.compose.ChatAction
import com.jlindemann.science.ai.compose.DeepLinkNavigator
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.model.ChatSession
import com.jlindemann.science.preferences.ProPlusVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Drives the AI chat panel for whichever screen hosts it.
 *
 * The panel was previously implemented twice — once in `MainActivity`, once in
 * `ElementInfoActivity` — in about 860 near-identical lines that had already drifted apart in
 * null-handling and in which coroutine scope they used. This owns that behaviour once, so a
 * change to the chat lands in both places by construction.
 *
 * All seventeen views are resolved a single time in [setup]; the previous code re-walked the view
 * tree on every suggestion refresh, message send and panel open.
 *
 * @param scope the host's coroutine scope. The host still owns and cancels it.
 * @param contextElement supplies the element the screen is about, so an element page can ground
 *   answers in it. Returns null on screens with no element context.
 * @param onPanelHidden lets the host re-evaluate its back-press interception.
 * @param onPanelOpened lets the host refresh anything of its own when the panel appears.
 */
class AiChatPanelController(
    private val activity: AppCompatActivity,
    private val panelRoot: View,
    private val scope: CoroutineScope,
    private val contextElement: () -> String? = { null },
    private val onPanelHidden: () -> Unit = {},
    private val onPanelOpened: () -> Unit = {}
) {

    val agent: AIAgentManager = AIAgentManager(activity)

    /** The live message list. Exposed because hosts restore it when re-entering the screen. */
    val messages: MutableList<ChatMessage> = mutableListOf()

    private lateinit var container: View
    private lateinit var scrim: View
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var sendBtn: ImageButton
    private lateinit var loading: ProgressBar
    private lateinit var limitView: TextView
    private var limitResetView: TextView? = null
    private lateinit var suggestions: ChipGroup
    private lateinit var inputContainer: View
    private lateinit var historyContainer: View
    private lateinit var historyRecycler: RecyclerView
    private lateinit var historyEmptyView: View

    private var adapter: ChatMessageAdapter? = null
    private var gradientAnimator: ValueAnimator? = null
    private var sessionId: String? = null

    /** Completes once the agent has loaded, so a queued send cannot run against an empty index. */
    private var initJob: Job? = null
    private var isSetUp = false

    private companion object {
        /** The value ProPlusVersion stores for a PRO+ user; 1 means not subscribed. */
        const val PRO_PLUS_UNLOCKED = 100
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    /** Resolve views, wire listeners and start loading the agent. Safe to call once. */
    fun setup() {
        if (isSetUp) return
        container = panelRoot.findViewById(R.id.ai_panel_container) ?: return
        scrim = panelRoot.findViewById(R.id.ai_panel_scrim) ?: return
        recycler = panelRoot.findViewById(R.id.ai_chat_recycler) ?: return
        input = panelRoot.findViewById(R.id.ai_message_input) ?: return
        sendBtn = panelRoot.findViewById(R.id.ai_send_btn) ?: return
        loading = panelRoot.findViewById(R.id.ai_loading_indicator) ?: return
        limitView = panelRoot.findViewById(R.id.ai_message_limit_view) ?: return
        // Optional: a host laying the panel out differently should lose the reset chip, not the panel.
        limitResetView = panelRoot.findViewById(R.id.ai_message_limit_reset_view)
        suggestions = panelRoot.findViewById(R.id.ai_suggestions_group) ?: return
        inputContainer = panelRoot.findViewById(R.id.ai_input_container) ?: return
        historyContainer = panelRoot.findViewById(R.id.ai_history_container) ?: return
        historyRecycler = panelRoot.findViewById(R.id.ai_history_recycler) ?: return
        historyEmptyView = panelRoot.findViewById(R.id.history_empty_view) ?: return
        isSetUp = true

        // The binder reads a card's numbers back out of the agent's typed index at bind time, which
        // is why a card only has to carry an element key rather than its whole dataset. Every source
        // is a lambda for the same reason: this runs before `initialize()`, so anything read here and
        // then held would be whatever existed before the agent loaded — which for the strings meant
        // null, and for the abundance and hazard cards meant never binding.
        val cardBinder = ChatCardBinder(
            resolveElement = { key -> agent.elementRecord(key) },
            strings = { agent.cardStrings() },
            store = { agent.knowledgeStore() }
        )
        adapter = ChatMessageAdapter(
            messages,
            { action -> handleAction(action) },
            cardBinder,
            // The cards are a PRO+ feature, so PRO alone leaves them blurred. Re-read per bind, so a
            // purchase unlocks the cards already on screen at the next rebind rather than needing
            // the chat reopened.
            isProPlusUser = { ProPlusVersion(activity).getValue() == PRO_PLUS_UNLOCKED },
            onUpgrade = { goToPro() }
        )
        recycler.layoutManager = LinearLayoutManager(activity).apply { stackFromEnd = true }
        recycler.adapter = adapter

        loading.visibility = View.VISIBLE
        initJob = scope.launch {
            agent.initialize()
            contextElement()?.let { agent.setCurrentElement(it) }
            updateMessageLimit()
            loadInitialConversation()
        }

        // The language picker is intentionally hidden: language follows the app locale and is
        // auto-detected per message.
        panelRoot.findViewById<ImageButton>(R.id.ai_language_btn)?.apply {
            visibility = View.GONE
            isEnabled = false
        }

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) send(text)
        }
        panelRoot.findViewById<ImageButton>(R.id.ai_history_btn)?.setOnClickListener { openHistory() }
        panelRoot.findViewById<ImageButton>(R.id.close_history_btn)?.setOnClickListener {
            historyContainer.visibility = View.GONE
        }
        panelRoot.findViewById<ImageButton>(R.id.ai_user_btn)?.setOnClickListener {
            activity.startActivity(Intent(activity, UserActivity::class.java))
        }
        panelRoot.findViewById<ImageButton>(R.id.ai_new_chat)?.setOnClickListener { startNewChat() }
        scrim.setOnClickListener { close() }

        ViewCompat.setOnApplyWindowInsetsListener(panelRoot) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (inputContainer.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                maxOf(ime, nav) + activity.resources.getDimensionPixelSize(R.dimen.margin)
            insets
        }

        BottomSheetBehavior.from(container).addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        panelRoot.visibility = View.GONE
                        stopGradientPulsation()
                        onPanelHidden()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    scrim.alpha = slideOffset
                }
            }
        )

        startInputAnimation()
        setupKeyboardAnimation()
    }

    fun isOpen(): Boolean = panelRoot.visibility == View.VISIBLE

    fun open() {
        if (!isSetUp) return
        panelRoot.visibility = View.VISIBLE
        val behavior = BottomSheetBehavior.from(container)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        // A second pass after layout; the sheet mis-measures its height on first open otherwise.
        container.post { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        scrim.alpha = 1f
        updateMessageLimit()
        updateSuggestions()
        onPanelOpened()
        startGradientPulsation(1500, 2)
    }

    fun close() {
        if (!isSetUp) return
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(panelRoot.windowToken, 0)
        BottomSheetBehavior.from(container).state = BottomSheetBehavior.STATE_HIDDEN
    }

    /** Stop animations. Does not cancel [scope]; the host owns that. */
    fun onDestroy() {
        gradientAnimator?.cancel()
        gradientAnimator = null
    }

    suspend fun refreshLanguage() {
        agent.refreshLanguage()
        withContext(Dispatchers.Main) { updateSuggestions() }
    }

    // ---- Messaging --------------------------------------------------------------------------

    /**
     * Send a message as if the user typed it.
     *
     * Queued behind initialisation, which fixes a latent bug in the widget path: it used to send
     * immediately after opening the panel, before the agent had finished loading.
     */
    fun send(text: String) {
        if (!isSetUp || text.isBlank()) return
        scope.launch {
            initJob?.join()
            deliver(text)
        }
    }

    private fun deliver(text: String) {
        val userMsg = ChatMessage(UUID.randomUUID().toString(), text, true, System.currentTimeMillis())
        messages.add(userMsg)
        adapter?.notifyItemInserted(messages.size - 1)
        recycler.scrollToPosition(messages.size - 1)
        input.text.clear()
        loading.visibility = View.VISIBLE

        agent.addToConversationHistory(userMsg)
        saveSession()
        startGradientPulsation(1000, ValueAnimator.INFINITE)

        scope.launch {
            val response = agent.generateResponse(text, contextElement = contextElement())
            loading.visibility = View.GONE
            messages.add(response)
            adapter?.notifyItemInserted(messages.size - 1)
            recycler.scrollToPosition(messages.size - 1)
            agent.addToConversationHistory(response)
            updateSuggestions()
            saveSession()
            updateMessageLimit()
            stopGradientPulsation()
        }
    }

    fun startNewChat() {
        messages.clear()
        sessionId = null
        scope.launch {
            agent.resetSession()
            withContext(Dispatchers.Main) {
                adapter?.notifyDataSetChanged()
                addGreeting()
                updateMessageLimit()
                updateSuggestions()
            }
        }
    }

    /**
     * Send the user to the PRO page from a locked card.
     *
     * Closes the panel first: the PRO page opens in `MainActivity`, and leaving the chat sheet up
     * over it would put the user behind a scrim they did not ask for. Falls back to launching the
     * intent directly when the host is not a [BaseActivity], so this cannot crash a screen that
     * embeds the panel differently.
     */
    private fun goToPro() {
        close()
        (activity as? BaseActivity)?.goToProPage() ?: activity.startActivity(
            Intent(activity, com.jlindemann.science.activities.MainActivity::class.java).apply {
                putExtra("show_pro", true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    private fun handleAction(action: ChatAction) {
        DeepLinkNavigator.navigate(activity, action) { query -> send(query) }
    }

    // ---- Conversation loading and persistence ------------------------------------------------

    private suspend fun loadInitialConversation() {
        if (AuthManager.isSignedIn() && agent.isHistoryEnabled()) {
            ChatHistoryManager.loadLatestChatSession { latest ->
                scope.launch {
                    if (latest != null && messages.isEmpty()) {
                        agent.setLanguage(latest.language)
                        applySession(latest)
                        updateMessageLimit()
                    } else if (messages.isEmpty()) {
                        addGreeting()
                    }
                    loading.visibility = View.GONE
                    updateSuggestions()
                }
            }
        } else {
            if (messages.isEmpty()) addGreeting()
            loading.visibility = View.GONE
            updateSuggestions()
        }
    }

    private fun openHistory() {
        if (!AuthManager.isSignedIn()) {
            Toast.makeText(activity, R.string.ai_history_sign_in, Toast.LENGTH_SHORT).show()
            return
        }
        if (!agent.isHistoryEnabled()) {
            Toast.makeText(activity, R.string.ai_history_pro_only, Toast.LENGTH_SHORT).show()
            return
        }
        historyContainer.visibility = View.VISIBLE
        loading.visibility = View.VISIBLE
        ChatHistoryManager.loadChatHistory { sessions ->
            loading.visibility = View.GONE
            if (sessions.isEmpty()) {
                historyEmptyView.visibility = View.VISIBLE
                historyRecycler.visibility = View.GONE
            } else {
                historyEmptyView.visibility = View.GONE
                historyRecycler.visibility = View.VISIBLE
                historyRecycler.layoutManager = LinearLayoutManager(activity)
                historyRecycler.adapter = ChatHistoryAdapter(sessions) { session ->
                    scope.launch {
                        agent.setLanguage(session.language)
                        applySession(session)
                        updateMessageLimit()
                        historyContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun applySession(session: ChatSession) {
        messages.clear()
        messages.addAll(session.messages)
        sessionId = session.id
        adapter?.notifyDataSetChanged()
        if (messages.isNotEmpty()) recycler.scrollToPosition(messages.size - 1)
        agent.setConversationHistory(session.messages)
    }

    private fun saveSession() {
        if (!AuthManager.isSignedIn() || !agent.isHistoryEnabled() || messages.isEmpty()) return
        if (sessionId == null) sessionId = UUID.randomUUID().toString()
        val firstUserMessage = messages.firstOrNull { it.isFromUser }?.text ?: "New Chat"
        val title =
            if (firstUserMessage.length > 40) firstUserMessage.take(37) + "..." else firstUserMessage
        val session = ChatSession(
            id = sessionId!!,
            title = title,
            timestamp = System.currentTimeMillis(),
            messages = messages.toList(),
            language = agent.getActiveLanguage()
        )
        ChatHistoryManager.saveChatSession(session) { success, id ->
            if (success && sessionId == null) sessionId = id
        }
    }

    // ---- Chrome -------------------------------------------------------------------------------

    private fun addGreeting() {
        messages.add(
            ChatMessage(
                UUID.randomUUID().toString(),
                AIPersonality.getGreeting(activity, agent.getActiveLanguage()),
                false,
                System.currentTimeMillis()
            )
        )
        adapter?.notifyItemInserted(messages.size - 1)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (!isSetUp) return
        suggestions.removeAllViews()
        for (suggestion in agent.getSuggestedQuestions()) {
            suggestions.addView(suggestionChip(suggestion))
        }
    }

    private fun suggestionChip(suggestion: String): TextView = TextView(activity).apply {
        text = suggestion
        textSize = 13f
        setTextColor(Utils.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface))
        setTypeface(null, Typeface.BOLD)
        val h = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
        val v = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
        setPadding(h, v, h, v)
        background = ContextCompat.getDrawable(context, R.drawable.bg_suggestion_chip_outlined)
        isClickable = true
        isFocusable = true
        setOnClickListener { send(suggestion) }
    }

    private fun updateMessageLimit() {
        if (!isSetUp) return
        if (!agent.shouldShowMessageLimit()) {
            limitView.visibility = View.GONE
            limitResetView?.visibility = View.GONE
            return
        }
        limitView.visibility = View.VISIBLE
        limitView.text = agent.getMessageLimitDisplay()
        // Recomputed here rather than held: the panel outlives midnight, and this runs on every open
        // and after every send, which is often enough that a stale time is never on screen for long.
        val reset = agent.getMessageLimitResetDisplay()
        limitResetView?.text = reset
        limitResetView?.visibility = if (reset == null) View.GONE else View.VISIBLE
    }

    private fun startGradientPulsation(duration: Long, repeatCount: Int) {
        val gradientView = panelRoot.findViewById<View>(R.id.ai_panel_gradient_view) ?: return
        gradientAnimator?.cancel()
        gradientAnimator = ValueAnimator.ofFloat(0f, 0.35f).apply {
            this.duration = duration
            this.repeatCount = repeatCount
            this.repeatMode = ValueAnimator.REVERSE
            addUpdateListener { gradientView.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopGradientPulsation() {
        gradientAnimator?.let {
            it.cancel()
            panelRoot.findViewById<View>(R.id.ai_panel_gradient_view)
                ?.animate()?.alpha(0f)?.setDuration(500)?.start()
        }
        gradientAnimator = null
    }

    private fun startInputAnimation() {
        val inputBg = panelRoot.findViewById<View>(R.id.ai_input_gradient_bg) ?: return
        ValueAnimator.ofFloat(0.6f, 1.0f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { inputBg.alpha = it.animatedValue as Float }
            start()
        }
    }

    /** Follow the keyboard, but only by however much it exceeds the navigation bar. */
    private fun setupKeyboardAnimation() {
        val inputWrapper = panelRoot.findViewById<LinearLayout>(R.id.ai_input_wrapper) ?: return
        ViewCompat.setWindowInsetsAnimationCallback(
            panelRoot,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    val shift = -(ime - nav).coerceAtLeast(0).toFloat()
                    inputWrapper.translationY = shift
                    recycler.translationY = shift
                    return insets
                }
            }
        )
    }
}
