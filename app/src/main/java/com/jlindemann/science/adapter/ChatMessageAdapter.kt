package com.jlindemann.science.adapter

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.jlindemann.science.R
import com.jlindemann.science.ai.cards.ChatCardCodec
import com.jlindemann.science.ai.cards.ChatCardKind
import com.jlindemann.science.ai.compose.ChatAction
import com.jlindemann.science.ai.compose.ChatActionCodec
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.utils.AnalyticsEvent
import com.jlindemann.science.utils.AnalyticsHelper
import com.jlindemann.science.utils.AnalyticsParam
import com.jlindemann.science.views.ProCardGate

class ChatMessageAdapter(
    private val messages: List<ChatMessage>,
    /**
     * Invoked when the user taps a source chip. Defaulted so existing call sites, which do not
     * offer navigation, continue to compile and simply render no chips.
     */
    private val onAction: ((ChatAction) -> Unit)? = null,
    /**
     * Fills a visual card. Defaulted to null so a host that does not supply one renders text only,
     * which is what happens before the agent has finished loading.
     */
    private val cardBinder: ChatCardBinder? = null,
    /**
     * Whether the viewer has PRO+, which is what the visual cards require. PRO alone is not enough:
     * the answer's *text* follows the app's ordinary paywall, but the card that illustrates it is a
     * PRO+ feature in its own right, whatever tier the underlying field sits behind.
     *
     * Read on every bind rather than captured once, so a purchase takes effect the moment the list
     * rebinds instead of needing the chat reopened.
     */
    private val isProPlusUser: () -> Boolean = { true },
    /** Sends the user to the PRO page. Null renders the lock without a button. */
    private val onUpgrade: (() -> Unit)? = null
) : RecyclerView.Adapter<ChatMessageAdapter.ChatViewHolder>() {

    /**
     * Messages whose entrance animation has already played.
     *
     * Without this, scrolling back up replays the slide-in for every row that comes into view, which
     * reads as the conversation re-arriving.
     */
    private val animated = HashSet<String>()

    /**
     * Messages whose locked card has already been reported.
     *
     * `bindCard` runs on every rebind and every scroll pass, so without this the paywall impression
     * would be counted once per time the row crossed the viewport rather than once per answer.
     */
    private val lockedCardReported = HashSet<String>()

    inner class ChatViewHolder(
        itemView: View,
        private val cardKind: ChatCardKind?,
        private val cardView: View?
    ) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: CardView = itemView.findViewById(R.id.userMessageContainer)
        private val aiRoot: View = itemView.findViewById(R.id.aiMessageRoot)
        private val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        private val aiMessageText: TextView = itemView.findViewById(R.id.aiMessageText)
        private val aiMascotIcon: android.widget.ImageView = itemView.findViewById(R.id.aiMascotIcon)
        private val aiGlow: View = itemView.findViewById(R.id.aiMessageGlow)
        private val aiActions: ChipGroup = itemView.findViewById(R.id.aiMessageActions)
        private val cardContainer: FrameLayout = itemView.findViewById(R.id.aiMessageCardContainer)
        private val cardHost: FrameLayout = itemView.findViewById(R.id.aiMessageCardHost)
        private val proOverlay: View = itemView.findViewById(R.id.aiCardProOverlay)
        private val proScrim: View = itemView.findViewById(R.id.aiCardProScrim)

        // Held so they can be cancelled. Previously both ran forever on a view that was then
        // recycled onto a different message, so a long conversation accumulated animators that
        // nothing ever stopped.
        private var glowAnimator: ValueAnimator? = null
        private var mascotAnimator: ValueAnimator? = null

        fun bind(message: ChatMessage) {
            if (message.isFromUser) {
                userContainer.visibility = View.VISIBLE
                aiRoot.visibility = View.GONE
                aiMascotIcon.visibility = View.GONE
                userMessageText.text = formatMarkdown(message.text)
                return
            }

            userContainer.visibility = View.GONE
            aiRoot.visibility = View.VISIBLE
            aiMascotIcon.visibility = View.VISIBLE
            aiMessageText.text = formatMarkdown(message.text)
            bindActions(message)
            bindCard(message)

            if (animated.add(message.id)) animateEntrance(aiRoot, aiMascotIcon)
            startGlowAnimation(aiGlow)
            startMascotAnimation(aiMascotIcon)
        }

        /**
         * Fill the card this holder was created for.
         *
         * The view type already guarantees the inflated card matches the message's kind, so there is
         * no "hide the other seven" branch here — RecyclerView pools per view type.
         */
        private fun bindCard(message: ChatMessage) {
            val card = cardKind?.let { ChatCardCodec.decode(message.cards) }
            val view = cardView
            val bound = card != null && view != null && cardBinder?.bind(view, card) == true
            cardContainer.visibility = if (bound) View.VISIBLE else View.GONE
            if (!bound) {
                proOverlay.visibility = View.GONE
                ProCardGate.apply(view, locked = false)
                return
            }

            // The card is bound either way and only obscured, so unlocking is a one-frame change
            // with no re-bind. The answer's text is never gated — only the visual is.
            val locked = !isProPlusUser()
            ProCardGate.apply(view, locked)
            proOverlay.visibility = if (locked) View.VISIBLE else View.GONE
            if (locked) {
                if (lockedCardReported.add(message.id)) {
                    AnalyticsHelper.logEvent(
                        itemView.context,
                        AnalyticsEvent.AI_CARD_LOCKED,
                        AnalyticsParam.CARD_KIND to card.kind.name
                    )
                }
                proScrim.visibility = if (ProCardGate.needsScrim()) View.VISIBLE else View.GONE
                proScrim.alpha = ProCardGate.SCRIM_ALPHA
                // Swallow taps on the card itself so a locked visual cannot be interacted with.
                proOverlay.setOnClickListener { onUpgrade?.invoke() }
                itemView.findViewById<View>(R.id.aiCardProButton)?.apply {
                    visibility = if (onUpgrade == null) View.GONE else View.VISIBLE
                    setOnClickListener { onUpgrade?.invoke() }
                }
            }
        }

        /** Render the answer's sources as tappable chips, or hide the group when there are none. */
        private fun bindActions(message: ChatMessage) {
            aiActions.removeAllViews()
            val actions = if (onAction == null) emptyList() else ChatActionCodec.decode(message.actions)
            if (actions.isEmpty()) {
                aiActions.visibility = View.GONE
                return
            }
            aiActions.visibility = View.VISIBLE
            val context = aiActions.context
            for (action in actions) {
                val chip = Chip(context).apply {
                    text = action.label
                    isCheckable = false
                    isClickable = true
                    textSize = 12f
                    chipBackgroundColor = null
                    setChipBackgroundColorResource(android.R.color.transparent)
                    chipStrokeWidth = 1f
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener { onAction?.invoke(action) }
                }
                aiActions.addView(chip)
            }
        }

        /** Stop everything this row owns before it is reused for a different message. */
        fun release() {
            glowAnimator?.cancel(); glowAnimator = null
            mascotAnimator?.cancel(); mascotAnimator = null
            aiGlow.animate().cancel()
            aiMascotIcon.animate().cancel()
            aiRoot.animate().cancel()
            aiRoot.alpha = 1f
            aiRoot.translationX = 0f
            aiMascotIcon.apply { alpha = 1f; scaleX = 1f; scaleY = 1f; rotation = 0f }
            cardBinder?.unbind(cardView)
            // Clear the blur before the row is reused; a pooled view would otherwise carry it onto
            // an unlocked card.
            ProCardGate.apply(cardView, locked = false)
        }

        private fun formatMarkdown(text: String): CharSequence {
            val builder = SpannableStringBuilder()
            val lines = text.split("\n")

            for (i in lines.indices) {
                val line = lines[i]
                if (line.trimStart().startsWith("###")) {
                    val headerText = line.trimStart().removePrefix("###").trim()
                    val start = builder.length
                    appendBoldText(builder, headerText)
                    val end = builder.length

                    builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(android.text.style.RelativeSizeSpan(1.2f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    appendBoldText(builder, line)
                }

                if (i < lines.size - 1) {
                    builder.append("\n")
                }
            }
            return builder
        }

        private fun appendBoldText(builder: SpannableStringBuilder, text: String) {
            var lastIdx = 0
            val regex = Regex("\\*\\*(.*?)\\*\\*")

            regex.findAll(text).forEach { match ->
                builder.append(text.substring(lastIdx, match.range.first))

                val start = builder.length
                val content = match.groupValues[1]
                builder.append(content)

                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                lastIdx = match.range.last + 1
            }

            if (lastIdx < text.length) {
                builder.append(text.substring(lastIdx))
            }
        }

        private fun animateEntrance(root: View, mascot: View) {
            root.alpha = 0f
            root.translationX = -50f
            root.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            mascot.alpha = 0f
            mascot.scaleX = 0f
            mascot.scaleY = 0f
            mascot.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }

        private fun startGlowAnimation(view: View) {
            glowAnimator?.cancel()
            glowAnimator = ValueAnimator.ofFloat(0.1f, 1.0f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { anim ->
                    val value = anim.animatedValue as Float
                    view.alpha = value
                    val scale = 1.0f + (value * 0.2f)
                    view.scaleX = scale
                    view.scaleY = scale
                }
                start()
            }
        }

        /**
         * The mascot's idle sway.
         *
         * A single reversing animator rather than the previous self-re-entrant `withEndAction`
         * chain, which had no handle to cancel and so kept rescheduling itself for the lifetime of
         * the process.
         */
        private fun startMascotAnimation(view: View) {
            mascotAnimator?.cancel()
            mascotAnimator = ValueAnimator.ofFloat(-15f, 15f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { anim ->
                    val value = anim.animatedValue as Float
                    view.rotation = value
                    val scale = 1.0f + (kotlin.math.abs(value) / 15f) * 0.1f
                    view.scaleX = scale
                    view.scaleY = scale
                }
                start()
            }
        }
    }

    /**
     * User rows, plain AI rows and one type per card kind.
     *
     * Encoding the kind in the view type is what lets the card be inflated once, when the holder is
     * created, instead of on every bind while scrolling. RecyclerView pools per type, so a holder
     * always receives a message whose card matches what it inflated.
     */
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        if (message.isFromUser) return TYPE_USER
        val kind = ChatCardCodec.kindOf(message.cards) ?: return TYPE_AI_PLAIN
        return TYPE_AI_CARD_BASE + kind.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_chat_message, parent, false)
        val kind = kindForViewType(viewType)
        val cardView = kind?.let { layoutFor(it) }?.let { layout ->
            inflater.inflate(layout, view.findViewById<FrameLayout>(R.id.aiMessageCardHost), true)
        }
        return ChatViewHolder(view, kind, cardView)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun onViewRecycled(holder: ChatViewHolder) {
        super.onViewRecycled(holder)
        holder.release()
    }

    override fun getItemCount() = messages.size

    private fun kindForViewType(viewType: Int): ChatCardKind? {
        if (viewType < TYPE_AI_CARD_BASE) return null
        return ChatCardKind.values().getOrNull(viewType - TYPE_AI_CARD_BASE)
    }

    private fun layoutFor(kind: ChatCardKind): Int = when (kind) {
        ChatCardKind.ELECTRON_SHELL -> R.layout.card_ai_electron_shell
        ChatCardKind.CRYSTAL_STRUCTURE -> R.layout.card_ai_crystal_structure
        ChatCardKind.EMISSION_SPECTRUM -> R.layout.card_ai_emission_spectrum
        ChatCardKind.POISSON_BAND -> R.layout.card_ai_poisson_band
        ChatCardKind.NFPA_DIAMOND -> R.layout.card_ai_nfpa_diamond
        ChatCardKind.IONIZATION_SERIES -> R.layout.card_ai_ionization_series
        ChatCardKind.ISOTOPE_DECAY -> R.layout.card_ai_isotope_decay
        ChatCardKind.ABUNDANCE -> R.layout.card_ai_abundance
    }

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_AI_PLAIN = 1
        const val TYPE_AI_CARD_BASE = 2
    }
}
