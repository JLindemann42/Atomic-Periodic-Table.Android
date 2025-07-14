package com.jlindemann.science.activities.tools

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.util.LivesManager
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
        findViewById<ImageButton>(R.id.back_btn_learn).setOnClickListener { this.onBackPressed() }

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
                if (isAnswering) {
                    isAnswering = false
                    timerAnimator?.cancel()
                    val selectedAnswer = questions[currentQuestionIndex].alternatives[index]
                    checkAnswer(selectedAnswer)
                }
            }
        }
    }

    /**
     * Sets up timer, question, answers, progress bar, and resets input.
     */
    private fun setupQuestionUI() {
        isAnswering = true
        val timeLimit = when (difficulty) {
            "easy" -> 30_000L
            "medium" -> 20_000L
            "hard" -> 10_000L
            else -> 30_000L
        }
        val progressBar = findViewById<ProgressBar>(R.id.time_progress)
        progressBar.progress = 100
        timerAnimator?.cancel()
        timerAnimator = ValueAnimator.ofInt(100, 0).apply {
            duration = timeLimit
            addUpdateListener { va ->
                progressBar.progress = va.animatedValue as Int
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isAnswering) {
                        isAnswering = false
                        checkAnswer("__TIMEOUT__")
                    }
                }
            })
            start()
        }
        val q = questions[currentQuestionIndex]
        findViewById<TextView>(R.id.tv_question).text = q.question
        val answerIds = listOf(R.id.answer_text_1, R.id.answer_text_2, R.id.answer_text_3, R.id.answer_text_4)
        q.alternatives.forEachIndexed { i, alt ->
            findViewById<TextView>(answerIds[i]).text = alt
        }
        // Reset grid rotation for next question
        val grid = findViewById<GridLayout>(R.id.grid_answers)
        grid.rotationY = 0f
        grid.visibility = View.VISIBLE
    }

    /**
     * Handles answer tapped or timeout.
     * Animates effect, disables further input, shows flip animation, and moves to next after 2s.
     */
    private fun checkAnswer(selectedAnswer: String) {
        isAnswering = false
        timerAnimator?.cancel()
        val correct = selectedAnswer == questions[currentQuestionIndex].correctAnswer
        val effectColors = if (selectedAnswer == "__TIMEOUT__")
            listOf(Color.parseColor("#D32F2F"), Color.parseColor("#C62828"), Color.parseColor("#FFCDD2"))
        else if (correct)
            listOf(Color.parseColor("#43A047"), Color.parseColor("#388E3C"), Color.parseColor("#81C784"))
        else
            listOf(Color.parseColor("#D32F2F"), Color.parseColor("#C62828"), Color.parseColor("#FFCDD2"))

        val effectOverlay = findViewById<FrameLayout>(R.id.effect_overlay)
        effectOverlay.visibility = View.VISIBLE
        animatedEffectView.startAnimation(effectColors) {
            effectOverlay.visibility = View.GONE
        }

        // Show result card between flips
        showResultCard(correct, selectedAnswer)

        val grid = findViewById<GridLayout>(R.id.grid_answers)
        grid.animate().rotationY(90f).setDuration(300).withEndAction {
            grid.visibility = View.INVISIBLE
            Handler(Looper.getMainLooper()).postDelayed({
                hideResultCard()
                nextQuestionWithFlip(grid, correct, selectedAnswer)
            }, 2000)
        }.start()

        if (selectedAnswer == "__TIMEOUT__") {
            val lost = LivesManager.loseLife(this)
            updateLivesCount()
            if (lost && LivesManager.getLives(this) == 0) {
                finish()
            }
            if (!lost) {
                finish()
            }
        } else if (correct) {
        } else {
            val lost = LivesManager.loseLife(this)
            updateLivesCount()
            if (lost && LivesManager.getLives(this) == 0) {
                finish()
            }
            if (!lost) {
                finish()
            } else {
            }
        }
    }

    /**
     * Shows next question with flip-in animation, or finishes the quiz.
     */
    private fun nextQuestionWithFlip(grid: GridLayout, wasCorrect: Boolean, selectedAnswer: String) {
        currentQuestionIndex++
        if (currentQuestionIndex >= questions.size) {
            Toast.makeText(this, "Quiz complete!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            setupQuestionUI()
            grid.rotationY = -90f
            grid.visibility = View.VISIBLE
            grid.animate().rotationY(0f).setDuration(300).start()
        }
    }

    /**
     * Show result card overlay between questions.
     */
    private fun showResultCard(correct: Boolean, selectedAnswer: String) {
        val resultCard = findViewById<FrameLayout>(R.id.result_card_overlay)
        val resultText = findViewById<TextView>(R.id.result_text)
        val resultSubtext = findViewById<TextView>(R.id.result_subtext)
        resultCard.visibility = View.VISIBLE
        if (selectedAnswer == "__TIMEOUT__") {
            resultText.text = "Time's Up"
            resultSubtext.text = "Lost 1 life"
        } else if (correct) {
            resultText.text = "Correct"
            resultSubtext.text = ""
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
        LivesManager.refillLivesIfNeeded(this)
        updateLivesCount()
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
}