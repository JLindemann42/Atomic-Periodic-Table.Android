package com.jlindemann.science.adapter

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.jlindemann.science.R
import com.jlindemann.science.ai.compose.ChatAction
import com.jlindemann.science.ai.compose.ChatActionCodec
import com.jlindemann.science.model.ChatMessage

class ChatMessageAdapter(
    private val messages: List<ChatMessage>,
    /**
     * Invoked when the user taps a source chip. Defaulted so existing call sites, which do not
     * offer navigation, continue to compile and simply render no chips.
     */
    private val onAction: ((ChatAction) -> Unit)? = null
) : RecyclerView.Adapter<ChatMessageAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: CardView = itemView.findViewById(R.id.userMessageContainer)
        private val aiRoot: View = itemView.findViewById(R.id.aiMessageRoot)
        private val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        private val aiMessageText: TextView = itemView.findViewById(R.id.aiMessageText)
        private val aiMascotIcon: android.widget.ImageView = itemView.findViewById(R.id.aiMascotIcon)
        private val aiGlow: View = itemView.findViewById(R.id.aiMessageGlow)
        private val aiActions: ChipGroup = itemView.findViewById(R.id.aiMessageActions)

        fun bind(message: ChatMessage) {
            if (message.isFromUser) {
                userContainer.visibility = View.VISIBLE
                aiRoot.visibility = View.GONE
                aiMascotIcon.visibility = View.GONE
                userMessageText.text = formatMarkdown(message.text)
            } else {
                userContainer.visibility = View.GONE
                aiRoot.visibility = View.VISIBLE
                aiMascotIcon.visibility = View.VISIBLE
                aiMessageText.text = formatMarkdown(message.text)
                bindActions(message)

                // Entrance animation
                animateEntrance(aiRoot, aiMascotIcon)

                // Start pulsating glow effect
                startGlowAnimation(aiGlow)
                // Start mascot pulse
                startMascotAnimation(aiMascotIcon)
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
                    
                    // Apply header styles (Large + Bold)
                    builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(android.text.style.RelativeSizeSpan(1.2f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    // Process bold text in normal lines
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
                // Append text before the match
                builder.append(text.substring(lastIdx, match.range.first))
                
                // Start of bold part
                val start = builder.length
                val content = match.groupValues[1]
                builder.append(content)
                
                // Apply bold style
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                lastIdx = match.range.last + 1
            }
            
            // Append remaining text
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
            val animator = ValueAnimator.ofFloat(0.1f, 1.0f)
            animator.duration = 1000
            animator.repeatCount = ValueAnimator.INFINITE
            animator.repeatMode = ValueAnimator.REVERSE
            animator.addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                view.alpha = value
                val scale = 1.0f + (value * 0.2f)
                view.scaleX = scale
                view.scaleY = scale
            }
            animator.start()
        }

        private fun startMascotAnimation(view: View) {
            view.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .rotation(15f)
                .setDuration(1000)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .rotation(-15f)
                        .setDuration(1000)
                        .withEndAction {
                            startMascotAnimation(view)
                        }
                        .start()
                }
                .start()
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount() = messages.size
}
