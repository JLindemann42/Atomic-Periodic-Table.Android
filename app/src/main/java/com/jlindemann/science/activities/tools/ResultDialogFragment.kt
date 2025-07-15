package com.jlindemann.science.activities.tools

import GameResultItem
import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.jlindemann.science.R

class ResultDialogFragment : DialogFragment() {

    private var results: List<GameResultItem> = emptyList()

    companion object {
        fun newInstance(results: List<GameResultItem>): ResultDialogFragment {
            val f = ResultDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList("results", ArrayList(results))
            f.arguments = args
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        results = arguments?.getParcelableArrayList("results") ?: emptyList()
        setStyle(STYLE_NO_FRAME, R.style.AppTheme_Dialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.dialog_game_results, container, false)

        root.setOnClickListener { dismiss() }

        val closeBtn = root.findViewById<ImageButton>(R.id.btn_close_popup)
        closeBtn.setOnClickListener { dismiss() }

        // New: set score summary
        val scoreText = root.findViewById<TextView>(R.id.tv_score_summary)
        val numCorrect = results.count { it.wasCorrect }
        scoreText.text = "Score: $numCorrect/${results.size} correct"

        val resultsLayout = root.findViewById<LinearLayout>(R.id.results_list)
        results.forEachIndexed { idx, item ->
            val entry = inflater.inflate(R.layout.item_result_entry, resultsLayout, false)
            entry.findViewById<TextView>(R.id.tv_question_number).text = "Q${idx + 1}"
            entry.findViewById<TextView>(R.id.tv_question_text).text = item.question
            entry.findViewById<TextView>(R.id.tv_your_answer).text =
                "Your answer: ${item.pickedAnswer}"
            entry.findViewById<TextView>(R.id.tv_correct_answer).text =
                "Correct answer: ${item.correctAnswer}"
            entry.findViewById<TextView>(R.id.tv_correct_label).apply {
                text = if (item.wasCorrect) "✔" else "✘"
                setTextColor(resources.getColor(if (item.wasCorrect) R.color.green else R.color.red, null))
            }
            resultsLayout.addView(entry)
        }

        return root
    }
}