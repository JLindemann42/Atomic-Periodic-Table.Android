package com.jlindemann.science.activities

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.adapter.ChatMessageAdapter
import com.jlindemann.science.ai.AIAgentManager
import com.jlindemann.science.ai.AIPersonality
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Activity for AI chat interface with element information
 */
class AIChatActivity : AppCompatActivity() {
    
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var aiAgentManager: AIAgentManager
    
    private var chatMessages = mutableListOf<ChatMessage>()
    private var adapter: ChatMessageAdapter? = null
    private var contextElement: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply theme
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()
        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> { setTheme(R.style.AppTheme) }
                Configuration.UI_MODE_NIGHT_YES -> { setTheme(R.style.AppThemeDark) }
            }
        }
        if (themePrefValue == 0) { setTheme(R.style.AppTheme) }
        if (themePrefValue == 1) { setTheme(R.style.AppThemeDark) }
        
        setContentView(R.layout.activity_ai_chat)
        
        // Get context element if provided
        contextElement = intent.getStringExtra("element")
        
        // Initialize UI
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        backButton = findViewById(R.id.backButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        
        setupRecyclerView()
        setupListeners()
        
        // Initialize AI agent
        aiAgentManager = AIAgentManager(this)
        scope.launch {
            aiAgentManager.initialize()
            addGreetingMessage()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(chatMessages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatRecyclerView.adapter = adapter
    }
    
    private fun setupListeners() {
        sendButton.setOnClickListener { sendMessage() }
        backButton.setOnClickListener { finish() }
        
        messageInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && 
                event.action == android.view.KeyEvent.ACTION_UP) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }
    
    private fun addGreetingMessage() {
        val greeting = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = AIPersonality.getGreeting() + (contextElement?.let { " I see you're looking at $it. Want to know more?" } ?: ""),
            isFromUser = false,
            timestamp = System.currentTimeMillis()
        )
        chatMessages.add(greeting)
        adapter?.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }
    
    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isBlank()) return
        
        // Add user message
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = messageText,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )
        chatMessages.add(userMessage)
        adapter?.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
        
        messageInput.text.clear()
        loadingIndicator.visibility = View.VISIBLE
        
        // Generate AI response
        scope.launch {
            val aiMessage = aiAgentManager.generateResponse(messageText, contextElement)
            chatMessages.add(aiMessage)
            adapter?.notifyItemInserted(chatMessages.size - 1)
            chatRecyclerView.scrollToPosition(chatMessages.size - 1)
            loadingIndicator.visibility = View.GONE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
