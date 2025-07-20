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
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.preferences.ProVersion

class FlashCardActivity : BaseActivity() {

    private lateinit var toggles: List<ToggleButton>
    private lateinit var infoText: TextView
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
        setCategoryListeners()
        findViewById<ImageButton>(R.id.shuffle_btn).setOnClickListener {
            launchRandomUnlockedGame()
        }
    }

    override fun onResume() {
        super.onResume()
        updateLivesCount()
        updateLivesInfo()
        updateCategoryBoxes()
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

    private fun getSelectedDifficulty(): String {
        return when {
            toggles[0].isChecked -> "easy"
            toggles[1].isChecked -> "medium"
            toggles[2].isChecked -> "hard"
            else -> "easy"
        }
    }

    private fun setCategoryListeners() {
        val proCategoryIds = setOf(
            R.id.btn_discovered_by, R.id.btn_discovery_year, R.id.btn_electrical_type,
            R.id.btn_radioactive, R.id.btn_electronegativity, R.id.btn_block,
            R.id.btn_crystal_structure, R.id.btn_superconducting_point
        )
        val categories = mapOf(
            R.id.btn_element_symbols to "element_symbols",
            R.id.btn_element_names to "element_names",
            R.id.btn_element_classifications to "element_classifications",
            R.id.btn_appearance to "appearance",
            R.id.btn_atomic_number to "atomic_number",
            R.id.btn_atomic_mass to "atomic_mass",
            R.id.btn_density to "density",
            R.id.btn_magnetic_type to "magnetic_type",
            R.id.btn_phase_stp to "phase_stp",
            R.id.btn_neutron_cross_sectional to "neutron_cross_sectional",
            R.id.btn_specific_heat_capacity to "specific_heat_capacity",
            // Pro user categories:
            R.id.btn_discovered_by to "discovered_by",
            R.id.btn_discovery_year to "discovery_year",
            R.id.btn_electrical_type to "electrical_type",
            R.id.btn_radioactive to "radioactive",
            R.id.btn_electronegativity to "electronegativity",
            R.id.btn_block to "block",
            R.id.btn_crystal_structure to "crystal_structure",
            R.id.btn_superconducting_point to "superconducting_point",
            R.id.btn_mohs_hardness to "mohs_hardness",
            R.id.btn_vickers_hardness to "vickers_hardness",
            R.id.btn_brinell_hardness to "brinell_hardness"
        )
        categories.forEach { (btnId, category) ->
            val btn = findViewById<View>(btnId)
            btn.setOnClickListener {
                val isPro = checkProStatus()
                val isProCategory = proCategoryIds.contains(btnId)
                val canPlayGame = btn.isEnabled && (!isProCategory || isPro)

                if (isProCategory && !isPro) {
                    // Pro required, always send to ProActivity even if greyed out
                    startActivity(Intent(this, ProActivity::class.java))
                } else if (canPlayGame) {
                    // Start the game if enabled and (not pro category or is pro)
                    val intent = Intent(this, LearningGamesActivity::class.java)
                    intent.putExtra("difficulty", getSelectedDifficulty())
                    intent.putExtra("category", category)
                    startActivity(intent)
                }
                // else: do nothing if not enabled and not pro category (greyed out due to lives/level)
            }
        }
    }

    // Helper to set lock icon on section header
    private fun TextView.setLockDrawable(unlocked: Boolean) {
        val drawable = if (unlocked) R.drawable.ic_lock_open else R.drawable.ic_lock
        setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0)
    }

    private fun updateCategoryBoxes() {
        val xp = XpManager.getXp(this)
        val userLevel = XpManager.getLevel(xp)
        val isProUser = checkProStatus()
        val lives = LivesManager.getLives(this)
        val isEnabled = lives > 0

        val proCategoryButtons = listOf(
            Pair(R.id.btn_discovered_by, R.id.pro_badge_discovered_by),
            Pair(R.id.btn_discovery_year, R.id.pro_badge_discovery_year),
            Pair(R.id.btn_electrical_type, R.id.pro_badge_electrical_type),
            Pair(R.id.btn_radioactive, R.id.pro_badge_radioactive),
            Pair(R.id.btn_electronegativity, R.id.pro_badge_electronegativity),
            Pair(R.id.btn_block, R.id.pro_badge_block),
            Pair(R.id.btn_crystal_structure, R.id.pro_badge_crystal_structure),
            Pair(R.id.btn_superconducting_point, R.id.pro_badge_superconducting_point),
            Pair(R.id.btn_mohs_hardness, R.id.pro_badge_mohs_hardness),
            Pair(R.id.btn_vickers_hardness, R.id.pro_badge_vickers_hardness),
            Pair(R.id.btn_brinell_hardness, R.id.pro_badge_brinell_hardness)
        )

        for ((btnId, badgeId) in proCategoryButtons) {
            val badge = findViewById<TextView>(badgeId)
            badge?.visibility = View.VISIBLE
        }

        val proButtonsByBox = mapOf(
            R.id.box_0_4 to listOf(R.id.btn_discovered_by, R.id.btn_discovery_year),
            R.id.box_5_9 to listOf(R.id.btn_electrical_type, R.id.btn_radioactive),
            R.id.box_10_14 to listOf(R.id.btn_electronegativity, R.id.btn_block),
            R.id.box_15_19 to listOf(R.id.btn_crystal_structure, R.id.btn_superconducting_point),
            R.id.box_20_24 to listOf(R.id.btn_mohs_hardness, R.id.btn_vickers_hardness, R.id.btn_brinell_hardness)
        )
        val boxesWithLevels = listOf(
            Triple(R.id.box_0_4, R.id.title_box_0_4, 0..4),
            Triple(R.id.box_5_9, R.id.title_box_5_9, 5..9),
            Triple(R.id.box_10_14, R.id.title_box_10_14, 10..14),
            Triple(R.id.box_15_19, R.id.title_box_15_19, 15..19),
            Triple(R.id.box_20_24, R.id.title_box_20_24, 20..24)
        )
        val proCategoryIds = setOf(
            R.id.btn_discovered_by, R.id.btn_discovery_year, R.id.btn_electrical_type,
            R.id.btn_radioactive, R.id.btn_electronegativity, R.id.btn_block,
            R.id.btn_crystal_structure, R.id.btn_superconducting_point,
            R.id.btn_mohs_hardness, R.id.btn_vickers_hardness, R.id.btn_brinell_hardness
        )

        for ((boxId, titleId, levelRange) in boxesWithLevels) {
            val box = findViewById<View>(boxId)
            val title = findViewById<TextView>(titleId)
            val unlocked = userLevel >= levelRange.last

            // If the userLevel is within the range, it's the "current" box, so also unlocked
            val current = userLevel in levelRange
            val boxIsUnlocked = unlocked || current

            box.alpha = if (boxIsUnlocked) 1f else 0.5f
            title.setLockDrawable(boxIsUnlocked)

            // Set enabled/alpha for each button in this box
            for (i in 0 until (box as ViewGroup).childCount) {
                val child = box.getChildAt(i)
                if (child.id == titleId) continue // skip the header
                val isProBtn = proCategoryIds.contains(child.id)
                child.isEnabled = boxIsUnlocked && isEnabled && (!isProBtn || isProUser)
                child.alpha = if (child.isEnabled) 1f else 0.5f
            }
        }
    }

    private fun checkProStatus(): Boolean {
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        return proPrefValue == 100
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
        findViewById<TextView>(R.id.total_xp_stat).text = "Total XP: $xp"
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

    private fun getAllUnlockedCategoryButtons(): List<Pair<View, String>> {
        val proCategoryIds = setOf(
            R.id.btn_discovered_by, R.id.btn_discovery_year, R.id.btn_electrical_type,
            R.id.btn_radioactive, R.id.btn_electronegativity, R.id.btn_block,
            R.id.btn_crystal_structure, R.id.btn_superconducting_point,
            R.id.btn_mohs_hardness, R.id.btn_vickers_hardness, R.id.btn_brinell_hardness
        )
        val categories = mapOf(
            R.id.btn_element_symbols to "element_symbols",
            R.id.btn_element_names to "element_names",
            R.id.btn_element_classifications to "element_classifications",
            R.id.btn_appearance to "appearance",
            R.id.btn_atomic_number to "atomic_number",
            R.id.btn_atomic_mass to "atomic_mass",
            R.id.btn_density to "density",
            R.id.btn_magnetic_type to "magnetic_type",
            R.id.btn_phase_stp to "phase_stp",
            R.id.btn_neutron_cross_sectional to "neutron_cross_sectional",
            R.id.btn_specific_heat_capacity to "specific_heat_capacity",
            // Pro user categories:
            R.id.btn_discovered_by to "discovered_by",
            R.id.btn_discovery_year to "discovery_year",
            R.id.btn_electrical_type to "electrical_type",
            R.id.btn_radioactive to "radioactive",
            R.id.btn_electronegativity to "electronegativity",
            R.id.btn_block to "block",
            R.id.btn_crystal_structure to "crystal_structure",
            R.id.btn_superconducting_point to "superconducting_point",
            R.id.btn_mohs_hardness to "mohs_hardness",
            R.id.btn_vickers_hardness to "vickers_hardness",
            R.id.btn_brinell_hardness to "brinell_hardness"
        )
        val isPro = checkProStatus()
        val unlocked = mutableListOf<Pair<View, String>>()
        for ((btnId, category) in categories) {
            val btn = findViewById<View>(btnId)
            val isProCategory = proCategoryIds.contains(btnId)
            if (btn != null && btn.isEnabled && (!isProCategory || isPro)) {
                unlocked.add(btn to category)
            }
        }
        return unlocked
    }

    private fun launchRandomUnlockedGame() {
        val unlocked = getAllUnlockedCategoryButtons()
        if (unlocked.isEmpty()) {
            Toast.makeText(this, "No unlocked games available!", Toast.LENGTH_SHORT).show()
            return
        }
        val (btn, category) = unlocked.random()
        // Use currently selected difficulty
        val difficulty = getSelectedDifficulty()
        val isProCategory = setOf(
            R.id.btn_discovered_by, R.id.btn_discovery_year, R.id.btn_electrical_type,
            R.id.btn_radioactive, R.id.btn_electronegativity, R.id.btn_block,
            R.id.btn_crystal_structure, R.id.btn_superconducting_point,
            R.id.btn_mohs_hardness, R.id.btn_vickers_hardness, R.id.btn_brinell_hardness
        ).contains(btn.id)
        if (isProCategory && !checkProStatus()) {
            startActivity(Intent(this, ProActivity::class.java))
            return
        }
        val intent = Intent(this, LearningGamesActivity::class.java)
        intent.putExtra("difficulty", difficulty)
        intent.putExtra("category", category)
        startActivity(intent)
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
}