package com.jlindemann.science.activities.tools

import GameResultItem
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.util.LivesManager
import com.jlindemann.science.util.XpManager
import java.util.concurrent.TimeUnit
import android.animation.Animator
import android.animation.AnimatorListenerAdapter

class FlashCardActivity : BaseActivity() {

    private lateinit var toggles: List<ToggleButton>
    private lateinit var infoText: TextView
    private lateinit var learningGameButtons: List<View>
    private var resultDialog: ResultDialogFragment? = null
    private var lastLevel: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = com.jlindemann.science.preferences.ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> setTheme(R.style.AppTheme)
                Configuration.UI_MODE_NIGHT_YES -> setTheme(R.style.AppThemeDark)
            }
        }
        if (themePrefValue == 0) setTheme(R.style.AppTheme)
        if (themePrefValue == 1) setTheme(R.style.AppThemeDark)

        setContentView(R.layout.activity_flashcards)
        findViewById<FrameLayout>(R.id.view_flash).systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        // Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_fla_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.flashcard_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_fla).elevation =
            (resources.getDimension(R.dimen.zero_elevation))

        val scrollView = findViewById<NestedScrollView>(R.id.flashcard_scroll)
        var isTitleVisible = false

        scrollView?.viewTreeObserver?.addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
            override fun onScrollChanged() {
                val scrollY = scrollView.scrollY
                val threshold = 150f

                val titleColorBackground = findViewById<FrameLayout>(R.id.common_title_back_fla_color)
                val titleText = findViewById<TextView>(R.id.flashcard_title)
                val titleDownstateText = findViewById<TextView>(R.id.flashcard_title_downstate)
                val titleBackground = findViewById<FrameLayout>(R.id.common_title_back_fla)

                if (scrollY > threshold) {
                    if (!isTitleVisible) {
                        // Animate titleText and titleColorBackground to visible
                        titleColorBackground.animateVisibility(true, visibleAlpha = 0.11f)
                        titleText.animateVisibility(true)
                        // Animate titleDownstateText to invisible
                        titleDownstateText.animateVisibility(false)
                        titleBackground.elevation = resources.getDimension(R.dimen.one_elevation)
                        isTitleVisible = true
                    }
                } else {
                    if (isTitleVisible) {
                        // Animate titleText and titleColorBackground to invisible
                        titleColorBackground.animateVisibility(false)
                        titleText.animateVisibility(false)
                        // Animate titleDownstateText to visible
                        titleDownstateText.animateVisibility(true)
                        titleBackground.elevation = resources.getDimension(R.dimen.zero_elevation)
                        isTitleVisible = false
                    }
                }
            }
        })

        findViewById<ImageButton>(R.id.back_btn_fla).setOnClickListener {
            onBackPressed()
        }

        infoText = findViewById(R.id.tv_lives_info)
        setupDifficultyToggles()
        setupLearningGameButtons()
        setCategoryListeners()

        // Save level for level up popup tracking
        lastLevel = XpManager.getLevel(XpManager.getXp(this))
    }

    override fun onResume() {
        super.onResume()
        updateLivesCount()
        updateLivesInfo()
        updateLearningGamesEnabled()
        updateXpAndLevelStats()
        val gameFinished = intent.getBooleanExtra("game_finished", false)
        val results = intent.getParcelableArrayListExtra<GameResultItem>("game_results")
        val totalQuestions = intent.getIntExtra("total_questions", results?.size ?: 0)
        val difficulty = intent.getStringExtra("difficulty") ?: "easy"
        if (results != null && results.isNotEmpty()) {
            showGameResultsPopup(results, gameFinished, totalQuestions, difficulty)
            intent.removeExtra("game_finished")
            intent.removeExtra("game_results")
            intent.removeExtra("total_questions")
            intent.removeExtra("difficulty")
        }
        showLevelUpPopupIfNeeded()
    }

    private fun showLevelUpPopupIfNeeded() {
        val xp = XpManager.getXp(this)
        val currentLevel = XpManager.getLevel(xp)
        if (lastLevel != -1 && currentLevel > lastLevel) {
            AlertDialog.Builder(this)
                .setTitle("Level Up!")
                .setMessage("Congratulations, you've reached level $currentLevel!")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setCancelable(true)
                .show()
        }
        lastLevel = currentLevel
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val gameFinished = intent.getBooleanExtra("game_finished", false)
        val results = intent.getParcelableArrayListExtra<GameResultItem>("game_results")
        val totalQuestions = intent.getIntExtra("total_questions", results?.size ?: 0)
        val difficulty = intent.getStringExtra("difficulty") ?: "easy"
        if (results != null && results.isNotEmpty()) {
            showGameResultsPopup(results, gameFinished, totalQuestions, difficulty)
        }
    }

    /**
     * Setup toggle buttons for single selection difficulty
     */
    private fun setupDifficultyToggles() {
        toggles = listOf(
            findViewById(R.id.toggle_easy),
            findViewById(R.id.toggle_medium),
            findViewById(R.id.toggle_hard)
        )
        toggles.forEach { toggle ->
            toggle.setOnCheckedChangeListener { btn, isChecked ->
                if (isChecked) {
                    toggles.filter { it != btn }.forEach { it.isChecked = false }
                }
            }
        }
        toggles[0].isChecked = true
    }

    private fun setupLearningGameButtons() {
        learningGameButtons = listOf(
            findViewById(R.id.btn_element_symbols),
            findViewById(R.id.btn_element_names),
            findViewById(R.id.btn_element_classifications),
            findViewById(R.id.btn_atomic_mass),
            findViewById(R.id.btn_chemical_reactions),
            findViewById(R.id.btn_mixed_questions)
        )
    }

    private fun getSelectedDifficulty(): String {
        return when {
            toggles[0].isChecked -> "easy"
            toggles[1].isChecked -> "medium"
            toggles[2].isChecked -> "hard"
            else -> "easy"
        }
    }

    private fun setCategoryListeners() {
        val categories = mapOf(
            R.id.btn_element_symbols to "element_symbols",
            R.id.btn_element_names to "element_names",
            R.id.btn_element_classifications to "element_classifications",
            R.id.btn_atomic_mass to "atomic_mass",
            R.id.btn_chemical_reactions to "chemical_reactions",
            R.id.btn_mixed_questions to "mixed_questions"
        )
        categories.forEach { (btnId, category) ->
            findViewById<View>(btnId).setOnClickListener {
                if (LivesManager.getLives(this) == 0) {
                    // Do nothing, disabled
                } else {
                    val intent = Intent(this, LearningGamesActivity::class.java)
                    intent.putExtra("difficulty", getSelectedDifficulty())
                    intent.putExtra("category", category)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_fla).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_fla).layoutParams = params

        val params2 = findViewById<TextView>(R.id.flashcard_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) +
                resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.flashcard_title_downstate).layoutParams = params2
    }

    private fun updateLivesInfo() {
        val lives = LivesManager.getLives(this)
        if (lives == 0) {
            infoText.visibility = View.VISIBLE
            val millis = LivesManager.getMillisToRefill(this)
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            infoText.text = "Out of lives! More lives in $hours hours and $minutes minutes."
        } else {
            infoText.visibility = View.GONE
        }
    }

    private fun updateLearningGamesEnabled() {
        val lives = LivesManager.getLives(this)
        val isEnabled = lives > 0
        val xp = XpManager.getXp(this)
        val level = XpManager.getLevel(xp)

        // Enable/disable all learning game buttons based on lives
        learningGameButtons.forEach { btn ->
            btn.isEnabled = isEnabled
            btn.alpha = if (isEnabled) 1f else 0.5f
        }

        // Element Groups: Only available from level 2
        val btnElementGroups = findViewById<View>(R.id.btn_element_classifications)
        val tvClassificationsReq = btnElementGroups.findViewById<TextView>(R.id.tv_classifications_requirement)
        if (level < 2) {
            btnElementGroups.isEnabled = false
            btnElementGroups.alpha = 0.5f
            tvClassificationsReq.visibility = View.VISIBLE
        } else if (isEnabled) {
            btnElementGroups.isEnabled = true
            btnElementGroups.alpha = 1f
            tvClassificationsReq.visibility = View.GONE
        } else {
            tvClassificationsReq.visibility = View.GONE
        }

        // Atomic Mass: Only available from level 5
        val btnAtomicMass = findViewById<View>(R.id.btn_atomic_mass)
        val tvAtomicMassReq = btnAtomicMass.findViewById<TextView>(R.id.tv_atomicmass_requirement)
        if (level < 5) {
            btnAtomicMass.isEnabled = false
            btnAtomicMass.alpha = 0.5f
            tvAtomicMassReq.visibility = View.VISIBLE
        } else if (isEnabled) {
            btnAtomicMass.isEnabled = true
            btnAtomicMass.alpha = 1f
            tvAtomicMassReq.visibility = View.GONE
        } else {
            tvAtomicMassReq.visibility = View.GONE
        }
    }

    /**
     * Update XP, Level, and ProgressBar to match scalable level system
     */
    private fun updateXpAndLevelStats() {
        val xp = XpManager.getXp(this)
        val level = XpManager.getLevel(xp)
        val minXp = XpManager.getXpForLevel(level)
        val maxXp = XpManager.getXpForLevel(level + 1)
        val xpInLevel = xp - minXp
        val xpRequired = maxXp - minXp
        val completed = getCompletedQuizzes()

        findViewById<TextView>(R.id.completed_quizzes_stat).text = completed.toString()
        findViewById<TextView>(R.id.total_xp_stat).text = xp.toString()
        findViewById<TextView>(R.id.level_stat).text = level.toString()
        findViewById<ProgressBar>(R.id.xp_progress).apply {
            max = xpRequired
            progress = xpInLevel
        }
        findViewById<TextView>(R.id.progress_text).text = "$xpInLevel/$xpRequired"
    }

    override fun onBackPressed() {
        super.onBackPressed()
        resultDialog?.let {
            if (it.isVisible) {
                it.dismiss()
                return
            }
        }
        finish()
    }

    private fun showGameResultsPopup(
        results: List<GameResultItem>,
        gameFinished: Boolean,
        totalQuestions: Int,
        difficulty: String = "easy"
    ) {
        if (resultDialog?.isVisible == true) return
        resultDialog = ResultDialogFragment.newInstance(results, gameFinished, totalQuestions, difficulty)
        resultDialog?.show(supportFragmentManager, "GameResultsPopup")
        updateXpAndLevelStats()
    }

    private fun getCompletedQuizzes(): Int {
        val prefs = getSharedPreferences("game_stats", MODE_PRIVATE)
        return prefs.getInt("completed_quizzes", 0)
    }

    private fun incrementCompletedQuizzes() {
        val prefs = getSharedPreferences("game_stats", MODE_PRIVATE)
        val current = prefs.getInt("completed_quizzes", 0)
        prefs.edit().putInt("completed_quizzes", current + 1).apply()
    }

    // Helper extension function for animating visibility
    fun View.animateVisibility(
        setVisible: Boolean,
        duration: Long = 200,
        visibleAlpha: Float = 1.0f
    ) {
        if (setVisible) {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(visibleAlpha)
                .setDuration(duration)
                .setListener(null)
        } else {
            animate()
                .alpha(0f)
                .setDuration(duration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = View.INVISIBLE
                    }
                })
        }
    }
}