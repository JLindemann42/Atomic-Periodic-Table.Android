package com.jlindemann.science.ai.chat

import androidx.annotation.StringRes
import com.jlindemann.science.R
import com.jlindemann.science.ai.core.Entitlements
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldValue
import com.jlindemann.science.ai.data.LocalizedView
import com.jlindemann.science.ai.retrieval.TextMatching
import kotlin.random.Random

/**
 * What a rich overview may include, and how much of it.
 *
 * @property full "tell me everything about gold": every section, not a selection
 * @property random chooses the sections for an ordinary overview; null takes the first ones
 * @property entitlements what the user may see. A PRO field is left out rather than shown.
 */
data class OverviewOptions(
    val full: Boolean = false,
    val random: Random? = null,
    val entitlements: Entitlements = Entitlements.FULL
)

/**
 * What an element *is*, for a question that names one and asks nothing in particular: "gold",
 * "tell me about gold", "element 79" — and "tell me everything about gold".
 *
 * The basic facts, then a few sections drawn from a pool — where it is found, what it is used for,
 * safety, advanced atomic data, origin, biological role, compounds, notable isotopes — and every
 * section when the user asks for everything.
 *
 * Three things differ from the overview the old keyword router gave. Its safety and advanced
 * sections labelled their lines in English ("Health", "Config") in every language; these use the
 * translated field labels. Its "notable isotopes" were the first three slots, which for gold are
 * Au-170 to Au-172, gone in fractions of a second; these are the stable ones, or failing that the
 * longest-lived. And it showed electron affinity to everyone, though that is a PRO field; here it
 * follows the entitlement.
 *
 * Everything is read in the reader's language: prose from the language's own description, minus any
 * sentence the file left as a copy of the English, with no English fallback.
 */
object ElementOverview {

    /** How much of the description leads the overview. */
    private const val LEAD_SENTENCES = 2

    /**
     * Words that ask for the whole overview, with the native-script forms of the Hindi, Urdu and
     * Chinese entries: normalisation leaves those scripts as written, so "sab kuch" could never
     * match "सब कुछ".
     */
    private val FULL_WORDS = listOf(
        "all", "everything", "complete", "full", "detailed", "whole", "total",
        "allt", "hela", "komplett", "fullstandig", "detaljerad",
        "alles", "vollstandig", "detailliert",
        "tout", "complet", "detaille", "entier",
        "todo", "completo", "detallado", "entero",
        "tutto", "dettagliato",
        "lahat", "kumpleto", "detalyado",
        "volledig", "volledige",
        "sab", "sab kuch", "purn", "vistrit", "mukammal", "tafseeli",
        "suoyou", "quanbu", "xiangxi", "quanmian",
        "सब", "सब कुछ", "पूरा", "विस्तृत",
        "سب", "سب کچھ", "مکمل", "تفصیلی",
        "所有", "全部", "详细", "全面"
    )

    /** "Where is it found" vocabulary, matched within a sentence. */
    private val ABUNDANCE_WORDS = listOf(
        "find", "found", "abundance", "crust", "universe", "nature", "meteorite",
        "förekomst", "finns", "natur", "universum",
        "vorkommen", "existiert",
        "trouver", "abondance", "univers",
        "encontrar", "abundancia", "naturaleza", "universo",
        "abundância", "natureza",
        "trovare", "abbondanza", "natura",
        "matatagpuan", "kalikasan", "sansinukob",
        "gevind", "vorkoms", "natuur", "heelal"
    )

    private val NFPA_HEALTH = listOf(
        R.string.ai_nfpa_health_0, R.string.ai_nfpa_health_1, R.string.ai_nfpa_health_2,
        R.string.ai_nfpa_health_3, R.string.ai_nfpa_health_4
    )
    private val NFPA_FLAMMABLE = listOf(
        R.string.ai_nfpa_flammable_0, R.string.ai_nfpa_flammable_1, R.string.ai_nfpa_flammable_2,
        R.string.ai_nfpa_flammable_3, R.string.ai_nfpa_flammable_4
    )
    private val NFPA_INSTABILITY = listOf(
        R.string.ai_nfpa_instability_0, R.string.ai_nfpa_instability_1, R.string.ai_nfpa_instability_2,
        R.string.ai_nfpa_instability_3, R.string.ai_nfpa_instability_4
    )

    private val FORMULA = Regex("""\b[A-Z][a-z]?\d*[A-Z][a-z]?\d*\b""")

    /** Whether a word in the question asks for everything. */
    fun isFullWord(token: String): Boolean = token in FULL_WORDS

    /** Whether the question asks for the whole overview. */
    fun asksForEverything(normalizedQuery: String): Boolean =
        FULL_WORDS.any { TextMatching.containsWord(normalizedQuery, it) }

