package com.jlindemann.science.utils

import android.content.Context

/** Best result per exam test, so the flashcard screen can show progress and a pass mark. */
object ExamManager {

    private const val PREFS = "exam_prefs"

    /** Best percentage of correct answers, or -1 when the exam has never been finished. */
    fun getBestScorePercent(context: Context, examKey: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(examKey), -1)

    fun isPassed(context: Context, examKey: String): Boolean =
        getBestScorePercent(context, examKey) >= FlashcardCatalog.EXAM_PASS_PERCENT

    /** Records a finished exam. Only an improvement is stored, so a bad retake cannot undo a pass. */
    fun recordResult(context: Context, examKey: String, correct: Int, total: Int) {
        if (total <= 0) return
        val percent = (correct * 100) / total
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (percent > prefs.getInt(key(examKey), -1)) {
            prefs.edit().putInt(key(examKey), percent).apply()
        }
    }

    private fun key(examKey: String) = "best_score_$examKey"
}
