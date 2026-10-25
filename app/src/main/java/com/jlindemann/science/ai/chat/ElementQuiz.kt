package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldValue
import com.jlindemann.science.ai.retrieval.TextMatching
import kotlin.random.Random

/**
 * "Quiz me" — one question about an element, then a verdict on the answer.
 *
 * In `must_defer.tsv` in five languages, so the engine declines it and this answers it. Unlike every
 * other handler this one spans two turns: it asks, and the next message is the answer. The
 * outstanding question lives in [com.jlindemann.science.ai.core.DialogueState.pendingQuizAnswer].
 */
object ElementQuiz {

    private val QUIZ_WORDS = listOf(
        "quiz", "question", "test", "fragesport", "tavling", "ratsel", "examen",
        "प्रश्नोत्तरी", "测验", "ceyan", "sawal", "toets", "pagsusulit", "tanong",
        "preguntas", "pregunta", "juego", "domanda", "quesito", "interrogazione",
        "frage", "prufung", "testa mig", "interroger", "questionnaire", "concours",
        "questao", "desafio", "testar", "fraga", "सवाल", "امتحان", "vrae", "vraag", "toetsie",
        // The word each language's own "Start a quiz" chip uses. Four of the twelve had no entry
        // that could ever match it, so tapping the chip was declined in Spanish, Hindi, Urdu and
        // Afrikaans.
        "cuestionario", "क्विज़", "کوئز", "vasvra"
    )

    /** How many words a reply can have and still be read as an answer rather than a new question. */
    private const val MAX_ANSWER_WORDS = 4

    /** Whether this asks for a quiz. Substring, so "frågesport" and "questions" both match. */
    fun asks(normalizedQuery: String?): Boolean =
        !normalizedQuery.isNullOrBlank() && QUIZ_WORDS.any { normalizedQuery.contains(it) }

    /** Whether a reply should be read as the answer to the question already outstanding. */
    fun looksLikeAnAnswer(normalizedQuery: String?, pending: String?): Boolean =
        pending != null && !normalizedQuery.isNullOrBlank() &&
                TextMatching.splitQueryTokens(normalizedQuery).count { it.isNotBlank() } <= MAX_ANSWER_WORDS

    /**
     * Judge a reply against the outstanding question.
     *
     * @param guessedKey the element the reply named, if any
     */
    fun judge(
        normalizedQuery: String,
        pending: String,
        guessedKey: String?,
        pendingDisplayName: String,
        strings: StringProvider
    ): String {
        val correct = normalizedQuery.contains(pending, ignoreCase = true) ||
                guessedKey.equals(pending, ignoreCase = true)
        return strings.get(if (correct) R.string.ai_quiz_correct else R.string.ai_quiz_wrong, pendingDisplayName)
    }

    /** One question and the element it is about, or null when the data cannot supply one. */
    fun ask(all: List<ElementRecord>, random: Random, strings: StringProvider): Pair<String, ElementRecord>? {
        if (all.isEmpty()) return null

        // Three shapes. The history question needs a discoverer and a year, and roughly a fifth of
        // elements have neither, so the candidates are filtered rather than asked and abandoned.
        var shape = random.nextInt(3)
        if (shape == 2) {
            val withHistory = all.filter(::hasHistory)
            if (withHistory.isEmpty()) shape = 0
            else {
                val pick = withHistory[random.nextInt(withHistory.size)]
                return strings.get(R.string.ai_quiz_history, show(pick, "discovered_by")!!, show(pick, "year_discovered")!!) to pick
            }
        }
        val element = all[random.nextInt(all.size)]
        return when (shape) {
            1 -> strings.get(R.string.ai_quiz_atomic, element.atomicNumber.toString()) to element
            else -> strings.get(R.string.ai_quiz_start, element.symbol) to element
        }
    }

    private fun hasHistory(element: ElementRecord): Boolean =
        present(show(element, "discovered_by")) && present(show(element, "year_discovered"))

    private fun present(value: String?): Boolean = !value.isNullOrBlank() && value.trim() != "---"

    /**
     * Read by field id. The Windows port reads `element_discovered_name` and `element_year`, which
     * are the JSON keys rather than the ids, so its history question is never asked.
     */
    private fun show(element: ElementRecord, fieldId: String): String? = when (val v = element.value(fieldId)) {
        is FieldValue.Text -> v.raw
        is FieldValue.Num -> v.quantity.display.ifBlank { v.quantity.value.toLong().toString() }
        else -> null
    }
}