    /**
     * The overview, or null when the element has nothing to say.
     *
     * @param localized the language's own text for the element. The record is parsed once from
     *   English, which is right for every measured value and wrong for prose: reading the phase and
     *   the description off it gave every language "solid" and an English paragraph.
     */
    fun of(
        element: ElementRecord,
        displayName: String,
        language: String,
        strings: StringProvider,
        localized: LocalizedView? = null,
        options: OverviewOptions = OverviewOptions()
    ): String? {
        val key = element.key
        val facts = StringBuilder()
        fun fact(@StringRes labelRes: Int, value: String?) {
            if (value.isNullOrBlank() || value == "---") return
            facts.append("• **").append(label(strings, labelRes)).append("**: ").append(value.trim()).append('\n')
        }
        fact(R.string.atomic_number_label, element.atomicNumber.toString())
        fact(R.string.element_symbols, element.symbol)
        fact(R.string.atomic_mass_colon, show(element.value("atomic_mass")))
        fact(R.string.phase_stp_colon, localized?.text(key, "phase") ?: element.phase)
        // No type line: `element_type` is not a field any element has (porting notes, D53).
        if (facts.isEmpty()) return null

        val english = show(element.value("description"))
        val sentences = ElementNarrative.translatedSentences(localized?.text(key, "description") ?: english, english, language)

        val text = StringBuilder()
        text.append("### ").append(strings.get(R.string.ai_rich_overview_basic_facts, displayName)).append("\n\n")
        text.append(facts.toString().trimEnd())

        val lead = sentences.take(LEAD_SENTENCES).map { it.trim() }
        // Joined as written: each sentence keeps its own ending, "." or "。".
        if (lead.isNotEmpty()) text.append("\n\n").append(joinSentences(lead))

        // The lead already said these; a section repeating one of them reads as padding.
        val rest = sentences.drop(LEAD_SENTENCES)
        for ((heading, body) in choose(pool(element, displayName, strings, rest, options), options)) {
            text.append("\n\n### ").append(heading).append('\n').append(body)
        }

        // A pointer to what else to ask, which the ordinary overview leaves out by design.
        if (!options.full) text.append("\n\n💡 ").append(strings.get(R.string.ai_pro_tip_hint))
        return text.toString()
    }

    // ---- Sections ---------------------------------------------------------------------------

    /** Every section this element has something for, in reading order. */
    private fun pool(
        element: ElementRecord,
        displayName: String,
        strings: StringProvider,
        sentences: List<String>,
        options: OverviewOptions
    ): List<Pair<String, String>> {
        val pool = ArrayList<Pair<String, String>>()
        fun add(@StringRes headingRes: Int, body: String?) {
            if (!body.isNullOrBlank()) pool.add(strings.get(headingRes, displayName) to body.trimEnd())
        }
        add(R.string.ai_rich_overview_where_found, whereFound(element, strings, sentences))
        add(R.string.ai_rich_overview_applications,
            bullets(sentences.filter { ElementNarrative.isAbout(ElementNarrative.Topic.USAGE, it) }, if (options.full) 4 else 2))
        add(R.string.ai_rich_overview_safety, safety(element, strings))
        add(R.string.ai_rich_overview_advanced, advanced(element, strings, options))
        add(R.string.ai_rich_overview_etymology,
            bullets(sentences.filter { ElementNarrative.isAbout(ElementNarrative.Topic.ETYMOLOGY, it) }, 1))
        add(R.string.ai_rich_overview_biological,
            bullets(sentences.filter { ElementNarrative.isAbout(ElementNarrative.Topic.BIOLOGICAL, it) }, 1))
        add(R.string.ai_rich_overview_compounds, compounds(sentences, if (options.full) 10 else 5))
        add(R.string.ai_rich_overview_isotopes, isotopes(element, strings, if (options.full) 5 else 3))
        return pool
    }

    /**
     * All of them for a full overview; otherwise three to five, kept in the pool's order so two
     * overviews of the same element read alike even when they say different things.
     */
    private fun choose(pool: List<Pair<String, String>>, options: OverviewOptions): List<Pair<String, String>> {
        if (options.full || pool.size <= 3) return pool
        val random = options.random ?: return pool.take(4)
        val count = minOf(pool.size, random.nextInt(3, 6))
        val keep = pool.indices.shuffled(random).take(count).toHashSet()
        return pool.filterIndexed { i, _ -> i in keep }
    }

