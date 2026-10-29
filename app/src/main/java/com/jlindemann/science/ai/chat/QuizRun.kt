package com.jlindemann.science.ai.chat

import com.jlindemann.science.ai.nlu.Lexicon
import com.jlindemann.science.ai.retrieval.TextMatching
import com.jlindemann.science.quiz.GeneratorContext
import com.jlindemann.science.quiz.QuestionGenerators
import com.jlindemann.science.quiz.QuizQuestion

/**
 * A run of multiple-choice questions started from chat (roadmap 2.5): "quiz me on the halogens",
 * "five isotope questions on uranium". The questions come from the Learning Games generators, drawn
 * from the elements the request names, and each right answer earns that game's XP.
 *
 * @property scopeKeys the elements the questions are about, or null for the whole table
 */
class QuizRunState(
    val questions: List<QuizQuestion>,
    val scopeKeys: Set<String>?
) {
    var index: Int = 0
    var correct: Int = 0
    var xp: Int = 0

    val current: QuizQuestion get() = questions[index]
    val finished: Boolean get() = index >= questions.size
}

/** What a run request asks for. */
data class QuizRequest(val count: Int, val categories: List<String>, val explicitCount: Boolean)

object QuizRun {

    const val DEFAULT_COUNT = 5
    const val MAX_COUNT = 10

    /**
     * A mix that works for any scope. Generators that cannot fill a question from the scope return
     * nothing and are skipped: one element has no "which of these is densest".
     */
    private val MIXED = listOf(
        "element_classifications", "periodic_period", "periodic_group", "valence_electrons",
        "superlative_highest", "superlative_lowest", "compare_pair", "isotope_neutrons"
    )

    private val ISOTOPES = listOf("isotope_neutrons", "isotope_stability", "isotope_half_life", "isotope_decay")

    private val SUPERLATIVES = listOf("superlative_highest", "superlative_lowest", "compare_pair")

    /** "molar mass" in each language. A formula is not scoped to elements, so this runs table-wide. */
    private val MOLAR_MASS_WORDS = listOf(
        "molar mass", "molmassa", "molare masse", "masse molaire", "masa molar", "massa molare",
        "massa molar", "molere massa", "molar na masa", "मोलर द्रव्यमान", "مولر ماس", "摩尔质量"
    )

    /** Ends a run early, said on its own or in a short reply: "stop", "ok stop the quiz". */
    private val STOP_WORDS = listOf(
        "stop", "quit", "end quiz", "enough", "sluta", "avbryt", "stopp", "aufhoren", "beenden",
        "arrete", "arreter", "parar", "basta", "fermati", "pare", "hou op", "tama na", "itigil",
        "बस", "रुको", "بس", "روکو", "停", "停止", "结束"
    )

    /** Number words up to ten, for "five isotope questions". Digits are read directly. */
    private val NUMBER_WORDS: Map<String, Int> = (listOf(
        "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "tre" to 3, "fyra" to 4, "fem" to 5, "sex" to 6, "sju" to 7, "atta" to 8, "nio" to 9, "tio" to 10,
        "drei" to 3, "vier" to 4, "funf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10,
        "trois" to 3, "quatre" to 4, "cinq" to 5, "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10,
        "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "quattro" to 4, "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10,
        "quatro" to 4, "sete" to 7, "oito" to 8, "dez" to 10,
        "drie" to 3, "vyf" to 5, "ses" to 6, "sewe" to 7, "agt" to 8, "nege" to 9, "tien" to 10,
        "tatlo" to 3, "apat" to 4, "lima" to 5, "anim" to 6, "pito" to 7, "walo" to 8, "siyam" to 9, "sampu" to 10,
        "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10,
        "تین" to 3, "چار" to 4, "پانچ" to 5, "چھ" to 6, "سات" to 7, "آٹھ" to 8, "نو" to 9, "دس" to 10
    ) + listOf("三" to 3, "四" to 4, "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10)
        // A bare 三 is also the 三 of 第三周期, the third period; a count is followed by a measure word.
        .flatMap { (han, n) -> listOf("${han}道" to n, "${han}个" to n) }).toMap()

    /** "period 3" and "group 17" name a scope, not a count. */
    private val POSITION_NUMBER = Regex(
        """(?:period|group|periode|period|grupp|gruppe|groupe|periodo|gruppo|grupo|groep|panahon|grupo|आवर्त|समूह|پیریڈ|گروپ|第)\s*\d{1,2}"""
    )
    private val DIGITS = Regex("""(?<!\d)(\d{1,2})(?!\d)""")

    /** What a quiz request asks for: how many questions, and of which kinds. */
    fun request(normalized: String): QuizRequest {
        val count = countOf(normalized)
        val categories = when {
            Lexicon.ISOTOPE_WORDS.any { normalized.contains(it) } -> ISOTOPES
            MOLAR_MASS_WORDS.any { normalized.contains(it) } -> listOf("molar_mass")
            Lexicon.MOST.any { normalized.contains(it) } || Lexicon.LEAST.any { normalized.contains(it) } -> SUPERLATIVES
            else -> MIXED
        }
        return QuizRequest((count ?: DEFAULT_COUNT).coerceIn(1, MAX_COUNT), categories, count != null)
    }

    /** Whether the request picked a kind of question rather than the default mix. */
    fun namesATopic(request: QuizRequest): Boolean = request.categories !== MIXED

    private fun countOf(normalized: String): Int? {
        val withoutPositions = POSITION_NUMBER.replace(normalized, " ")
        DIGITS.find(withoutPositions)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        return NUMBER_WORDS.entries.firstOrNull { (word, _) -> TextMatching.containsWord(normalized, word) }?.value
    }

    /**
     * Up to [request]'s count of questions, taking one from each kind in turn so a run mixes them,
     * and never the same question twice.
     */
    fun questions(ctx: GeneratorContext, request: QuizRequest): List<QuizQuestion> {
        val generators = request.categories.mapNotNull { QuestionGenerators.forCategory(it) }
            .shuffled(ctx.random)
            .toMutableList()
        val out = ArrayList<QuizQuestion>(request.count)
        val seen = HashSet<String>()
        while (out.size < request.count && generators.isNotEmpty()) {
            val iterator = generators.iterator()
            while (iterator.hasNext() && out.size < request.count) {
                val generator = iterator.next()
                val fresh = generator.generate(ctx, 3).firstOrNull { seen.add(it.question + "|" + it.correctAnswer) }
                if (fresh == null) iterator.remove() else out.add(fresh)
            }
        }
        return out
    }

    fun stops(normalized: String): Boolean =
        TextMatching.splitQueryTokens(normalized).size <= 4 &&
            STOP_WORDS.any { TextMatching.containsWord(normalized, it) }

    /**
     * The option a reply picks, or null when it picks none or more than one. A chip sends the option
     * itself; a typed reply may say it inside a sentence, "it's sodium".
     */
    fun choice(normalized: String, options: List<String>): String? {
        val folded = options.associateWith { TextMatching.normalizeForLookup(it) }
        folded.entries.firstOrNull { it.value == normalized }?.let { return it.key }
        val inside = folded.entries.filter { (_, option) ->
            option.isNotBlank() && TextMatching.containsWord(normalized, option)
        }
        return inside.singleOrNull()?.key
    }
}
