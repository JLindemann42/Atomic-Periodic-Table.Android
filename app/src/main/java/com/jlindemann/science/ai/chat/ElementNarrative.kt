package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.retrieval.TextMatching

/**
 * The three questions answered from an element's prose description rather than from a field: what
 * it is used for, what it does in the body, and where its name comes from.
 *
 * The corpus requires the engine to decline all three — they are in `must_defer.tsv` — because no
 * stored field holds the answer. Declining is only correct if something downstream picks them up,
 * and this is it.
 */
object ElementNarrative {

    /** Which of the three a question is asking. */
    enum class Topic { USAGE, BIOLOGICAL, ETYMOLOGY }

    private val USAGE_WORDS = listOf(
        "use", "uses", "used", "application", "applications", "where to find", "function",
        "utility", "industry", "medicine",
        "anvands", "anvandning", "anvandningar", "nytta", "funktion",
        "verwendung", "verwendungen", "nutzen", "industrie", "medizin",
        "utilisation", "utilisations", "usage", "usages", "medecine",
        "uso", "usos", "utilidad", "industria", "medicina",
        "usi", "impiego", "impieghi", "utilidade", "utilizacao", "utilizacoes",
        "उपयोग", "प्रयोग", "काम", "istimal", "استعمال", "استعمالات",
        "用途", "应用", "使用", "yongtu", "yingyong", "shiyong",
        "gebruik", "gebruike", "gamit", "paggamit", "mga gamit"
    )

    private val BIOLOGICAL_WORDS = listOf(
        "body", "blood", "health", "toxic", "poison", "biological", "medicine", "essential", "diet", "nutrition",
        "kropp", "halsa", "giftig", "biologisk", "korper", "blut", "gesundheit", "medizin",
        "corps", "sang", "sante", "toxique", "biologique", "medecine", "essentiel",
        "cuerpo", "sangre", "salud", "toxico", "biologico", "medicina", "esencial",
        "corpo", "sangue", "salute", "tossico", "essenziale",
        "saude", "essencial",
        "शरीर", "रक्त", "स्वास्थ्य", "जहरीला", "जैविक", "दवा",
        "身体", "血液", "健康", "毒", "生物", "医学", "shenti", "xueye", "jiankang", "du", "shengwu", "yixue",
        "jism", "khoon", "sehat", "zeher", "biologiese", "kalusugan", "katawan", "dugo"
    )

    private val ETYMOLOGY_WORDS = listOf(
        "name", "called", "named", "origin", "etymology", "greek", "latin", "word", "meaning",
        "namn", "ursprung", "betydelse", "herkunft", "etymologie", "griechisch", "latein", "wort", "bedeutung",
        "nom", "origine", "grec", "mot", "signification",
        "nombre", "origen", "etimologia", "griego", "palabra", "significado",
        "nome", "greco", "latino", "parola", "significato",
        "origem", "grego", "latim", "palavra",
        "नाम", "उत्पत्ति", "व्युत्पत्ति", "यूनानी", "लैटिन", "शब्द", "अर्थ",
        "名字", "叫", "起源", "语源", "希腊", "拉丁", "词", "意思",
        "mingzi", "jiao", "qiyuan", "yuyuan", "xila", "lading", "ci", "yisi",
        "naam", "kaha jata", "asl", "itimaloji", "pangalan", "pinagmulan", "kahulugan"
    )

    /** "What"/"how" words. Usage needs one, so "useful" alone is not a question. */
    private val ASKS_WHAT_OR_HOW = listOf(
        "what", "how", "vad", "hur", "was", "wie", "quel", "comment", "que", "como", "cosa", "come",
        "क्या", "कैसे", "什么", "怎么", "kya", "kaise", "wat", "ano", "paano"
    )

    /** "Why"/"where" words. Etymology needs one, or every mention of a name matches. */
    private val ASKS_WHY_OR_WHERE = listOf(
        "why", "how", "where", "hur", "varfor", "varifran", "warum", "wie", "wo", "pourquoi", "comment",
        "por que", "como", "perche", "porque", "onde",
        "क्यों", "कैसे", "为什么", "weishenme", "kyun", "kaise", "hoekom", "bakit", "paano"
    )

