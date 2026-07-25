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
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
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
        // initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (t: Throwable) {
            // log or ignore: safe fallback if initialization already happened or missing config
            t.printStackTrace()
        }

        // Initialize Firebase Analytics
        AnalyticsHelper.initialize(this)

        // Optional: enable Firestore offline persistence
        try {
            val fs = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            fs.firestoreSettings = settings
        } catch (t: Throwable) {
            // ignore if Firestore not available
            t.printStackTrace()
        }
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