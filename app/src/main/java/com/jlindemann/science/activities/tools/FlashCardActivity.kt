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
import java.util.concurrent.TimeUnit

class FlashCardActivity : BaseActivity() {

    private lateinit var toggles: List<ToggleButton>
    private lateinit var infoText: TextView
    private lateinit var learningGameButtons: List<View>
    private var resultDialog: ResultDialogFragment? = null

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
        scrollView?.viewTreeObserver?.addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
            override fun onScrollChanged() {
                if (scrollView.scrollY > 150f) {
                    findViewById<FrameLayout>(R.id.common_title_back_fla_color).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.flashcard_title).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.flashcard_title_downstate).visibility = View.INVISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_fla).elevation =
                        (resources.getDimension(R.dimen.one_elevation))
                } else {
                    findViewById<FrameLayout>(R.id.common_title_back_fla_color).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.flashcard_title).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.flashcard_title_downstate).visibility = View.VISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_fla).elevation =
                        (resources.getDimension(R.dimen.zero_elevation))
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

        // --- Game result popup logic ---
        if (intent.getBooleanExtra("game_finished", false)) {
            val results = intent.getParcelableArrayListExtra<GameResultItem>("game_results")
            if (results != null && results.isNotEmpty()) {
                showGameResultsPopup(results)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("game_finished", false)) {
            val results = intent.getParcelableArrayListExtra<GameResultItem>("game_results")
            if (results != null && results.isNotEmpty()) {
                showGameResultsPopup(results)
            }
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
        // Optionally: Set default checked (Easy)
        toggles[0].isChecked = true
    }

    private fun setupLearningGameButtons() {
        learningGameButtons = listOf(
            findViewById(R.id.btn_element_symbols),
            findViewById(R.id.btn_element_classifications),
            findViewById(R.id.btn_atomic_mass),
            findViewById(R.id.btn_chemical_reactions),
            findViewById(R.id.btn_mixed_questions)
        )
    }

    /**
     * Get currently selected difficulty
     */
    private fun getSelectedDifficulty(): String {
        return when {
            toggles[0].isChecked -> "easy"
            toggles[1].isChecked -> "medium"
            toggles[2].isChecked -> "hard"
            else -> "easy"
        }
    }

    /**
     * Setup listeners for category buttons to launch LearningGamesActivity
     */
    private fun setCategoryListeners() {
        val categories = mapOf(
            R.id.btn_element_symbols to "element_symbols",
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

    override fun onResume() {
        super.onResume()
        updateLivesCount()
        updateLivesInfo()
        updateLearningGamesEnabled()
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
        val isEnabled = LivesManager.getLives(this) > 0
        learningGameButtons.forEach { btn ->
            btn.isEnabled = isEnabled
            btn.alpha = if (isEnabled) 1f else 0.5f
        }
    }

    // ---- Exit confirmation ----
    override fun onBackPressed() {
        resultDialog?.let {
            if (it.isVisible) {
                it.dismiss()
                return
            }
        }
        // Just finish activity, no lives lost!
        finish()
    }

    // ---- Game Results Popup ----
    private fun showGameResultsPopup(results: List<GameResultItem>) {
        if (resultDialog?.isVisible == true) return
        resultDialog = ResultDialogFragment.newInstance(results)
        resultDialog?.show(supportFragmentManager, "GameResultsPopup")
    }
}