    private val USAGE_SENTENCE_WORDS = listOf("use", "industry", "medicine", "application", "important", "anvandning", "viktig")
    private val BIOLOGICAL_SENTENCE_WORDS = listOf("living", "organism", "role", "body", "blood", "health", "toxic", "essential")
    private val ETYMOLOGY_SENTENCE_WORDS = listOf("name", "greek", "latin", "word", "from", "namn", "grek", "ord")

    /**
     * English function words. Four or more make a sentence an English clause rather than a term,
     * which is how a non-English answer avoids quoting the English description.
     */
    private val ENGLISH_FUNCTION_WORDS = listOf(
        "the", "and", "which", "with", "has", "have", "is", "are", "was", "were", "been", "of",
        "from", "that", "its", "it", "only", "also", "most", "used", "found", "other", "than",
        "but", "such", "these", "those", "there", "their", "when", "while", "into", "about"
    )

    private const val ENGLISH_CLAUSE_THRESHOLD = 4

    /**
     * Which topic [normalizedQuery] asks about, or null.
     *
     * Biological needs no question word — "is zinc important for the body" has none and is in the
     * corpus. The other two do: without the gate, "gold is useful" and every sentence naming an
     * element would be claimed.
     */
    fun detect(normalizedQuery: String?): Topic? {
        if (normalizedQuery.isNullOrBlank()) return null
        val q = normalizedQuery
        if (has(q, BIOLOGICAL_WORDS)) return Topic.BIOLOGICAL
        if (has(q, ETYMOLOGY_WORDS) && mentions(q, ASKS_WHY_OR_WHERE)) return Topic.ETYMOLOGY
        // A heading reads as a request without a question word: "Uses of gold", "%1$s के उपयोग".
        // The app offers exactly that as a suggestion chip in all twelve languages.
        if (has(q, USAGE_WORDS) && (mentions(q, ASKS_WHAT_OR_HOW) || opensWith(q, USAGE_WORDS))) return Topic.USAGE
        return null
    }

    /**
     * Exact, word-boundary matching — deliberately not fuzzy. Edit-distance matching over a short,
     * multilingual vocabulary answered "what happens when sodium reacts with water" with a list of
     * uses for sodium.
     */
    private fun has(query: String, words: List<String>): Boolean = words.any { TextMatching.containsWord(query, it) }

    /**
     * Whether the query leads with one of [words] — the heading shape. Hindi and Urdu put the
     * possessor first, so the last token counts as well; Chinese writes no spaces, so the whole
     * query's start and end do too.
     */
    private fun opensWith(query: String, words: List<String>): Boolean {
        val tokens = TextMatching.splitQueryTokens(query).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        return words.any {
            tokens.first() == it || tokens.last() == it || query.startsWith(it) || query.endsWith(it)
        }
    }

    private fun mentions(query: String, words: List<String>): Boolean = words.any { query.contains(it) }

