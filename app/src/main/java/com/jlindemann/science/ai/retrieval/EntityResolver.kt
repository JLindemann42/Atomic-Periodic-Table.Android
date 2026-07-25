package com.jlindemann.science.ai.retrieval

import com.jlindemann.science.ai.data.ElementKey

/** A name the resolver knows about, mapped to the English element key it identifies. */
data class ElementAlias(
    val surface: String,
    val key: ElementKey,
    val language: String,
    val kind: Kind
) {
    enum class Kind { NAME, SYMBOL, NUMBER }
}

/** A resolved element mention, with how confident the match is. */
data class ElementMatch(val key: ElementKey, val score: Double, val matched: String)

/**
 * Finds which elements a query mentions, across all twelve languages.
 *
 * Two hazards drive the design:
 *
 *  1. **Two-letter symbols collide with ordinary words.** Swedish `är` normalises to `ar`, which
 *     is argon's symbol; English `in` is indium; Filipino `ng` and `sa` are common particles.
 *     The guard from the legacy agent is preserved verbatim, since its behaviour is asserted by
 *     the existing reflection tests.
 *  2. **Chinese element names are single characters that recur inside ordinary words.** 金 is
 *     gold, but it also opens 金属 ("metal"); 铁 is iron but appears throughout compounds. A
 *     single Han character therefore only counts as an element mention when it stands as the
 *     whole query or is not absorbed into a longer run of Han characters.
 */
