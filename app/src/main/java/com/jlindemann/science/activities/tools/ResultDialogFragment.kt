package com.jlindemann.science.activities.tools

import GameResultItem
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.jlindemann.science.R
import kotlin.math.roundToInt

class ResultDialogFragment : DialogFragment() {

    private var results: List<GameResultItem> = emptyList()
    private var totalQuestions: Int = 0
    private var gameFinished: Boolean = false
    private var difficulty: String = "easy"
    private var totalXp: Int = 0
    private var category: String = "element_symbols"

    companion object {
        fun newInstance(
            results: List<GameResultItem>,
            gameFinished: Boolean,
            totalQuestions: Int,
            difficulty: String,
            totalXp: Int = 0,
            category: String = "element_symbols"
        ): ResultDialogFragment {
            val fragment = ResultDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList("game_results", ArrayList(results))
            args.putInt("total_questions", totalQuestions)
            args.putBoolean("game_finished", gameFinished)
            args.putString("difficulty", difficulty)
            args.putInt("total_xp", totalXp)
            args.putString("category", category)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        results = arguments?.getParcelableArrayList<GameResultItem>("game_results") ?: emptyList()
        totalQuestions = arguments?.getInt("total_questions") ?: results.size
        gameFinished = arguments?.getBoolean("game_finished") ?: false
        difficulty = arguments?.getString("difficulty") ?: "easy"
        totalXp = arguments?.getInt("total_xp") ?: 0
        category = arguments?.getString("category") ?: "element_symbols"
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_game_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tv_popup_title).text = "Game Results"

        val correctAnswers = results.count { it.wasCorrect }
        view.findViewById<TextView>(R.id.tv_score_summary).text = "Score: $correctAnswers/$totalQuestions"

        val allAnswersCompleted = results.size == totalQuestions && results.all { it.pickedAnswer != null }

        var displayTotalXp = totalXp
        if (displayTotalXp == 0) {
            val xpMultiplier = getXpMultiplier(difficulty)
            val xpElements = results.filter { it.wasCorrect }.sumOf { (it.baseXp * xpMultiplier).roundToInt() }
            val finishedGame = allAnswersCompleted
            val allCorrect = finishedGame && correctAnswers == totalQuestions
            val xpGameWin = if (finishedGame) (25 * xpMultiplier).roundToInt() else 0
            val xpPerfect = if (allCorrect) (20 * xpMultiplier).roundToInt() else 0
            displayTotalXp = xpElements + xpGameWin + xpPerfect
        }
        showXpBreakdown(view, results, totalQuestions, allAnswersCompleted, difficulty, displayTotalXp)

        val resultsList = view.findViewById<LinearLayout>(R.id.results_list)
        resultsList.removeAllViews()
        results.forEachIndexed { idx, result ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams = params
                setPadding(0, 12, 0, 12)
            }
            val questionText = TextView(context).apply {
                text = "${idx + 1}. ${result.question}"
                textSize = 15f
            }
            val yourAnswer = TextView(context).apply {
                text = "Your answer: ${result.pickedAnswer ?: "-"}"
                textSize = 14f
                setTextColor(
                    if (result.wasCorrect) resources.getColor(R.color.green, null)
                    else resources.getColor(R.color.red, null)
                )
            }
            val correctAnswer = TextView(context).apply {
                text = "Correct answer: ${result.correctAnswer}"
                textSize = 14f
            }
            val perQXp = (result.baseXp * getXpMultiplier(difficulty)).roundToInt()
            val xpBreak = TextView(context).apply {
                text = if (result.wasCorrect) "XP for this question: +$perQXp" else ""
                textSize = 12f
            }
            val status = TextView(context).apply {
                text = if (result.wasCorrect) "✔" else "✘"
                textSize = 16f
                setTextColor(
                    if (result.wasCorrect) resources.getColor(R.color.green, null)
                    else resources.getColor(R.color.red, null)
                )
            }
            row.addView(questionText)
            row.addView(yourAnswer)
            row.addView(correctAnswer)
            if (result.wasCorrect) row.addView(xpBreak)
            row.addView(status)
            resultsList.addView(row)
        }

        view.findViewById<ImageButton>(R.id.btn_close_popup).setOnClickListener {
            dismiss()
        }
    }

    private fun getXpMultiplier(difficulty: String): Double {
        return when (difficulty) {
            "medium" -> 1.3
            "hard" -> 1.5
            else -> 1.0
        }
    }

    private fun getLivesLost(difficulty: String): Int {
        return when (difficulty) {
            "hard" -> 2
            else -> 1 // easy and medium
        }
    }

    private fun showXpBreakdown(
        view: View,
        results: List<GameResultItem>,
        totalQuestions: Int,
        allAnswersCompleted: Boolean,
        difficulty: String,
        totalXp: Int
    ) {
        val xpMultiplier = getXpMultiplier(difficulty)
        val correctAnswers = results.count { it.wasCorrect }
        val finishedGame = allAnswersCompleted
        val allCorrect = finishedGame && correctAnswers == totalQuestions

        view.findViewById<TextView>(R.id.tv_total_xp).text =
            "Total XP: $totalXp  (${difficulty.replaceFirstChar { it.uppercase() }} x${xpMultiplier})"

        val breakdownList = view.findViewById<LinearLayout>(R.id.xp_breakdown_list)
        breakdownList.removeAllViews()

        val xpElements = results.filter { it.wasCorrect }.sumOf { (it.baseXp * xpMultiplier).roundToInt() }
        val xpGameWin = if (finishedGame) (25 * xpMultiplier).roundToInt() else 0
        val xpPerfect = if (allCorrect) (20 * xpMultiplier).roundToInt() else 0

        val elementsRow = TextView(context).apply {
            text = "Questions Correct: +${xpElements}xp"
            textSize = 15f
        }
        breakdownList.addView(elementsRow)

        if (xpGameWin > 0) {
            val winRow = TextView(context).apply {
                text = "Finished Game: +${xpGameWin}xp"
                textSize = 15f
            }
            breakdownList.addView(winRow)
        }

        if (xpPerfect > 0) {
            val perfectRow = TextView(context).apply {
                text = "Perfect (All Correct): +${xpPerfect}xp"
                textSize = 15f
            }
            breakdownList.addView(perfectRow)
        }

        val livesLostPerWrong = getLivesLost(difficulty)
        val wrongOrTimeoutAnswers = results.count { !it.wasCorrect }
        val livesLostTotal = wrongOrTimeoutAnswers * livesLostPerWrong
        val livesRow = TextView(context).apply {
            text = "Lives lost: $livesLostTotal ($livesLostPerWrong per wrong/timeout answer)"
            textSize = 15f
        }
        breakdownList.addView(livesRow)
    }
}