package com.jlindemann.science.ai.nlu

import java.text.Normalizer
import kotlin.math.ln

/**
 * Which language a message is written in, from its character trigrams (roadmap 1.4).
 *
 * Both platforms used to judge by element names alone, so a question naming no element carried no
 * signal: "hur många ädelgaser finns det" was answered in English. This is a naive Bayes model over
 * the trigrams of each word, trained on the app's own strings, the per-language lexicon and the
 * element descriptions, and shipped as counts in `shared/language/trigrams.tsv`, which the Windows
 * app reads too. `LanguageModelGoldenTest` builds it.
 *
 * Element names stay as the tie-breaker, and as the whole answer for a message too short to judge
 * by its letters: "guld" is Swedish because only Swedish calls gold that, and "gold" after a German
 * turn stays German because German calls it that too.
 *
 * @param counts trigram counts per language, a space written as `_`
 * @param totals every trigram the language was trained on, including those not kept in [counts]
 */
class LanguageDetector(
    private val counts: Map<String, Map<String, Int>>,
    private val totals: Map<String, Int>
) {

    /** Trigrams any language knows; the smoothing's vocabulary. */
    private val vocabulary: Set<String> = counts.values.flatMapTo(HashSet()) { it.keys }

    val languages: List<String> = counts.keys.sorted()

    private val denominators: Map<String, Double> =
        languages.associateWith { ln((totals[it] ?: 0).toDouble() + vocabulary.size) }

    /**
     * How well each language explains [message], 0 for the best, over the trigrams some language
     * knows. Empty when it has none: a message of digits, or of words no language was trained on.
     *
     * Each word votes, and no word decides: under every language a word scores its log-likelihood
     * less the best language's, floored at -[WORD_CLIP]. One long word whose letters happen to look
     * German - "fahrenheit", "-heit" - otherwise outvoted every short Spanish word around it, and
     * "y en fahrenheit" switched a Spanish thread to German.
     */
    fun scores(message: String): Map<String, Double> {
        val total = HashMap<String, Double>()
        var anyKnown = false
        for (word in words(message)) {
            val known = word.filter { it in vocabulary }
            if (known.isEmpty()) continue
            anyKnown = true
            val likelihood = languages.associateWith { lang ->
                val table = counts.getValue(lang)
                val denominator = denominators.getValue(lang)
                known.sumOf { ln((table[it] ?: 0) + 1.0) - denominator }
            }
            val best = likelihood.values.max()
            for ((lang, ll) in likelihood) total.merge(lang, maxOf(ll - best, -WORD_CLIP), Double::plus)
        }
        return if (anyKnown) total else emptyMap()
    }

    /**
     * The language to answer [message] in.
     *
     * A tie keeps the language in play, as it always has: switching on weak evidence is worse than
     * not switching, because a thread that changes language every third turn reads as broken even
     * when each guess was defensible. So a message of one word is judged by its element names, and a
     * longer one switches only when the best language beats [active] by [SWITCH_MARGIN].
     *
     * @param byElementNames the element-name detector, `EntityResolver.detectLanguage`
     */
    fun detect(message: String, active: String, byElementNames: (String, String) -> String): String {
        if (wordCount(message) < MIN_WORDS) return byElementNames(message, active)
        val scores = scores(message)
        if (scores.isEmpty()) return byElementNames(message, active)

        val ranked = scores.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
        var choice = ranked[0].key
        // Close languages can score within a hair of each other on a short question. A name only one
        // of them uses settles it: "wat is die digtheid van goud" is Afrikaans, not Dutch-ish German.
        if (ranked.size > 1 && ranked[0].value - ranked[1].value < TIE_MARGIN) {
            val named = byElementNames(message, active)
            if (named == ranked[1].key) choice = named
        }

        if (sameLanguage(choice, active)) return active
        val activeScore = scores[bare(active)] ?: return choice
        return if (scores.getValue(choice) - activeScore >= SWITCH_MARGIN) choice else active
    }

    companion object {
        /** Below this many words the letters are too few to judge by, and names decide. */
        const val MIN_WORDS = 2

        /** Nats within which the two best languages are a tie, broken by element names. */
        const val TIE_MARGIN = 1.0

        /**
         * Nats by which a language must beat the one in play to take over.
         *
         * This and [WORD_CLIP] were measured over the dev corpus, every row detected from its own
         * language and from English: from 12 to 14 here and 14 to 16 there, no row is switched away
         * from its language and every `language.tsv` row passes; these sit in the middle.
         */
        const val SWITCH_MARGIN = 13.0

        /** The most a language can lose on one word. */
        const val WORD_CLIP = 16.0

        /**
         * The trigrams of each word, lowercased and NFKC-normalised, padded with a space at each
         * end: "vad är" is ` va`, `vad`, `ad `, ` är`, `är `. Anything that is not a letter or a
         * combining mark separates words, so digits and punctuation carry no weight. Accents are kept:
         * "ä" is the strongest evidence of Swedish there is.
         *
         * Three exceptions. A chemical formula is no language's words: "H2O" would otherwise be the
         * words "h" and "o", and "o" is Portuguese. Nor is a unit name ([Lexicon.UNIT_WORDS]), which
         * every language borrows: "fahrenheit" has German letters and turned "300 K in fahrenheit"
         * German. And a Han character is a word of its own, since Chinese writes no spaces and a
         * whole sentence as one word has no trigram anyone has seen.
         */
        fun features(text: String): List<String> = words(text).flatten()

        /** [features], word by word. */
        fun words(text: String): List<List<String>> {
            val out = ArrayList<List<String>>()
            val word = StringBuilder()
            fun flush() {
                if (word.isEmpty()) return
                val padded = " $word "
                if (word.toString() !in UNITS) out.add((0..padded.length - 3).map { padded.substring(it, it + 3) })
                word.setLength(0)
            }
            for (c in withoutFormulas(Normalizer.normalize(text, Normalizer.Form.NFKC))) {
                when {
                    isHan(c) -> { flush(); word.append(c); flush() }
                    c.isLetter() || isMark(c) -> word.append(c.lowercaseChar())
                    else -> flush()
                }
            }
            flush()
            return out
        }

        /**
         * [text] with chemical formulas blanked: a run of element symbols, brackets and counts with a
         * digit or two capitals in it - "H2O", "Ca(OH)2", "NaCl", "CO". A lone capital stays, so
         * "I" and "Fe" are still words.
         */
        private fun withoutFormulas(text: String): String = FORMULA.replace(text) { m ->
            val v = m.value
            if (v.any(Char::isDigit) || v.count(Char::isUpperCase) >= 2) " ".repeat(v.length) else v
        }

        /** The unit names that are one word, which carry no evidence of a language. */
        private val UNITS: Set<String> = Lexicon.UNIT_WORDS.keys.filterTo(HashSet()) { ' ' !in it }

        /** `[0-9]`, not `\d`: .NET's `\d` is every Unicode digit, and Windows reads the same text. */
        private val FORMULA = Regex("""(?<![\p{L}\p{N}])(?:[A-Z][a-z]?[0-9]*|[()][0-9]*)+(?![\p{L}\p{N}])""")

        /** Words in [text], formulas aside; a Han character counts as one, as in [features]. */
        fun wordCount(text: String): Int {
            var words = 0
            var inWord = false
            for (c in withoutFormulas(Normalizer.normalize(text, Normalizer.Form.NFKC))) {
                when {
                    isHan(c) -> { words++; inWord = false }
                    c.isLetter() || isMark(c) -> { if (!inWord) words++; inWord = true }
                    else -> inWord = false
                }
            }
            return words
        }

        /**
         * Reads the model file: `lang`, trigram, count, with a space written `_` and the trigram `*`
         * holding the language's total. `#` lines and the header are skipped. Null when there is no
         * model, so the caller keeps element names alone.
         */
        fun parse(text: String?): LanguageDetector? {
            if (text.isNullOrBlank()) return null
            val counts = HashMap<String, HashMap<String, Int>>()
            val totals = HashMap<String, Int>()
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("lang\t")) continue
                val cells = line.trimEnd('\r').split('\t')
                if (cells.size < 3) continue
                val count = cells[2].toIntOrNull() ?: continue
                if (cells[1] == TOTAL) totals[cells[0]] = count
                else counts.getOrPut(cells[0]) { HashMap() }[cells[1].replace('_', ' ')] = count
            }
            return if (counts.isEmpty()) null else LanguageDetector(counts, totals)
        }

        /** The model file's name for a language's total. */
        const val TOTAL = "*"

        private fun isHan(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

        private fun isMark(c: Char): Boolean = when (Character.getType(c).toByte()) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
            else -> false
        }

        private fun bare(language: String): String = language.substringBefore('-')

        private fun sameLanguage(a: String, b: String): Boolean = bare(a).equals(bare(b), ignoreCase = true)
    }
}