class EntityResolver(
    /** Normalised surface form to alias. Built once from all available language tables. */
    private val aliases: Map<String, List<ElementAlias>>,
    private val activeLanguage: String = "en"
) {

    /** Symbols that are far more likely to be a scientist or unit name than an element. */
    private val symbolBlocklist = setOf(
        "poisson", "planck", "boltzmann", "celsius", "fahrenheit", "kelvin", "newton",
        "pascal", "faraday", "curie", "avogadro", "coulomb", "joule", "watt", "volt"
    )

    /**
     * Resolve every element mentioned in a query, best match first.
     *
     * @param limit maximum number of distinct elements to return
     */
    fun resolveAll(query: String, limit: Int = 4): List<ElementMatch> {
        val raw = query.lowercase()
        val normalized = TextMatching.normalizeForLookup(query)
        val words = TextMatching.splitQueryTokens(normalized)
        val found = LinkedHashMap<ElementKey, ElementMatch>()

        // Atomic number, but only when the query frames it as one.
        atomicNumberIn(raw)?.let { number ->
            aliases[number.toString()]?.firstOrNull { it.kind == ElementAlias.Kind.NUMBER }?.let {
                found[it.key] = ElementMatch(it.key, 1.0, number.toString())
            }
        }

        // Longest surface forms first so "carbon dioxide" cannot be shadowed by "carbon".
        for (alias in aliases.values.flatten().sortedByDescending { it.surface.length }) {
            if (found.size >= limit) break
            if (found.containsKey(alias.key)) continue
            if (!matches(alias, raw, normalized, words, query)) continue
            val score = scoreOf(alias)
            found[alias.key] = ElementMatch(alias.key, score, alias.surface)
        }

        if (found.isEmpty()) fuzzyMatch(words)?.let { found[it.key] = it }

        return found.values.sortedByDescending { it.score }.take(limit)
    }

    /** The single best element match, or null when the query names none. */
    fun resolve(query: String): ElementMatch? = resolveAll(query, limit = 1).firstOrNull()

    private fun matches(
        alias: ElementAlias,
        rawQuery: String,
        normalizedQuery: String,
        words: List<String>,
        originalQuery: String
    ): Boolean {
        val surface = alias.surface
        if (surface.isBlank()) return false
        if (surface in symbolBlocklist) return false

        if (alias.kind == ElementAlias.Kind.SYMBOL) {
            if (isCommonWordCollision(surface, normalizedQuery, words, activeLanguage)) return false
            // Single-letter symbols collide with almost anything once punctuation is split off:
            // "gold's cas number" yields a bare "s", which is sulfur, turning a plain lookup
            // into a two-element comparison. They only count in a short query or when the user
            // says they mean a symbol.
            if (surface.length == 1 && words.size > 2 &&
                !normalizedQuery.contains("symbol") && !normalizedQuery.contains("element")
            ) return false
            // A blocklisted word containing the symbol, e.g. "poisson" for S.
            if (symbolBlocklist.any { it.contains(surface) && normalizedQuery.contains(it) }) return false
        }

        if (Tokenizer.isUnspacedScript(surface)) {
            return matchesUnspaced(surface, originalQuery)
        }
        if (TextMatching.containsToken(rawQuery, normalizedQuery, surface)) return true
        return matchesInflected(surface, words)
    }

    /**
     * Match an element name carrying a grammatical ending.
     *
     * Several supported languages inflect nouns, so a word-boundary match alone misses the most
     * natural phrasing: Swedish "guldets densitet", German "des Goldes", Italian "dell'oro".
     * A short suffix on an otherwise exact name is accepted; names shorter than four characters
     * are excluded so symbols and short names cannot over-match.
     */
    private fun matchesInflected(surface: String, words: List<String>): Boolean {
        if (surface.length < 4) return false
        if (words.any { word ->
                word.length > surface.length &&
                        word.length - surface.length <= MAX_INFLECTION_SUFFIX &&
                        word.startsWith(surface)
            }
        ) return true

        // Indic and Arabic-script nouns inflect by changing the final letter rather than adding
        // to it: Urdu گold "سونا" becomes "سونے" in an oblique phrase. A single edit on a name of
        // four or more characters is almost certainly the same word. Restricted to non-Latin
        // scripts, where the suffix rule above cannot apply and where a near-miss like
        // "iron"/"iran" is not a risk.
        if (isLatin(surface)) return false
        return words.any { word ->
            kotlin.math.abs(word.length - surface.length) <= 1 &&
                    TextMatching.levenshtein(word, surface) <= 1
        }
    }

    private fun isLatin(text: String): Boolean = text.all {
        !it.isLetter() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN
    }

    /**
     * A Han surface form counts only when it is not merely part of a longer Han run, unless it
     * is the entire query. Without this, 金属 ("metal") would resolve to gold on every mention.
     */
    private fun matchesUnspaced(surface: String, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed == surface) return true
        var index = trimmed.indexOf(surface)
        while (index >= 0) {
            val before = trimmed.getOrNull(index - 1)
            val after = trimmed.getOrNull(index + surface.length)
            val absorbed = isCompounding(before) || isCompounding(after)
            // Multi-character names are specific enough to match even inside a longer run.
            if (!absorbed || surface.length > 1) return true
            index = trimmed.indexOf(surface, index + 1)
        }
        return false
    }

    /**
     * Whether a neighbouring character could make this part of a longer word.
     *
     * Grammatical particles do not form compounds, so they act as boundaries: 金的密度 is
     * "gold's density" and 金 is the element, whereas 金属 is "metal" and 金 is not. Without this
     * distinction the guard against the second case also blocks the first.
     */
    private fun isCompounding(c: Char?): Boolean =
        c != null && Tokenizer.isHan(c) && c !in HAN_PARTICLES

    private fun scoreOf(alias: ElementAlias): Double = when (alias.kind) {
        ElementAlias.Kind.NUMBER -> 1.0
        ElementAlias.Kind.NAME -> 0.9 + alias.surface.length.coerceAtMost(10) / 200.0
        ElementAlias.Kind.SYMBOL -> 0.7
    }

    /** Last resort for a short query that named nothing exactly — tolerate a typo. */
    private fun fuzzyMatch(words: List<String>): ElementMatch? {
        if (words.isEmpty() || words.size > 2) return null
        var best: ElementMatch? = null
        for (word in words) {
            if (word.length < 4) continue
            val threshold = if (word.length > 6) 2 else 1
            for (alias in aliases.values.flatten()) {
                if (alias.kind != ElementAlias.Kind.NAME) continue
                if (kotlin.math.abs(alias.surface.length - word.length) > threshold) continue
                val distance = TextMatching.levenshtein(word, alias.surface)
                if (distance > threshold) continue
                val score = 0.5 - distance * 0.1
                if (best == null || score > best!!.score) {
                    best = ElementMatch(alias.key, score, alias.surface)
                }
            }
        }
        return best
    }

    /** Read an atomic number out of a query that frames it as one. */
    private fun atomicNumberIn(query: String): Int? {
        val match = Regex("""\b(\d{1,3})\b""").find(query) ?: return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        if (number !in 1..118) return null
        val framed = listOf("element", "atomic number", "number", "grundämne", "atomnummer",
            "ordnungszahl", "número atómico", "numéro atomique", "numero atomico",
            "número atômico", "परमाणु", "جوہری", "原子序数", "atoomgetal", "atomikong")
            .any { query.contains(it) }
        val shortQuery = query.trim().split(Regex("\\s+")).size <= 3
        return if (framed || shortQuery) number else null
    }

    companion object {
        /** Longest grammatical ending accepted on an element name, e.g. Swedish "guld" + "ets". */
        private const val MAX_INFLECTION_SUFFIX = 3

        /** Chinese function characters that separate words rather than joining them. */
        private val HAN_PARTICLES = setOf(
            '的', '是', '有', '和', '與', '与', '在', '中', '了', '嗎', '吗', '呢', '把', '被',
            '為', '为', '之', '或', '及', '而', '就', '都', '也', '很', '多', '少', '個', '个'
        )

        /**
         * Whether a short token is an ordinary word in the active language rather than a symbol.
         *
         * Preserved verbatim from `AIAgentManager`; `AIAgentManagerTest` asserts these exact
         * cases through reflection, including the Swedish `är`->`ar` argon collision.
         */
        fun isCommonWordCollision(
            token: String,
            lowerQuery: String,
            queryWords: List<String>,
            language: String
        ): Boolean {
            if (token.length > 2) return false

            val isCommon = when (language) {
                "en" -> token in listOf("in", "as", "at", "be", "he", "am", "i", "no", "ar")
                "sv" -> token in listOf("ar", "i", "se", "ne", "na", "be", "es") // "ar" for "är"
                "de" -> token in listOf("as", "be", "er", "es", "in", "se", "am", "zu", "an", "um", "du", "so", "da")
                "fr" -> token in listOf("au", "as", "y", "la", "ca", "ce", "es", "en", "de", "le", "un", "et", "il", "se", "ne")
                "es" -> token in listOf("y", "la", "ca", "se", "no", "si", "as", "es", "de", "el", "en", "un", "al", "su", "lo", "ya")
                "it" -> token in listOf("i", "la", "se", "si", "ne", "in", "di", "il", "ed", "un", "ad", "al", "lo", "su", "ma")
                "hi", "ur" -> token in listOf("se", "ne", "na", "pa", "ka", "ki", "ke", "ko", "jo", "to", "ab", "vo", "hi")
                "pt" -> token in listOf("o", "as", "os", "se", "em", "um", "ou", "ao", "do", "da")
                "af" -> token in listOf("na", "te", "op", "in", "om", "by", "sy", "as", "hy", "is")
                "fil" -> token in listOf("ng", "sa", "na", "at", "ay", "ko", "mo", "ba", "ka", "pa")
                else -> false
            }

            if (isCommon) {
                if (queryWords.size > 1 && !lowerQuery.contains("element") && !lowerQuery.contains("symbol")) return true
            }
            return false
        }

        /** Build the alias table from every available language's element names and symbols. */
        fun buildAliases(
            tables: Map<String, Map<ElementKey, Triple<String, String, String>>>
        ): Map<String, List<ElementAlias>> {
            val aliases = HashMap<String, MutableList<ElementAlias>>()

            fun put(surface: String, alias: ElementAlias) {
                if (surface.isBlank()) return
                aliases.getOrPut(surface) { ArrayList(2) }
                    .let { list -> if (list.none { it.key == alias.key && it.kind == alias.kind }) list.add(alias) }
            }

            for ((language, rows) in tables) {
                for ((key, triple) in rows) {
                    val (name, symbol, atomicNumber) = triple
                    val normalizedName = TextMatching.normalizeForLookup(name)
                    put(normalizedName, ElementAlias(normalizedName, key, language, ElementAlias.Kind.NAME))
                    put(name.lowercase(), ElementAlias(name.lowercase(), key, language, ElementAlias.Kind.NAME))
                    if (symbol.isNotBlank()) {
                        put(symbol.lowercase(), ElementAlias(symbol.lowercase(), key, language, ElementAlias.Kind.SYMBOL))
                    }
                    if (atomicNumber.isNotBlank()) {
                        put(atomicNumber, ElementAlias(atomicNumber, key, language, ElementAlias.Kind.NUMBER))
                    }
                }
            }
            return aliases
        }
    }
}
