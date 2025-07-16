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

    companion object {
        fun newInstance(results: List<GameResultItem>, gameFinished: Boolean, totalQuestions: Int, difficulty: String): ResultDialogFragment {
            val fragment = ResultDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList("game_results", ArrayList(results))
            args.putInt("total_questions", totalQuestions)
            args.putBoolean("game_finished", gameFinished)
            args.putString("difficulty", difficulty)
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

        // Set title
        view.findViewById<TextView>(R.id.tv_popup_title).text = "Game Results"

        // Score summary (always x/totalQuestions)
        val correctAnswers = results.count { it.wasCorrect }
        view.findViewById<TextView>(R.id.tv_score_summary).text = "Score: $correctAnswers/$totalQuestions"

        // Determine if all answers completed
        val allAnswersCompleted = results.size == totalQuestions && results.all { it.pickedAnswer != null }

        // XP breakdown
        showXpBreakdown(view, results, totalQuestions, allAnswersCompleted, difficulty)

        // List individual question results
        val resultsList = view.findViewById<LinearLayout>(R.id.results_list)
        resultsList.removeAllViews()
        results.forEachIndexed { idx, result ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams = params
                setPadding(0, 8, 0, 8)
            }
            val questionText = TextView(context).apply {
                text = "${idx + 1}. ${result.question}"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            }
            val yourAnswer = TextView(context).apply {
                text = "Your: ${result.pickedAnswer ?: "-"}"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val correct = TextView(context).apply {
                text = if (result.wasCorrect) "✔" else "✘"
                setTextColor(if (result.wasCorrect) 0xFF388E3C.toInt() else 0xFFD32F2F.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.5f)
                gravity = android.view.Gravity.END
            }
            row.addView(questionText)
            row.addView(yourAnswer)
            row.addView(correct)
            resultsList.addView(row)
        }

        // Close button
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

    private fun showXpBreakdown(view: View, results: List<GameResultItem>, totalQuestions: Int, allAnswersCompleted: Boolean, difficulty: String) {
        val xpMultiplier = getXpMultiplier(difficulty)
        val correctAnswers = results.count { it.wasCorrect }
        val finishedGame = allAnswersCompleted
        val allCorrect = finishedGame && correctAnswers == totalQuestions

        val xpElements = (correctAnswers * 5 * xpMultiplier).roundToInt()
        val xpGameWin = if (finishedGame) (25 * xpMultiplier).roundToInt() else 0
        val xpPerfect = if (allCorrect) (20 * xpMultiplier).roundToInt() else 0
        val totalXp = xpElements + xpGameWin + xpPerfect

        // Set total XP
        view.findViewById<TextView>(R.id.tv_total_xp).text = "Total XP: $totalXp  (${difficulty.capitalize()} x${xpMultiplier})"

        // Setup breakdown
        val breakdownList = view.findViewById<LinearLayout>(R.id.xp_breakdown_list)
        breakdownList.removeAllViews()

        val elementsRow = TextView(context).apply {
            text = "Questions Correct: +${xpElements}xp"
            textSize = 15f
        }
        breakdownList.addView(elementsRow)

        // Only show these if all answers completed
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

        // Show lives lost info per wrong/timeout answer
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