    /**
     * The answer, or null when there is no description to read.
     *
     * @param elementName already localized for display
     * @param language the chat's language, which decides whether English sentences count
     */
    fun answer(
        topic: Topic,
        description: String?,
        elementName: String,
        language: String,
        strings: StringProvider,
        discoveredBy: String? = null,
        year: String? = null
    ): String? {
        val sentences = inActiveLanguage(split(description), language, fallBackToEnglish = true)
        val wanted = when (topic) {
            Topic.USAGE -> USAGE_SENTENCE_WORDS
            Topic.BIOLOGICAL -> BIOLOGICAL_SENTENCE_WORDS
            Topic.ETYMOLOGY -> ETYMOLOGY_SENTENCE_WORDS
        }
        val matched = sentences.filter { s -> wanted.any { s.contains(it, ignoreCase = true) } }
        if (matched.isNotEmpty()) {
            val header = when (topic) {
                Topic.USAGE -> R.string.ai_usage_header
                Topic.BIOLOGICAL -> R.string.ai_bio_role_header
                Topic.ETYMOLOGY -> R.string.ai_etymology_header
            }
            return strings.get(header, elementName) + "\n\n" +
                    matched.joinToString(". ") { it.trim() }.trim() + "."
        }
        // Nothing in the prose. Say so rather than answering a different question.
        return when (topic) {
            Topic.USAGE -> strings.get(R.string.ai_no_usage_data, elementName)
            Topic.BIOLOGICAL -> strings.get(R.string.ai_no_bio_data, elementName).trimEnd().trimEnd(':')
            Topic.ETYMOLOGY -> strings.get(
                R.string.ai_no_etymology_data, elementName, known(discoveredBy, strings), known(year, strings)
            )
        }
    }

    /**
     * Whether one sentence of a description is about [topic] — for the overview, which sorts the
     * description into sections with the same vocabulary this answers questions with.
     */
    fun isAbout(topic: Topic, sentence: String): Boolean {
        val q = TextMatching.normalizeForLookup(sentence)
        return when (topic) {
            Topic.USAGE -> has(q, USAGE_WORDS)
            Topic.BIOLOGICAL -> has(q, BIOLOGICAL_WORDS)
            Topic.ETYMOLOGY -> has(q, ETYMOLOGY_WORDS)
        }
    }

    /**
     * The sentences of a language's description that were actually translated.
     *
     * An untranslated sentence in a language file is a verbatim copy of the English one, so it is
     * recognised by exact comparison rather than by counting English words: "In a pure form, it is
     * a bright, slightly reddish yellow, dense, soft, malleable, and ductile metal" has only three
     * and passed the word test. The word test still runs after, for English that was edited rather
     * than copied. Nothing falls back to English; for English itself the English text is returned.
     */
    fun translatedSentences(localized: String?, english: String?, language: String): List<String> {
        if (language.equals("en", ignoreCase = true)) return splitWhole(english)
        val copies = splitWhole(english).toHashSet()
        return splitWhole(localized).filter { it !in copies && !looksEnglish(it) }
    }

    /**
     * Sentences with their own punctuation kept, for text that is shown as written. Breaks only at a
     * terminator followed by a space, or after a full-width one — splitting at every full stop cut
     * "4.5 billion years" at "4." and never split Chinese, which ends sentences with "。".
     */
    private fun splitWhole(description: String?): List<String> =
        if (description.isNullOrBlank()) emptyList()
        else WHOLE_SENTENCE.split(description.trim()).map { it.trim() }.filter { it.isNotEmpty() }

    private val WHOLE_SENTENCE = Regex("""(?<=[.!?])\s+|(?<=[。！？])""")

    /** A value, or the shipped word for "unknown" — never a blank or a sentinel. */
    private fun known(value: String?, strings: StringProvider): String =
        if (value.isNullOrBlank() || value.trim() == "---") strings.get(R.string.unknown) else value.trim()

    private fun split(description: String?): List<String> =
        if (description.isNullOrBlank()) emptyList()
        else description.split('.', '!', '?').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Drop English sentences when the chat is not in English — but only if something is left,
     * because a half-translated description is still better than no answer.
     */
    private fun inActiveLanguage(sentences: List<String>, language: String, fallBackToEnglish: Boolean): List<String> {
        if (language.equals("en", ignoreCase = true)) return sentences
        val translated = sentences.filterNot(::looksEnglish)
        return if (translated.isNotEmpty() || !fallBackToEnglish) translated else sentences
    }

    private fun looksEnglish(sentence: String): Boolean {
        val lower = sentence.lowercase()
        var hits = 0
        for (word in ENGLISH_FUNCTION_WORDS) {
            if (TextMatching.containsWord(lower, word)) hits++
            if (hits >= ENGLISH_CLAUSE_THRESHOLD) return true
        }
        return false
    }
}
