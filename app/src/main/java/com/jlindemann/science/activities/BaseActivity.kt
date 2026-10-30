package com.jlindemann.science.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.utils.AiChatPanelController
import com.jlindemann.science.R
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.util.LivesManager
import com.jlindemann.science.utils.Pasteur
import com.jlindemann.science.utils.LocaleUtil
import com.jlindemann.science.utils.AnalyticsHelper
import androidx.core.view.WindowInsetsCompat
import java.util.*

abstract class BaseActivity : AppCompatActivity(), View.OnApplyWindowInsetsListener {
    companion object {
        private const val TAG = "BaseActivity"
    }

    private var systemUiConfigured = false

    /**
     * The screen this is, from [com.jlindemann.science.ai.chat.Screens], for a chat opened here
     * (roadmap 2.9). A screen that names one gets the chat button and panel without a layout
     * change; null, the default, adds nothing. The main screen and the element pages host their own.
     */
    protected open val chatScreen: String? = null

    /** The element this screen is showing, if any, so the chat can focus it. */
    protected open fun chatElement(): String? = null

    private var chatPanel: AiChatPanelController? = null

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        attachChat()
    }

    /**
     * Add the chat button and the chat panel over this screen's own layout. Both go into the
     * content frame, the panel last so it covers the button when open.
     */
    private fun attachChat() {
        val screen = chatScreen ?: return
        if (chatPanel != null) return
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val density = resources.displayMetrics.density
        val margin = (16 * density).toInt()

        val fab = FloatingActionButton(this).apply {
            id = R.id.ai_chat_fab
            setImageResource(R.drawable.ic_ai_mascot)
            contentDescription = getString(R.string.ask_atomic)
            val primary = android.util.TypedValue()
            if (theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, primary, true)) {
                backgroundTintList = android.content.res.ColorStateList.valueOf(primary.data)
            }
        }
        content.addView(fab, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.END
        ).apply { setMargins(margin, margin, margin, margin) })
        // Clear of the navigation bar on edge-to-edge screens.
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = margin + bars.bottom
                marginEnd = margin + bars.right
            }
            insets
        }

        val panelRoot = LayoutInflater.from(this).inflate(R.layout.ai_chat_panel, content, false)
        panelRoot.id = R.id.ai_panel_include
        content.addView(panelRoot, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Added after the screen's own callbacks, so while it is enabled it is the one that runs.
        val back = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                chatPanel?.close()
            }
        }
        onBackPressedDispatcher.addCallback(this, back)
        chatPanel = AiChatPanelController(
            activity = this,
            panelRoot = panelRoot,
            scope = lifecycleScope,
            contextElement = { chatElement() },
            screen = screen,
            onPanelOpened = { back.isEnabled = true; fab.hide() },
            onPanelHidden = { back.isEnabled = false; fab.show() }
        ).also { it.setup() }
        fab.setOnClickListener { chatPanel?.open() }
    }

    override fun onDestroy() {
        chatPanel?.onDestroy()
        super.onDestroy()
    }

    // --- Locale wrapping for runtime language switching ---
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", null)
        val country = prefs.getString("app_country", null)
        val locale = if (lang.isNullOrBlank()) null
        else if (country.isNullOrBlank()) Locale(lang)
        else Locale(lang, country)
        val context = LocaleUtil.wrap(newBase, locale)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase and Firestore are initialised once, in AtomicApplication. Firestore settings
        // can only be applied before the instance is first used, so doing it per activity threw on
        // every activity after the first.

        // Initialize Firebase Analytics
        AnalyticsHelper.initialize(this)
    }

    override fun onResume() {
        super.onResume()
        checkAchievements()
        
        // Log screen view for analytics
        val screenName = this.javaClass.simpleName.replace("Activity", "")
        AnalyticsHelper.logScreenView(this, screenName, this.javaClass.simpleName)
    }

    override fun onStart() {
        super.onStart()
        val content = findViewById<View>(android.R.id.content)
        content.setOnApplyWindowInsetsListener(this)

        if (!systemUiConfigured) {
            systemUiConfigured = true
        }
    }

    open fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) = Unit

    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val systemBars = insetsCompat.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insetsCompat.getInsets(WindowInsetsCompat.Type.ime())
        
        // Report system bars + IME height to subclasses
        onApplySystemInsets(
            systemBars.top,
            ime.bottom.coerceAtLeast(systemBars.bottom),
            systemBars.left,
            systemBars.right
        )
        // Return original insets to allow children to handle them (e.g. for smooth animations)
        return insets
    }

    private fun checkAchievements() {
        val achievements = ArrayList<Achievement>()
        AchievementModel.getList(this, achievements)

        for (achievement in achievements) {
            if (achievement.isMaxProgressReached() && !achievement.isToastShown(this)) {
                Toast.makeText(this, "Achievement reached: ${achievement.title}", Toast.LENGTH_SHORT).show()
                achievement.markToastShown(this)
            }
        }
    }

    open fun updateLivesCount() {
        val lives = LivesManager.getLives(this)
        val maxLives = LivesManager.getMaxLives(this)
        val isUnlimited = maxLives == Int.MAX_VALUE || lives == Int.MAX_VALUE
        val livesDisplay = if (isUnlimited) "∞" else lives.toString()

        // Try both possible IDs (XML may differ between screens)
        val livesCountView = findViewById<TextView?>(R.id.tv_lives_count)
        livesCountView?.text = livesDisplay

        val livesLabelView = findViewById<TextView?>(R.id.tv_lives)
        val label = if (isUnlimited) getString(R.string.lives_unlimited) else getString(R.string.lives_label, livesDisplay)
        livesLabelView?.text = label
    }


    fun getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    fun getColorStateListFromAttr(attr: Int): android.content.res.ColorStateList? {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColorStateList(this, typedValue.resourceId)
        } else {
            android.content.res.ColorStateList.valueOf(typedValue.data)
        }
    }

    //Navigates to the Pro page (ProFragment within MainActivity).
    fun goToProPage() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("show_pro", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}