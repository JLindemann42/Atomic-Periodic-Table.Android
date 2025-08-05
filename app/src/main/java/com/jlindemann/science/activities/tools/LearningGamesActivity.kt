package com.jlindemann.science.activities.tools

import GameResultItem
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.util.LivesManager
import com.jlindemann.science.util.XpManager
import com.jlindemann.science.views.AnimatedEffectView
import org.json.JSONArray
import kotlin.math.roundToInt

class LearningGamesActivity : BaseActivity() {

    private lateinit var questions: List<Question>
    private var currentQuestionIndex = 0
    private var difficulty = "easy"
    private var totalQuestions = 10
    private lateinit var animatedEffectView: AnimatedEffectView
    private var timerAnimator: ValueAnimator? = null
    private var isAnswering = true
    private var hasLeftGame = false
    private var leaveDialogShowing = false
    private var quizCompleted = false
    private lateinit var category: String

    private val handler = Handler(Looper.getMainLooper())
    private var pendingNextQuestionRunnable: Runnable? = null

    // Results tracking
    private val gameResults = mutableListOf<GameResultItem>()

    data class Question(
        val question: String,
        val correctAnswer: String,
        val alternatives: List<String>,
        val baseXp: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = com.jlindemann.science.preferences.ThemePreference(this)
        val themePrefValue = themePreference.getValue()
        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                android.content.res.Configuration.UI_MODE_NIGHT_NO -> setTheme(R.style.AppTheme)
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> setTheme(R.style.AppThemeDark)
            }
        }
        if (themePrefValue == 0) setTheme(R.style.AppTheme)
        if (themePrefValue == 1) setTheme(R.style.AppThemeDark)

        setContentView(R.layout.activity_learninggames)
        findViewById<FrameLayout>(R.id.view_learn).systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        difficulty = intent.getStringExtra("difficulty") ?: "easy"
        category = intent.getStringExtra("category") ?: "element_symbols"

        totalQuestions = when (difficulty) {
            "easy" -> 8
            "medium" -> 16
            "hard" -> 24
            else -> 8
        }

        questions = generateQuestions(category, totalQuestions)
        setupQuestionUI()
        updateLivesCount()
        setupAnswerListeners()
        setupBackButton()

        // Setup background effect view
        val effectOverlay = findViewById<FrameLayout>(R.id.effect_overlay)
        animatedEffectView = AnimatedEffectView(this)
        effectOverlay.addView(
            animatedEffectView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun setupAnswerListeners() {
        val answerCards = listOf(
            findViewById<LinearLayout>(R.id.answer_1),
            findViewById<LinearLayout>(R.id.answer_2),
            findViewById<LinearLayout>(R.id.answer_3),
            findViewById<LinearLayout>(R.id.answer_4)
        )
        answerCards.forEachIndexed { index, card ->
            card.setOnClickListener {
                if (isAnswering && !hasLeftGame && index < questions[currentQuestionIndex].alternatives.size) {
                    isAnswering = false
                    timerAnimator?.cancel()
                    val selectedAnswer = questions[currentQuestionIndex].alternatives[index]
                    checkAnswer(selectedAnswer)
                }
            }
        }
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.back_btn_learn).setOnClickListener {
            showExitConfirmationDialog()
        }
    }

    override fun onBackPressed() {
        showExitConfirmationDialog()
    }

    private fun showExitConfirmationDialog() {
        if (leaveDialogShowing || hasLeftGame) return
        leaveDialogShowing = true
        android.app.AlertDialog.Builder(this)
            .setTitle("Leave Game?")
            .setMessage("Are you sure you want to leave the game? You will lose 5 lives.")
            .setPositiveButton("Leave") { _, _ ->
                leaveDialogShowing = false
                leaveGameAndLoseLives()
            }
            .setNegativeButton("Stay") { dialog, _ ->
                leaveDialogShowing = false
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun leaveGameAndLoseLives() {
        if (!hasLeftGame) {
            hasLeftGame = true
            quizCompleted = false
            LivesManager.loseLives(this, 5)
            updateLivesCount()
            cleanupPending()
            finishWithResults(forceNotFinished = true)
        }
    }

    private fun cleanupPending() {
        timerAnimator?.cancel()
        pendingNextQuestionRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun setupQuestionUI() {
        if (hasLeftGame) return
        isAnswering = true
        setAnswerEnabled(true)
        val timeLimit = when (difficulty) {
            "easy" -> 20_000L
            "medium" -> 12_000L
            "hard" -> 7_500L
            else -> 20_000L
        }
        val progressBar = findViewById<ProgressBar>(R.id.time_progress)
        val questionText = findViewById<TextView>(R.id.tv_question)
        val questionNumber = findViewById<TextView>(R.id.tv_question_number)
        val grid = findViewById<GridLayout>(R.id.grid_answers)

        // Update question number display
        questionNumber.text = "${currentQuestionIndex + 1}/${questions.size}"

        progressBar.visibility = View.VISIBLE
        progressBar.alpha = 1f
        progressBar.progress = 100
        questionText.visibility = View.VISIBLE
        questionText.alpha = 1f
        grid.visibility = View.VISIBLE
        grid.alpha = 0f

        timerAnimator?.cancel()
        timerAnimator = ValueAnimator.ofInt(100, 0).apply {
            duration = timeLimit
            addUpdateListener { va ->
                progressBar.progress = va.animatedValue as Int
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isAnswering && !hasLeftGame) {
                        isAnswering = false
                        checkAnswer("__TIMEOUT__")
                    }
                }
            })
            start()
        }
        val q = questions[currentQuestionIndex]
        questionText.text = q.question
        val answerIds = listOf(R.id.answer_text_1, R.id.answer_text_2, R.id.answer_text_3, R.id.answer_text_4)
        q.alternatives.forEachIndexed { i, alt ->
            findViewById<TextView>(answerIds[i]).text = alt
        }

        // Handle radioactive case: only two options, hide 3 and 4
        if (category == "radioactive") {
            findViewById<LinearLayout>(R.id.answer_3).visibility = View.GONE
            findViewById<LinearLayout>(R.id.answer_4).visibility = View.GONE
        } else if (category == "electrical_type") {
            findViewById<LinearLayout>(R.id.answer_4).visibility = View.GONE
        }
        else if (category == "phase_stp") {
            findViewById<LinearLayout>(R.id.answer_4).visibility = View.GONE
        }
        else {
            findViewById<LinearLayout>(R.id.answer_3).visibility = View.VISIBLE
            findViewById<LinearLayout>(R.id.answer_4).visibility = View.VISIBLE
        }


        grid.animate().alpha(1f).setDuration(300).start()
        progressBar.animate().alpha(1f).setDuration(300).start()
        questionText.animate().alpha(1f).setDuration(300).start()
    }

    private fun setAnswerEnabled(enabled: Boolean) {
        listOf(
            findViewById<LinearLayout>(R.id.answer_1),
            findViewById<LinearLayout>(R.id.answer_2),
            findViewById<LinearLayout>(R.id.answer_3),
            findViewById<LinearLayout>(R.id.answer_4)
        ).forEach {
            it.isEnabled = enabled
        }
    }

    private fun getXpMultiplier(): Double {
        return when (difficulty) {
            "medium" -> 1.3
            "hard" -> 1.5
            else -> 1.0
        }
    }

    private fun getBaseXp(category: String): Int = when (category) {
        "element_symbols" -> 8
        "element_names" -> 8
        "element_classifications" -> 13
        "discovered_by" -> 16
        "discovery_year" -> 16
        "appearance" -> 12
        "atomic_number" -> 8
        "electrical_type" -> 16
        "radioactive" -> 16
        "atomic_mass" -> 25
        "density" -> 40
        "electronegativity" -> 30
        "block" -> 15
        "magnetic_type" -> 18
        "phase_stp" -> 10
        "crystal_structure" -> 40
        "superconducting_point" -> 50
        "neutron_cross_sectional" -> 50
        "specific heat capacity" -> 50
        "mohs_hardness" -> 60
        "vickers_hardness" -> 60
        "brinell_hardness" -> 60
        else -> 5
    }

    private fun getLivesLost(): Int {
        return when (difficulty) {
            "hard" -> 2
            else -> 1 // easy and medium
        }
    }

    private fun checkAnswer(selectedAnswer: String) {
        if (hasLeftGame) return
        isAnswering = false
        setAnswerEnabled(false)
        timerAnimator?.cancel()
        val q = questions[currentQuestionIndex]
        val correct = normalizeLabel(selectedAnswer) == normalizeLabel(q.correctAnswer)

        gameResults.add(
            GameResultItem(
                question = q.question,
                pickedAnswer = if (selectedAnswer == "__TIMEOUT__") "Timeout" else selectedAnswer,
                correctAnswer = q.correctAnswer,
                wasCorrect = correct,
                baseXp = q.baseXp
            )
        )

        val effectColors = if (selectedAnswer == "__TIMEOUT__")
            listOf(Color.parseColor("#D32F2F"), Color.parseColor("#C62828"), Color.parseColor("#FFCDD2"))
        else if (correct)
            listOf(Color.parseColor("#43A047"), Color.parseColor("#388E3C"), Color.parseColor("#81C784"))
        else
            listOf(Color.parseColor("#D32F2F"), Color.parseColor("#C62828"), Color.parseColor("#FFCDD2"))

        val effectOverlay = findViewById<FrameLayout>(R.id.effect_overlay)
        val grid = findViewById<GridLayout>(R.id.grid_answers)
        val progressBar = findViewById<ProgressBar>(R.id.time_progress)
        val questionText = findViewById<TextView>(R.id.tv_question)

        fadeOutView(grid, 200) {
            fadeOutView(progressBar, 200)
            fadeOutView(questionText, 200)

            fadeInEffectOverlay(effectOverlay, 300)
            animatedEffectView.post {
                animatedEffectView.startAnimation(effectColors) {
                    fadeOutEffectOverlay(effectOverlay, 300)
                }
            }

            val xpGained = if (selectedAnswer == "__TIMEOUT__" || !correct) 0 else (q.baseXp * getXpMultiplier()).roundToInt()
            if (selectedAnswer == "__TIMEOUT__") {
                showResultCard(false, selectedAnswer, 0)
            } else if (correct) {
                XpManager.addXp(this, xpGained)
                showResultCard(true, selectedAnswer, xpGained)
            } else {
                showResultCard(false, selectedAnswer, 0)
            }

            pendingNextQuestionRunnable?.let { handler.removeCallbacks(it) }
            pendingNextQuestionRunnable = Runnable {
                if (!hasLeftGame) {
                    hideResultCard()
                    nextQuestionWithFlip(grid, correct, selectedAnswer)
                }
            }
            handler.postDelayed(pendingNextQuestionRunnable!!, 2000)
        }

        val livesLost = getLivesLost()
        if (selectedAnswer == "__TIMEOUT__" || !correct) {
            val lost = LivesManager.loseLives(this, livesLost)
            updateLivesCount()
            if (lost && LivesManager.getLives(this) == 0) {
                finishWithResults()
            }
            if (!lost) {
                finishWithResults()
            }
        }
    }

    private fun nextQuestionWithFlip(grid: GridLayout, wasCorrect: Boolean, selectedAnswer: String) {
        if (hasLeftGame) return
        currentQuestionIndex++
        if (currentQuestionIndex >= questions.size) {
            quizCompleted = true
            finishWithResults()
        } else {
            setupQuestionUI()
            grid.rotationY = -90f
            grid.visibility = View.VISIBLE
            grid.animate().rotationY(0f).setDuration(300).start()
        }
    }

    private fun getDifficultyLabel(): String {
        return when (difficulty) {
            "medium" -> "Medium"
            "hard" -> "Hard"
            else -> "Easy"
        }
    }

    private fun finishWithResults(forceNotFinished: Boolean = false) {
        cleanupPending()
        val allAnswersCompleted = gameResults.size == questions.size && gameResults.all { it.pickedAnswer != null }
        val finishedGame = quizCompleted && allAnswersCompleted && !forceNotFinished
        val xpMultiplier = getXpMultiplier()
        val xpElements = gameResults.filter { it.wasCorrect }.sumOf { (it.baseXp * xpMultiplier).roundToInt() }
        val xpGameWin = if (finishedGame) (25 * xpMultiplier).roundToInt() else 0
        val xpPerfect = if (finishedGame && gameResults.all { it.wasCorrect }) (20 * xpMultiplier).roundToInt() else 0
        val totalXp = xpElements + xpGameWin + xpPerfect

        updateFlashcardAchievements(finishedGame, finishedGame && gameResults.all { it.wasCorrect })

        if (finishedGame) {
            XpManager.addXp(this, xpGameWin)
            if (gameResults.all { it.wasCorrect }) {
                XpManager.addXp(this, xpPerfect)
            }
            val prefs = getSharedPreferences("game_stats", MODE_PRIVATE)
            val current = prefs.getInt("completed_quizzes", 0)
            prefs.edit().putInt("completed_quizzes", current + 1).apply()
        }

        val intent = Intent(this, FlashCardActivity::class.java)
        intent.putParcelableArrayListExtra("game_results", ArrayList(gameResults))
        intent.putExtra("game_finished", finishedGame)
        intent.putExtra("total_questions", totalQuestions)
        intent.putExtra("difficulty", difficulty)
        intent.putExtra("difficulty_label", getDifficultyLabel())
        intent.putExtra("xp_multiplier", xpMultiplier)
        intent.putExtra("total_xp", totalXp)
        intent.putExtra("category", category)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun showResultCard(correct: Boolean, selectedAnswer: String, xpGained: Int = 0) {
        val resultCard = findViewById<FrameLayout>(R.id.result_card_overlay)
        val resultText = findViewById<TextView>(R.id.result_text)
        val resultSubtext = findViewById<TextView>(R.id.result_subtext)
        resultCard.visibility = View.VISIBLE

        val livesLost = getLivesLost()

        if (selectedAnswer == "__TIMEOUT__") {
            resultText.text = "Time's Up"
            resultSubtext.text = if (livesLost == 1) "Lost 1 life" else "Lost $livesLost lives"
        } else if (correct) {
            resultText.text = "Correct"
            resultSubtext.text = if (xpGained > 0) "+${xpGained}xp" else ""
        } else {
            resultText.text = "Wrong"
            resultSubtext.text = if (livesLost == 1) "Lost 1 life" else "Lost $livesLost lives"
        }
    }

    private fun hideResultCard() {
        findViewById<FrameLayout>(R.id.result_card_overlay).visibility = View.GONE
    }

    // Normalization function to canonicalize labels and answers
    private fun normalizeLabel(label: String): String {
        return label.trim()
            .replace("-", " ")
            .replace(Regex("\\bmetals\\b", RegexOption.IGNORE_CASE), "Metal")
            .replace(Regex("\\bmetal\\b", RegexOption.IGNORE_CASE), "Metal")
            .replace(Regex("^\\s*---\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun generateQuestions(category: String, count: Int): List<Question> {
        val elementFiles = assets.list("")?.filter { it.endsWith(".json") } ?: emptyList()
        val elements = elementFiles.flatMap { loadElementsFromAsset(it) }
            .filter { it.element.isNotBlank() }

        val questions = mutableListOf<Question>()
        val usedElements = mutableSetOf<String>()

        fun wrongAnswersFor(fieldSelector: (ElementData) -> String, correct: String): List<String> =
            elements
                .filter {
                    val v = normalizeLabel(fieldSelector(it))
                    v != normalizeLabel(correct) && v.isNotBlank() && v != "---"
                }
                .map { normalizeLabel(fieldSelector(it)) }
                .filter { it.isNotBlank() && it != "---" }
                .distinct()
                .shuffled()
                .take(3)

        repeat(count) {
            val element = elements.filter { it.element !in usedElements }.randomOrNull() ?: elements.random()
            usedElements.add(element.element)

            val baseXp = getBaseXp(category)
            val (questionText, correct, alternatives) = when (category) {
                "element_symbols" -> {
                    val question = "What is the symbol for ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.short)
                    val wrongs = wrongAnswersFor({ it.short }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "element_names" -> {
                    val question = "What is the name for ${normalizeLabel(element.short)}?"
                    val correct = normalizeLabel(element.element)
                    val wrongs = wrongAnswersFor({ it.element }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "element_classifications" -> {
                    val question = "What is the element group of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.element_group)
                    val wrongs = wrongAnswersFor({ it.element_group }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "discovered_by" -> {
                    val question = "Who discovered ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.element_discovered_name)
                    val wrongs = wrongAnswersFor({ it.element_discovered_name }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "discovery_year" -> {
                    val question = "In what year was ${normalizeLabel(element.element)} discovered?"
                    val correct = normalizeLabel(element.element_year)
                    val wrongs = wrongAnswersFor({ it.element_year }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "appearance" -> {
                    val question = "What is the appearance of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.appearance)
                    val wrongs = wrongAnswersFor({ it.appearance }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "atomic_number" -> {
                    val question = "What is the atomic number of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.atomic_number)
                    val wrongs = wrongAnswersFor({ it.atomic_number }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "electrical_type" -> {
                    val question = "What is the electrical type of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.electrical_type)
                    val wrongs = wrongAnswersFor({ it.electrical_type }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "radioactive" -> {
                    val question = "Is ${normalizeLabel(element.element)} radioactive?"
                    val isRadioactive = element.radioactive.trim().lowercase() == "yes"
                    val correct = if (isRadioactive) "Yes" else "No"
                    val options = listOf("Yes", "No")
                    Triple(question, correct, options)
                }
                "atomic_mass" -> {
                    val question = "What is the atomic mass of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.element_atomicmass)
                    val wrongs = wrongAnswersFor({ it.element_atomicmass }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "density" -> {
                    val question = "What is the density of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.density)
                    val wrongs = wrongAnswersFor({ it.density }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "electronegativity" -> {
                    val question = "What is the electronegativity of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.element_electronegativty)
                    val wrongs = wrongAnswersFor({ it.element_electronegativty }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "block" -> {
                    val question = "What block does ${normalizeLabel(element.element)} belong to?"
                    val correct = normalizeLabel(element.element_block)
                    val wrongs = wrongAnswersFor({ it.element_block }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "magnetic_type" -> {
                    val question = "What is the magnetic type of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.magnetic_type)
                    val wrongs = wrongAnswersFor({ it.magnetic_type }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "phase_stp" -> {
                    val question = "What is the phase of ${normalizeLabel(element.element)} at STP?"
                    val correct = normalizeLabel(element.element_phase)
                    val wrongs = wrongAnswersFor({ it.element_phase }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "crystal_structure" -> {
                    val question = "What is the crystal structure of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.crystal_structure)
                    val wrongs = wrongAnswersFor({ it.crystal_structure }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "superconducting_point" -> {
                    val question = "What is the superconducting point of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.superconducting_point)
                    val wrongs = wrongAnswersFor({ it.superconducting_point }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "neutron_cross_sectional" -> {
                    val question = "What is the neutron cross sectional of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.neutron_cross_sectional)
                    val wrongs = wrongAnswersFor({ it.neutron_cross_sectional }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "specific_heat_capacity" -> {
                    val question = "What is the specific heat capacity of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.specific_heat_capacity)
                    val wrongs = wrongAnswersFor({ it.specific_heat_capacity }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "mohs_hardness" -> {
                    val question = "What is the mohs hardness of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.mohs_hardness)
                    val wrongs = wrongAnswersFor({ it.mohs_hardness }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "vickers_hardness" -> {
                    val question = "What is the vickers hardness of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.vickers_hardness)
                    val wrongs = wrongAnswersFor({ it.vickers_hardness }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "brinell_hardness" -> {
                    val question = "What is the brinell hardness of ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.brinell_hardness)
                    val wrongs = wrongAnswersFor({ it.brinell_hardness }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
                "mixed_questions" -> {
                    val categories = listOf(
                        "element_symbols", "element_names", "element_classifications", "discovered_by", "discovery_year",
                        "appearance", "atomic_number", "electrical_type", "radioactive",
                        "atomic_mass", "density", "electronegativity", "block",
                        "magnetic_type", "phase_stp", "crystal_structure", "superconducting_point"
                    )
                    val cat = categories.random()
                    val mixedQ = generateQuestions(cat, 1)[0]
                    Triple(mixedQ.question, mixedQ.correctAnswer, mixedQ.alternatives)
                }
                else -> {
                    val question = "What is the symbol for ${normalizeLabel(element.element)}?"
                    val correct = normalizeLabel(element.short)
                    val wrongs = wrongAnswersFor({ it.short }, correct)
                    Triple(question, correct, (wrongs + correct).distinct().shuffled())
                }
            }
            // Skip question if correct answer is blank or placeholder
            if (correct.isBlank() || correct == "---") {
                return@repeat
            }
            val filteredAlternatives = alternatives
                .map(::normalizeLabel)
                .filter { it.isNotBlank() && it != "---" }
                .distinct()
            // Skip if there are not enough alternatives (at least 2)
            if (filteredAlternatives.size < 2) {
                return@repeat
            }
            questions.add(Question(questionText, correct, filteredAlternatives, baseXp))
        }
        return questions
    }

    data class ElementData(
        val element: String,
        val short: String,
        val element_group: String,
        val element_atomicmass: String,
        val appearance: String,
        val density: String,
        val element_ion_charge: String,
        val atomic_number: String,
        val element_discovered_name: String,
        val element_year: String,
        val electrical_type: String,
        val radioactive: String,
        val element_electronegativty: String,
        val element_block: String,
        val magnetic_type: String,
        val element_phase: String,
        val crystal_structure: String,
        val superconducting_point: String,
        val neutron_cross_sectional: String,
        val specific_heat_capacity: String,
        val mohs_hardness: String,
        val vickers_hardness: String,
        val brinell_hardness: String
    )

    private fun loadElementsFromAsset(filename: String): List<ElementData> {
        return try {
            val json = assets.open(filename).bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ElementData(
                    element = obj.optString("element"),
                    short = obj.optString("short"),
                    appearance = obj.optString("element_appearance"),
                    density = obj.optString("element_density"),
                    element_group = obj.optString("element_group"),
                    element_atomicmass = obj.optString("element_atomicmass"),
                    element_ion_charge = obj.optString("element_ion_charge"),
                    atomic_number = obj.optString("element_atomic_number"),
                    element_discovered_name = obj.optString("element_discovered_name"),
                    element_year = obj.optString("element_year"),
                    electrical_type = obj.optString("electrical_type"),
                    radioactive = obj.optString("radioactive"),
                    element_electronegativty = obj.optString("element_electronegativty"),
                    element_block = obj.optString("element_block"),
                    magnetic_type = obj.optString("magnetic_type"),
                    element_phase = obj.optString("element_phase"),
                    crystal_structure = obj.optString("crystal_structure"),
                    superconducting_point = obj.optString("superconducting_point"),
                    neutron_cross_sectional = obj.optString("neutron_cross_sectional"),
                    specific_heat_capacity = obj.optString("element_specific_heat_capacity"),
                    mohs_hardness = obj.optString("mohs_hardness"),
                    vickers_hardness = obj.optString("vickers_hardness"),
                    brinell_hardness = obj.optString("brinell_hardness")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun updateLivesCount() {
        // MODIFIED: Show "∞" (infinity) if ProPlusVersion == 100
        val proPlusPref = com.jlindemann.science.preferences.ProPlusVersion(this)
        val isInfinite = proPlusPref.getValue() == 100
        val livesTextView = findViewById<TextView>(R.id.tv_lives_count)
        val livesLabelView = findViewById<TextView>(R.id.tv_lives)
        if (isInfinite) {
            livesTextView.text = "∞"
            livesLabelView.text = "Lives: ∞"
        } else {
            val lives = LivesManager.getLives(this)
            livesTextView.text = lives.toString()
            livesLabelView.text = "Lives: $lives"
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasLeftGame) {
            LivesManager.refillLivesIfNeeded(this)
            updateLivesCount()
        }
    }

    private fun updateFlashcardAchievements(finishedGame: Boolean, allCorrect: Boolean) {
        // Load flashcard achievements only (IDs: 101001, 101002, 101003, 101004)
        val achievementIds = listOf(101001, 101002, 101003, 101004)
        val achievements = ArrayList<Achievement>()
        AchievementModel.getList(this, achievements)
        val achMap = achievements.associateBy { it.id }

        // "Perfect Game": Get all questions correct in a game (ID: 101001)
        if (allCorrect) {
            achMap[101001]?.let {
                it.incrementProgress(this, 1)
            }
        }

        // "Quiz Enthusiast": Play 10 games (ID: 101002)
        // "Quiz Master": Play 100 games (ID: 101003)
        // "Getting the hang of it": Play 500 games (ID: 101004)
        if (finishedGame) {
            listOf(101002, 101003, 101004).forEach { id ->
                achMap[id]?.let { it.incrementProgress(this, 1) }
            }
        }
    }


    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_learn).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_learn).layoutParams = params

        val params2 = findViewById<TextView>(R.id.tv_question_number).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) +
                resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.tv_question_number).layoutParams = params2
    }

    // Helpers for overlay fade
    private fun fadeInEffectOverlay(effectOverlay: View, duration: Long = 300, onEnd: (() -> Unit)? = null) {
        effectOverlay.alpha = 0f
        effectOverlay.visibility = View.VISIBLE
        effectOverlay.animate()
            .alpha(1f)
            .setDuration(duration)
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    private fun fadeOutEffectOverlay(effectOverlay: View, duration: Long = 300, onEnd: (() -> Unit)? = null) {
        effectOverlay.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                effectOverlay.visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }

    // Helpers for fading arbitrary views
    private fun fadeOutView(view: View, duration: Long = 300, onEnd: (() -> Unit)? = null) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                view.visibility = View.INVISIBLE
                onEnd?.invoke()
            }.start()
    }

    private fun fadeInView(view: View, duration: Long = 300, onEnd: (() -> Unit)? = null) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupPending()
    }
}