    private fun whereFound(element: ElementRecord, strings: StringProvider, sentences: List<String>): String {
        val body = StringBuilder()
        show(element.value("abundance_earth_crust"))?.takeIf { it.isNotEmpty() }?.let { crust ->
            body.append("• **").append(label(strings, R.string.abundance_earth_crust)).append("**: ").append(crust).append('\n')
        }
        // Whole words, as the other sections match. Matching within words let the Swedish and
        // German "natur" claim "naturally occurring isotopes" as a sentence about where uranium is.
        val sentence = sentences.firstOrNull { s ->
            val folded = TextMatching.normalizeForLookup(s)
            ABUNDANCE_WORDS.any { TextMatching.containsWord(folded, TextMatching.normalizeForLookup(it)) }
        }
        if (sentence != null) body.append("• ").append(sentence.trim())
        return body.toString()
    }

    /** The NFPA ratings, each with the translated label and the level's meaning. */
    private fun safety(element: ElementRecord, strings: StringProvider): String? {
        val nfpa = element.nfpa ?: return null
        val body = StringBuilder()
        fun rating(@StringRes labelRes: Int, value: Int?, levels: List<Int>) {
            val level = value?.takeIf { it in 0..4 } ?: return
            body.append("• **").append(capitalise(strings.get(labelRes))).append("**: ")
                .append(level).append("/4 — ").append(strings.get(levels[level])).append('\n')
        }
        rating(R.string.ai_field_nfpa_health, nfpa.health, NFPA_HEALTH)
        rating(R.string.ai_field_nfpa_flammability, nfpa.flammability, NFPA_FLAMMABLE)
        rating(R.string.ai_field_nfpa_instability, nfpa.instability, NFPA_INSTABILITY)
        return body.toString()
    }

    /** Electron configuration, and electron affinity where the user's tier allows it. */
    private fun advanced(element: ElementRecord, strings: StringProvider, options: OverviewOptions): String {
        val body = StringBuilder()
        show(element.value("electron_configuration"))?.takeIf { it.isNotEmpty() && it != "---" }?.let {
            body.append("• **").append(label(strings, R.string.electron_configuration_colon)).append("**: ").append(it).append('\n')
        }
        if (options.entitlements.allowsField("electron_affinity")) {
            show(element.value("electron_affinity"))?.takeIf { it.isNotEmpty() && it != "---" }?.let {
                body.append("• **").append(label(strings, R.string.electron_affinity_colon)).append("**: ").append(it).append('\n')
            }
        }
        return body.toString()
    }

    /** Chemical formulas named in the description. */
    private fun compounds(sentences: List<String>, take: Int): String? {
        val found = sentences
            .flatMap { s -> FORMULA.findAll(s).map { it.value }.toList() }
            .filter { it.length > 2 }
            .distinct()
            .take(take)
        return if (found.isEmpty()) null else "• " + found.joinToString(", ")
    }

    /** The isotopes worth naming: the stable ones, or for an element with none, the longest-lived. */
    private fun isotopes(element: ElementRecord, strings: StringProvider, take: Int): String? {
        val stable = element.isotopes.filter { it.stable }.take(take)
        val lines = if (stable.isNotEmpty()) {
            stable.map { strings.get(R.string.ai_isotope_stable, it.name) }
        } else {
            element.isotopes
                .filter { it.halfLifeSeconds != null }
                .sortedByDescending { it.halfLifeSeconds }
                .take(take)
                .map { strings.get(R.string.ai_isotope_halflife_only, it.name, it.halfLifeDisplay) }
        }
        return if (lines.isEmpty()) null else lines.joinToString("\n")
    }

    // ---- Helpers ----------------------------------------------------------------------------

    /** A space after a Latin full stop, none after a full-width one, as Chinese runs sentences together. */
    private fun joinSentences(sentences: List<String>): String {
        val joined = StringBuilder()
        for (sentence in sentences) {
            if (joined.isNotEmpty() && joined.last() !in "。！？") joined.append(' ')
            joined.append(sentence)
        }
        return joined.toString()
    }

    private fun bullets(sentences: List<String>, take: Int): String? {
        val picked = sentences.take(take).map { "• " + it.trim() }
        return if (picked.isEmpty()) null else picked.joinToString("\n")
    }

    /**
     * A shipped label without its separator. Both colons: Chinese writes a full-width one, which
     * survived an ASCII-only strip and printed "原子序数：:".
     */
    private fun label(strings: StringProvider, @StringRes labelRes: Int): String =
        strings.get(labelRes).replace(":", "").replace("：", "").trim()

    private fun capitalise(label: String): String = label.replaceFirstChar { it.uppercase() }

    /** A field value as text, whatever shape it is stored in. */
    private fun show(value: FieldValue): String? = when (value) {
        is FieldValue.Text -> value.raw
        is FieldValue.Enum -> value.localized.ifBlank { value.canonical }
        is FieldValue.Num -> value.quantity.display.ifBlank { value.quantity.value.toString() }
        else -> null
    }
}
