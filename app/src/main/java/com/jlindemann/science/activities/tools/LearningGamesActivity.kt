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
import com.jlindemann.science.util.LivesManager
import com.jlindemann.science.util.XpManager
import com.jlindemann.science.views.AnimatedEffectView
import org.json.JSONArray

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

    private val handler = Handler(Looper.getMainLooper())
    private var pendingNextQuestionRunnable: Runnable? = null

    // Results tracking
    private val gameResults = mutableListOf<GameResultItem>()

    data class Question(
        val question: String,
        val correctAnswer: String,
        val alternatives: List<String>
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
        val category = intent.getStringExtra("category") ?: "element_symbols"

        totalQuestions = when (difficulty) {
            "easy" -> 10
            "medium" -> 20
            "hard" -> 30
            else -> 10
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
                if (isAnswering && !hasLeftGame) {
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
            finishWithResults()
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
            "easy" -> 30_000L
            "medium" -> 20_000L
            "hard" -> 10_000L
            else -> 30_000L
        }
        val progressBar = findViewById<ProgressBar>(R.id.time_progress)
        val questionText = findViewById<TextView>(R.id.tv_question)
        val grid = findViewById<GridLayout>(R.id.grid_answers)

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
        ).forEach { it.isEnabled = enabled }
    }

    private fun checkAnswer(selectedAnswer: String) {
        if (hasLeftGame) return
        isAnswering = false
        setAnswerEnabled(false)
        timerAnimator?.cancel()
        val correct = selectedAnswer == questions[currentQuestionIndex].correctAnswer

        // Add result for the current question
        gameResults.add(
            GameResultItem(
                question = questions[currentQuestionIndex].question,
                pickedAnswer = if (selectedAnswer == "__TIMEOUT__") "Timeout" else selectedAnswer,
                correctAnswer = questions[currentQuestionIndex].correctAnswer,
                wasCorrect = correct
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

            if (selectedAnswer == "__TIMEOUT__") {
                showResultCard(false, selectedAnswer, 0)
            } else if (correct) {
                XpManager.addXp(this, 5)
                showResultCard(true, selectedAnswer, 5)
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

        if (selectedAnswer == "__TIMEOUT__") {
            val lost = LivesManager.loseLife(this)
            updateLivesCount()
            if (lost && LivesManager.getLives(this) == 0) {
                finishWithResults()
            }
            if (!lost) {
                finishWithResults()
            }
        } else if (correct) {
            // No life lost, XP handled above
        } else {
            val lost = LivesManager.loseLife(this)
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

    private fun finishWithResults() {
        cleanupPending()
        if (quizCompleted) {
            // XP for quiz completion
            XpManager.addXp(this, 25)
            // Bonus for all correct
            val allCorrect = gameResults.all { it.wasCorrect }
            if (allCorrect) {
                XpManager.addXp(this, 20)
            }
        }
        val intent = Intent(this, FlashCardActivity::class.java)
        intent.putParcelableArrayListExtra("game_results", ArrayList(gameResults))
        intent.putExtra("game_finished", true)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun showResultCard(correct: Boolean, selectedAnswer: String, xpGained: Int = 0) {
        val resultCard = findViewById<FrameLayout>(R.id.result_card_overlay)
        val resultText = findViewById<TextView>(R.id.result_text)
        val resultSubtext = findViewById<TextView>(R.id.result_subtext)
        resultCard.visibility = View.VISIBLE
        if (selectedAnswer == "__TIMEOUT__") {
            resultText.text = "Time's Up"
            resultSubtext.text = "Lost 1 life"
        } else if (correct) {
            resultText.text = "Correct"
            resultSubtext.text = if (xpGained > 0) "+${xpGained}xp" else ""
        } else {
            resultText.text = "Wrong"
            resultSubtext.text = "Lost 1 life"
        }
    }

    private fun hideResultCard() {
        findViewById<FrameLayout>(R.id.result_card_overlay).visibility = View.GONE
    }

    private fun generateQuestions(category: String, count: Int): List<Question> {
        val elementFiles = assets.list("")?.filter { it.endsWith(".json") } ?: emptyList()
        val elements = elementFiles.flatMap { loadElementsFromAsset(it) }

        val questions = mutableListOf<Question>()
        val usedElements = mutableSetOf<String>()

        repeat(count) {
            val element = elements.filter { it.element !in usedElements }.randomOrNull() ?: elements.random()
            usedElements.add(element.element)

            val (questionText, correct, alternatives) = when (category) {
                "element_symbols" -> {
                    val question = "What is the symbol for ${element.element}?"
                    val correct = element.short
                    val wrongs = elements.filter { it.short != correct }.shuffled().take(3).map { it.short }
                    Triple(question, correct, (wrongs + correct).shuffled())
                }
                "element_classifications" -> {
                    val question = "What is the classification of ${element.element}?"
                    val correct = element.element_group
                    val wrongs = elements.filter { it.element_group != correct }.map { it.element_group }
                        .distinct().shuffled().take(3)
                    Triple(question, correct, (wrongs + correct).shuffled())
                }
                "atomic_mass" -> {
                    val question = "What is the atomic mass of ${element.element}?"
                    val correct = element.element_atomicmass
                    val wrongs = elements.filter { it.element_atomicmass != correct }.map { it.element_atomicmass }
                        .distinct().shuffled().take(3)
                    Triple(question, correct, (wrongs + correct).shuffled())
                }
                "chemical_reactions" -> {
                    val question = "What is the most common ion charge for ${element.element}?"
                    val correct = element.element_ion_charge
                    val wrongs = elements.filter { it.element_ion_charge != correct }.map { it.element_ion_charge }
                        .distinct().shuffled().take(3)
                    Triple(question, correct, (wrongs + correct).shuffled())
                }
                "mixed_questions" -> {
                    val cat = listOf("element_symbols", "element_classifications", "atomic_mass", "chemical_reactions").random()
                    val mixedQ = generateQuestions(cat, 1)[0]
                    Triple(mixedQ.question, mixedQ.correctAnswer, mixedQ.alternatives)
                }
                else -> {
                    val question = "What is the symbol for ${element.element}?"
                    val correct = element.short
                    val wrongs = elements.filter { it.short != correct }.shuffled().take(3).map { it.short }
                    Triple(question, correct, (wrongs + correct).shuffled())
                }
            }
            questions.add(Question(questionText, correct, alternatives))
        }
        return questions
    }

    data class ElementData(
        val element: String,
        val short: String,
        val element_group: String,
        val element_atomicmass: String,
        val element_ion_charge: String
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
                    element_group = obj.optString("element_group"),
                    element_atomicmass = obj.optString("element_atomicmass"),
                    element_ion_charge = obj.optString("element_ion_charge")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun updateLivesCount() {
        val lives = LivesManager.getLives(this)
        findViewById<TextView>(R.id.tv_lives_count).text = lives.toString()
        findViewById<TextView>(R.id.tv_lives).text = "Lives: $lives"
    }

    override fun onResume() {
        super.onResume()
        if (!hasLeftGame) {
            LivesManager.refillLivesIfNeeded(this)
            updateLivesCount()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_learn).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_learn).layoutParams = params

        val params2 = findViewById<TextView>(R.id.tv_question).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) +
                resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.tv_question).layoutParams = params2
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