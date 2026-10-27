package com.jlindemann.science.ai.retrieval

import com.jlindemann.science.ai.data.ElementKey
import com.jlindemann.science.ai.nlu.Lexicon

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
 * A compound named in words (roadmap 1.2).
 *
 * @property surface the words that named it, folded
 * @property formula its formula, or null when the name does not fix one - "iron oxide", since iron
 *   has more than one common charge
 * @property cation the cation as the solubility table writes it ("Na^+", "Ca^2+", "NH4^+"), or null
 *   for a molecule
 */
data class CompoundMatch(val surface: String, val formula: String?, val cation: String?)

/** The compounds a query names, and the query with their words blanked out. */
data class CompoundScan(val compounds: List<CompoundMatch>, val remainder: String)

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
    /** The language this resolver answers in; the planner reads its stopwords off it. */
    val activeLanguage: String = "en"
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
    /** Every element name of one word, folded, in any language, and its element: the speller's names (roadmap 1.6). */
    fun nameWords(): Map<String, ElementKey> =
        aliases.values.flatten()
            .filter { it.kind == ElementAlias.Kind.NAME && ' ' !in it.surface }
            .associate { it.surface to it.key }

    fun resolveAll(query: String, limit: Int = 4): List<ElementMatch> {
        // A compound claims its words first: "sodium chloride" is not sodium (roadmap 1.2).
        val scan = scanCompounds(query)
        if (scan.compounds.isNotEmpty()) return resolveElements(scan.remainder, limit)
        return resolveElements(query, limit)
    }

    private fun resolveElements(query: String, limit: Int): List<ElementMatch> {
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

    /**
     * An element number the query asks for that the periodic table does not have, or null.
     *
     * "Tell me about element 119" resolved nothing, fell through to lexical retrieval and came back
     * with the mass of an electron. The number is the whole point of the question, so the honest
     * response needs to know it was out of range rather than merely unmatched.
     */
    fun outOfRangeElementNumber(query: String): Int? {
        val match = Regex("""\b(\d{1,3})\b""").find(query) ?: return null
        if (match.range.first > 0 && query[match.range.first - 1] == '-') return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        if (number in 1..118) return null
        return if (ELEMENT_NUMBER_FRAMES.any { query.lowercase().contains(it) }) number else null
    }

    /** The single best element match, or null when the query names none. */
    fun resolve(query: String): ElementMatch? = resolveAll(query, limit = 1).firstOrNull()

    /**
     * The compounds a query names in words, and the query with their words blanked out
     * (roadmap 1.2).
     *
     * "Sodium chloride" used to resolve as sodium plus chlorine, so "density of sodium chloride"
     * was answered with sodium's density. A compound now claims its words before elements are
     * looked for, and [resolveAll] only sees what is left: "water's oxygen" is still oxygen, because
     * oxygen is outside the claimed words.
     *
     * Salts are not listed. They are composed from an element name in any language, an anion name
     * from [Lexicon.ANION_NAMES] and the shape each language writes them in:
     *
     *  - cation, anion: "sodium chloride", "सोडियम क्लोराइड"
     *  - anion, connector, cation: "cloruro de sodio", "chlorure d'argent", "solfato di rame"
     *  - one word: "natriumklorid", "Calciumcarbonat", "natriumchloried"
     *  - Han, anion prefix then the cation's character: 氯化钠, 硫酸铜
     *
     * with an optional Roman numeral for the charge, "iron(III) chloride". The formula follows from
     * the charges; a metal without one common charge ([Lexicon.CATION_CHARGES]) still claims its
     * words but has no formula. Molecules and household names ("carbon dioxide", "table salt") come
     * from [Lexicon.COMMON_COMPOUNDS].
     */
    fun scanCompounds(query: String): CompoundScan {
        lastScan?.let { if (it.first == query) return it.second }
        val scan = scan(query)
        lastScan = query to scan
        return scan
    }

    /** The planner asks about one query several times in a row; scanning it once is enough. */
    @Volatile private var lastScan: Pair<String, CompoundScan>? = null

    private fun scan(query: String): CompoundScan {
        val tokens = query.split(WHITESPACE).filter { it.isNotEmpty() }
        // Each whitespace word, folded and split the way every lexicon entry is, remembering which
        // word each piece came from so a claimed compound can be blanked in the original text.
        val words = ArrayList<String>()
        val origin = ArrayList<Int>()
        tokens.forEachIndexed { i, token ->
            TextMatching.splitQueryTokens(TextMatching.normalizeForLookup(token)).forEach {
                words.add(it); origin.add(i)
            }
        }
        val claimed = HashSet<Int>()
        val hanClaims = HashMap<Int, MutableList<String>>()
        val found = ArrayList<CompoundMatch>()

        fun claim(from: Int, to: Int) { for (k in from..to) claimed.add(k) }
        fun free(from: Int, to: Int) = from >= 0 && to < words.size && (from..to).none { it in claimed }

        // Salts first, so 硫酸铜 (copper sulfate) is not read as 硫酸 (sulfuric acid) plus copper.
        for (k in words.indices) {
            if (k in claimed) continue
            val word = words[k]
            Lexicon.ANION_NAMES[word]?.takeUnless { TextMatching.isHanText(word) }?.let { anion ->
                // cation, anion - with a Roman numeral between when the name gives the charge
                val roman = ROMAN[words.getOrNull(k - 1)]
                val before = if (roman != null) k - 2 else k - 1
                cationAt(words.getOrNull(before), roman)?.let { cation ->
                    if (free(before, k)) {
                        salt(word, cation, anion)?.let { found.add(it); claim(before, k) }
                    }
                }
                // anion, connector, cation
                if (k !in claimed && words.getOrNull(k + 1) in Lexicon.COMPOUND_CONNECTORS) {
                    val romanAfter = ROMAN[words.getOrNull(k + 3)]
                    cationAt(words.getOrNull(k + 2), romanAfter)?.let { cation ->
                        val end = if (romanAfter != null) k + 3 else k + 2
                        if (free(k, end)) salt(word, cation, anion)?.let { found.add(it); claim(k, end) }
                    }
                }
            }
            if (k in claimed) continue
            if (TextMatching.isHanText(word)) {
                hanSalts(word).forEach { (surface, match) ->
                    found.add(match); hanClaims.getOrPut(origin[k]) { ArrayList() }.add(surface)
                }
                continue
            }
            // one word: cation and anion written together, perhaps with an ending
            joinedSalt(word)?.let { found.add(it); claim(k, k) }
        }

        // Named molecules, longest first, in the words no salt claimed.
        for ((name, formula) in MOLECULES) {
            if (TextMatching.isHanText(name)) {
                for (k in words.indices) {
                    if (k in claimed) continue
                    val taken = hanClaims[origin[k]].orEmpty()
                    val rest = taken.fold(words[k]) { text, surface -> text.replace(surface, " ") }
                    if (rest.contains(name)) {
                        found.add(CompoundMatch(name, formula, null))
                        hanClaims.getOrPut(origin[k]) { ArrayList() }.add(name)
                    }
                }
                continue
            }
            val parts = name.split(' ')
            var k = 0
            while (k + parts.size <= words.size) {
                if (free(k, k + parts.size - 1) && words.subList(k, k + parts.size) == parts) {
                    found.add(CompoundMatch(name, formula, null)); claim(k, k + parts.size - 1)
                    k += parts.size
                } else k++
            }
        }

        if (found.isEmpty()) return CompoundScan(emptyList(), query)
        val blanked = claimed.map { origin[it] }.toSet()
        val remainder = tokens.mapIndexed { i, token ->
            when {
                i in blanked -> " "
                hanClaims[i] != null -> hanClaims.getValue(i).fold(token) { text, s -> text.replace(s, " ") }
                else -> token
            }
        }.joinToString(" ")
        return CompoundScan(found, remainder)
    }

    /** A cation named by a word: an element with a known or stated charge, or ammonium. */
    private fun cationAt(word: String?, statedCharge: Int?): Cation? {
        if (word == null) return null
        if (word in Lexicon.AMMONIUM_NAMES) return Cation("NH4", 1, "ammonium")
        val alias = aliases[word]?.firstOrNull { it.kind == ElementAlias.Kind.NAME } ?: return null
        val symbol = symbolOf(alias.key) ?: return null
        return Cation(symbol, statedCharge ?: Lexicon.CATION_CHARGES[alias.key]?.toIntOrNull(), alias.key)
    }

    private data class Cation(val formula: String, val charge: Int?, val key: String)

    /** A salt from a cation and an anion code such as "SO4^2-"; null for a pairing that is not one. */
    private fun salt(surface: String, cation: Cation, anion: String): CompoundMatch? {
        val (anionFormula, anionCharge) = ion(anion)
        // Hydrogen names an acid only with a single-atom anion: "hydrogen chloride" is HCl, but
        // "hydrogen carbonate" is the bicarbonate ion, not carbonic acid.
        if (cation.key == "hydrogen" && !isMonatomic(anionFormula)) return null
        val charge = cation.charge
        val formula = charge?.let { compose(cation.formula, it, anionFormula, anionCharge) }
        val code = when {
            charge == null -> null
            charge == 1 -> "${cation.formula}^+"
            else -> "${cation.formula}^$charge+"
        }
        return CompoundMatch(surface, formula, code)
    }

    /** "natriumklorid", "calciumcarbonat", "silverkloriden": a cation name and an anion in one word. */
    private fun joinedSalt(word: String): CompoundMatch? {
        for ((anionName, anion) in LATIN_ANIONS) {
            val at = word.indexOf(anionName, startIndex = 1)
            if (at <= 0 || word.length - at - anionName.length > MAX_JOINED_ENDING) continue
            val cation = cationAt(word.substring(0, at), null) ?: continue
            return salt(word, cation, anion)
        }
        return null
    }

    /** 氯化钠, 硫酸铜: an anion prefix followed by the character that names the cation. */
    private fun hanSalts(word: String): List<Pair<String, CompoundMatch>> {
        val out = ArrayList<Pair<String, CompoundMatch>>()
        var text = word
        for ((prefix, anion) in HAN_ANIONS) {
            var at = text.indexOf(prefix)
            while (at >= 0) {
                val next = text.getOrNull(at + prefix.length)?.toString()
                val cation = next?.let { c ->
                    if (c in Lexicon.AMMONIUM_NAMES) Cation("NH4", 1, "ammonium")
                    else aliases[c]?.firstOrNull { it.kind == ElementAlias.Kind.NAME && it.language == "zh" }
                        ?.let { alias -> symbolOf(alias.key)?.let { Cation(it, Lexicon.CATION_CHARGES[alias.key]?.toIntOrNull(), alias.key) } }
                }
                if (cation != null) {
                    val surface = prefix + next
                    salt(surface, cation, anion)?.let { out.add(surface to it) }
                    text = text.replaceFirst(surface, " ".repeat(surface.length))
                }
                at = text.indexOf(prefix, at + 1)
            }
        }
        return out
    }

    private fun symbolOf(key: ElementKey): String? = symbols[key]

    /** Element key to its symbol, properly capitalised, from the symbol aliases. */
    private val symbols: Map<ElementKey, String> by lazy {
        aliases.values.flatten()
            .filter { it.kind == ElementAlias.Kind.SYMBOL }
            .associate { it.key to it.surface.replaceFirstChar(Char::uppercaseChar) }
    }

    /**
     * Which language the user is writing in, judged by whose element names they used.
     *
     * The alias table already holds every shipped language's names, so the detector is a tally
     * over it: "vad är guldets densitet" contains a Swedish element name and nothing else
     * identifying, which is exactly the evidence available.
     *
     * A tie keeps the current language. Spurious switching is worse than not switching — most
     * element names are shared across several languages, and a thread that flips language every
     * third turn reads as broken even when each guess was defensible.
     *
     * @param active the language in play, returned unchanged when nothing is decisive
     */
    fun detectLanguage(query: String, active: String): String {
        val lower = query.lowercase()
        val normalized = TextMatching.normalizeForLookup(query)
        val scores = HashMap<String, Int>()

        for (word in TextMatching.splitQueryTokens(lower)) {
            val matches = aliases[TextMatching.normalizeForLookup(word)] ?: aliases[word] ?: continue
            score(matches, scores, active)
        }
        // Nothing matched token by token, so scan the whole query. The scripts that write no
        // spaces need this: 金的密度 is one token to a splitter and three characters to a
        // containment check.
        if (scores.isEmpty()) {
            for ((surface, matches) in aliases) {
                if (!TextMatching.containsToken(lower, normalized, surface)) continue
                score(matches, scores, active)
            }
        }
        if (scores.isEmpty()) return active

        val best = scores.values.max()
        val leaders = scores.filterValues { it == best }.keys.sorted()
        if (leaders.any { it.equals(active, ignoreCase = true) }) return active
        // English loses a tie it is only in by coincidence: the active language is not among the
        // leaders, so the user is not reading English either, and a name shared by English and one
        // other language is evidence for the other one. Sorted first, so the choice between two
        // equally distinctive leaders is stable rather than dependent on map order.
        return leaders.firstOrNull { it != "en" } ?: leaders.first()
    }

    /**
     * Credit a matched surface to the language that actually claims it.
     *
     * Only a surface claimed by exactly one language is evidence. "Gold" is the English, German and
     * Afrikaans name; scoring every claimant made "electron configuration of gold" answer in German,
     * because German tied with English and English was then discarded on purpose. Scoring
     * distinctiveness keeps "guld" working — Swedish alone claims it — and leaves a shared name
     * contributing nothing.
     *
     * A short name from a language other than [active] is no evidence either, by the same floor
     * [matches] applies: French "or" is gold, and every English "sodium or potassium" switched the
     * reply to French.
     */
    private fun score(matches: List<ElementAlias>, scores: MutableMap<String, Int>, active: String) {
        var only: String? = null
        for (alias in matches) {
            if (alias.kind != ElementAlias.Kind.NAME) continue
            // Not for scripts that write no spaces, as in [matches]: every Chinese element name is
            // one character, and 金 is the clearest evidence of Chinese there is.
            if (alias.language != active && alias.surface.length < MIN_FOREIGN_NAME &&
                !Tokenizer.isUnspacedScript(alias.surface)
            ) continue
            if (only == null) { only = alias.language; continue }
            // A second language claims the same spelling, so it distinguishes nothing.
            if (only != alias.language) return
        }
        val language = only ?: return
        scores[language] = (scores[language] ?: 0) + 1
    }

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

        // Atomic numbers are resolved only by [atomicNumberIn], which checks that the query frames
        // the number as an element number at all. Matching them here as well bypassed that check
        // completely: "if I have 12 grams of carbon-12, how many moles is that" resolved magnesium,
        // because 12 is magnesium's atomic number and a bare token match asks no further questions.
        if (alias.kind == ElementAlias.Kind.NUMBER) return false

        // A name that only appears inside a fixed term naming something else.
        if (alias.kind == ElementAlias.Kind.NAME &&
            NAME_EXEMPT_PHRASES.any { it.contains(surface) && normalizedQuery.contains(it) }
        ) return false

        if (alias.kind == ElementAlias.Kind.SYMBOL) {
            if (isCommonWordCollision(surface, normalizedQuery, words, activeLanguage)) return false
            // A short symbol must be *written* as a symbol.
            //
            // Once punctuation is split off, a one- or two-letter surface collides with ordinary
            // words in twelve languages at once: the "s" of "tungsten's" is sulfur, the French "or"
            // is gold inside an English sentence, and the Swedish "är" normalises to "ar", which is
            // argon. Each turned a plain lookup into a two-element comparison.
            //
            // Capitalisation is the signal that separates them, because it is a convention users
            // already follow — nobody writes the element as "fe" mid-sentence, and everybody writes
            // "W". Requiring it also *gains* a case the previous rule lost: "atomic mass of W" was
            // being missed entirely.
            //
            // The earlier rule keyed off the query mentioning "symbol" or "element", which was
            // exactly backwards — "what's tungsten's chemical symbol" contains both, so the escape
            // fired precisely where the collision was worst.
            if (surface.length <= MAX_CASE_SENSITIVE_SYMBOL && words.size > 2 &&
                !appearsAsSymbol(surface, originalQuery)
            ) return false
            // A blocklisted word containing the symbol, e.g. "poisson" for S.
            if (symbolBlocklist.any { it.contains(surface) && normalizedQuery.contains(it) }) return false
        }

        if (Tokenizer.isUnspacedScript(surface)) {
            return matchesUnspaced(surface, originalQuery)
        }
        val foreign = alias.language != activeLanguage && alias.language != BASE_LANGUAGE
        // A short name from a language the user is not writing in is a collision waiting to happen:
        // French "or" is gold and appears in every English disjunction, Italian "oro" and Spanish
        // "ne" are the same hazard. An exact match used to bypass the language guard below, so the
        // word "or" in an English question resolved gold and turned a lookup into a comparison.
        // Names only. A chemical symbol belongs to no language — "Au" is gold in all twelve — so
        // applying a foreign-name floor to symbols silently broke every "melting point of Fe".
        if (foreign && alias.kind == ElementAlias.Kind.NAME && surface.length < MIN_FOREIGN_NAME) return false
        if (TextMatching.containsToken(rawQuery, normalizedQuery, surface)) return true

        // Inflection tolerance is confined to the language being spoken (plus English, which
        // supplies the canonical keys). An exact name from any language still resolves — a Swede
        // writing "gold" is fine — but a *stem* from an unrelated language is how "silver chloride"
        // became two elements: German "Chlor" is within the suffix tolerance of English "chloride",
        // so a compound's name was read as a comparison of its constituents.
        if (foreign) return false
        return matchesInflected(surface, words)
    }

    /**
     * A name with the vowel of its final syllable elided, or null when it has no such syllable.
     *
     * Recognises only the consonant-vowel-consonant ending that actually syncopates ("kisel",
     * "kvicksilver", German "Wasser"), so names that simply end in a vowel are untouched.
     */
    private fun syncopated(surface: String): String? {
        if (surface.length < MIN_SYNCOPE_LENGTH) return null
        val vowelAt = surface.length - 2
        if (surface[vowelAt] !in SYNCOPE_VOWELS) return null
        if (surface.last() in SYNCOPE_VOWELS) return null
        if (surface[vowelAt - 1] in SYNCOPE_VOWELS) return null
        return surface.removeRange(vowelAt, vowelAt + 1)
    }

    /**
     * True when [surface] appears in [originalQuery] written the way a chemical symbol is written:
     * a capital first letter, lower-case remainder, standing as its own word.
     *
     * Case is read from the *original* query rather than the normalised one, which is the only place
     * it survives. A query shouted in capitals ("WHAT IS THE ATOMIC MASS OF W") would satisfy this
     * for every two-letter word in it, so an all-caps query is treated as carrying no case
     * information at all and the symbol is declined.
     */
    private fun appearsAsSymbol(surface: String, originalQuery: String): Boolean {
        if (originalQuery.none { it.isLowerCase() }) return false
        val expected = surface.replaceFirstChar { it.uppercaseChar() }
        var from = 0
        while (true) {
            val at = originalQuery.indexOf(expected, from)
            if (at < 0) return false
            val before = originalQuery.getOrNull(at - 1)
            val after = originalQuery.getOrNull(at + expected.length)
            if ((before == null || !before.isLetterOrDigit()) &&
                (after == null || !after.isLetterOrDigit())
            ) return true
            from = at + 1
        }
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
        // Three characters, not four: Swedish inflects its shortest element names heavily — "bly"
        // becomes "blyets" in "vad är blyets smältpunkt", which a four-character floor excluded.
        // Two-character names stay out, because those are where symbol collisions live.
        if (surface.length < MIN_INFLECTABLE_NAME) return false
        if (words.any { word ->
                word.length > surface.length &&
                        word.length - surface.length <= MAX_INFLECTION_SUFFIX &&
                        word.startsWith(surface)
            }
        ) return true

        // Syncope: Swedish and German drop the unstressed vowel of a final syllable before an
        // ending, so "kisel" becomes "kislets" and "kvicksilver" becomes "kvicksilvrets". The stem
        // the user wrote is not a prefix of the name, which is why plain suffix tolerance missed
        // these entirely and the whole question was declined.
        // A Germanic compound whose *first* element is the element name: Swedish "uranatom",
        // German "Wasserstoffatom", "kolatomen". The suffix tolerance above allows three
        // characters, and "atom" is four, so "vad väger en uranatom" resolved no element at all.
        // Only these heads qualify — a general compound rule would match far too much.
        if (words.any { word ->
                COMPOUND_HEADS.any { head -> word == surface + head }
            }
        ) return true

        syncopated(surface)?.let { shortened ->
            if (words.any { word ->
                    word.length > shortened.length &&
                            word.length - shortened.length <= MAX_INFLECTION_SUFFIX &&
                            word.startsWith(shortened)
                }
            ) return true
        }

        // Indic and Arabic-script nouns inflect by changing the final letter rather than adding
        // to it: Urdu gold "سونا" becomes "سونے" in an oblique phrase. A single edit on a name of
        // four or more characters is almost certainly the same word. Restricted to non-Latin
        // scripts, where the suffix rule above cannot apply and where a near-miss like
        // "iron"/"iran" is not a risk.
        //
        // The edit has to fall at the *end*, though, or the rule stops modelling inflection and
        // starts modelling the periodic table's own naming: Hindi सोडियम (sodium) and रोडियम
        // (rhodium) are one substitution apart, as are Urdu سوڈیم/روڈیم and ہیلیم/گیلیم — and each
        // pair differs in its first character, which no inflection ever does. Every Hindi and Urdu
        // question about sodium resolved rhodium alongside it and was answered as a comparison.
        if (isLatin(surface)) return false
        return words.any { word ->
            kotlin.math.abs(word.length - surface.length) <= 1 &&
                    TextMatching.levenshtein(word, surface) <= 1 &&
                    sharesStem(word, surface)
        }
    }

    /**
     * Whether two forms agree on everything but their last character.
     *
     * The guard on the single-edit rule above: an inflectional ending changes the tail, so the stem
     * must survive intact.
     */
    private fun sharesStem(word: String, surface: String): Boolean {
        val stem = minOf(word.length, surface.length) - 1
        if (stem < MIN_NON_LATIN_STEM) return false
        return word.regionMatches(0, surface, 0, stem)
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
        c != null && Tokenizer.isHan(c) && c !in HAN_PARTICLES && c !in singleHanElements

    /**
     * The Han characters that are themselves a whole element name.
     *
     * They act as boundaries for the same reason the particles do: 金银 is "gold and silver", not a
     * compound word, and treating 银 as compounding meant 比较金银和铜 resolved copper alone — a
     * three-way comparison answered about one element. 金属 ("metal") is unaffected, because 属 is
     * not an element.
     */
    private val singleHanElements: Set<Char> by lazy {
        aliases.entries
            .filter { (surface, list) ->
                surface.length == 1 && Tokenizer.isHan(surface[0]) &&
                        list.any { it.kind == ElementAlias.Kind.NAME }
            }
            .map { it.key[0] }
            .toSet()
    }

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
            // A continuation is not a typo. "More" is one edit from the French name for boron,
            // "bore", and "sure" is one from the Swedish for oxygen, "syre" — so a bare "more"
            // after a question about iron answered with boron's melting point and moved the
            // conversation to boron for good. An exact "bore" still resolves; it never reaches
            // this last resort. Windows porting notes, D47.
            if (FuzzyMatch.isAffirmative(word)) continue
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
        // A quantity of moles is not a place in the table. "2 摩尔碳中有多少个原子" and "2 मोल
        // कार्बन में कितने परमाणु हैं" both resolved helium, because 2 is helium's atomic number and
        // the Hindi word for "atom" is one of the frames that says a bare number *is* one.
        if (MOLE_FRAMES.any { query.contains(it) }) return null
        val match = Regex("""\b(\d{1,3})\b""").find(query) ?: return null
        // A mass number is not an atomic number. "Is iron-54 stable" is a question about an isotope
        // of iron; reading the 54 as an element number answered it about xenon, and "yes" at that.
        if (match.range.first > 0 && query[match.range.first - 1] == '-') return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        if (number !in 1..118) return null
        val framed = ELEMENT_NUMBER_FRAMES.any { query.contains(it) }
        val shortQuery = query.trim().split(Regex("\\s+")).size <= 3
        return if (framed || shortQuery) number else null
    }

    companion object {
        private val WHITESPACE = Regex("""\s+""")

        /** A charge written into a salt's name: "iron(III) chloride", "cloruro de hierro (III)". */
        private val ROMAN = mapOf("ii" to 2, "iii" to 3, "iv" to 4)

        /** Longest ending accepted after an anion written into one word, Swedish "kloriden". */
        private const val MAX_JOINED_ENDING = 3

        /** Latin-script anion names that can end a one-word salt, longest first. */
        private val LATIN_ANIONS: List<Pair<String, String>> = Lexicon.ANION_NAMES.entries
            .filter { (name, _) -> name.length >= 4 && name.all { it in 'a'..'z' } }
            .map { it.key to it.value }
            .sortedByDescending { it.first.length }

        /** Han anion prefixes, longest first so 氢氧化 (hydroxide) is tried before 氧化 (oxide). */
        private val HAN_ANIONS: List<Pair<String, String>> = Lexicon.ANION_NAMES.entries
            .filter { TextMatching.isHanText(it.key) }
            .map { it.key to it.value }
            .sortedByDescending { it.first.length }

        /** Named molecules, longest first so "carbon dioxide" is not read as something shorter. */
        private val MOLECULES: List<Pair<String, String>> = Lexicon.COMMON_COMPOUNDS.entries
            .map { it.key to it.value }
            .sortedByDescending { it.first.length }

        /** "SO4^2-" to ("SO4", 2). */
        private fun ion(code: String): Pair<String, Int> {
            val formula = code.substringBefore('^')
            val charge = code.substringAfter('^').filter(Char::isDigit).toIntOrNull() ?: 1
            return formula to charge
        }

        /** One element and no count: "Cl", "O" - which never need brackets. */
        private fun isMonatomic(formula: String): Boolean =
            formula.count(Char::isUpperCase) == 1 && formula.none(Char::isDigit)

        /**
         * The neutral formula of a cation and an anion: as many of each as balances the charges,
         * with a polyatomic ion bracketed when there is more than one of it - Ca(OH)2, Al2(SO4)3,
         * (NH4)2SO4.
         */
        internal fun compose(cation: String, cationCharge: Int, anion: String, anionCharge: Int): String {
            val divisor = gcd(cationCharge, anionCharge)
            fun part(formula: String, count: Int) = when {
                count == 1 -> formula
                isMonatomic(formula) -> "$formula$count"
                else -> "($formula)$count"
            }
            return part(cation, anionCharge / divisor) + part(anion, cationCharge / divisor)
        }

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

        /**
         * Everyday names the shipped tables miss, by element key and language.
         *
         * Deliberately small: only where the shipped name is a transliteration of the English word
         * and the language has a common word of its own. Everything else comes from the assets.
         */
        private val NATIVE_SYNONYMS: Map<ElementKey, Map<String, String>> = mapOf(
            "iron" to mapOf("ur" to "لوہا"),
            "arsenic" to mapOf("ur" to "سنکھیا", "hi" to "संखिया"),
            "copper" to mapOf("ur" to "تانبا", "hi" to "तांबा"),
            "sulfur" to mapOf("ur" to "گندھک", "hi" to "गंधक"),
            "mercury" to mapOf("ur" to "پارہ", "hi" to "पारा"),
            "zinc" to mapOf("ur" to "جست", "hi" to "जस्ता"),
            "silver" to mapOf("hi" to "रजत"),
            "gold" to mapOf("hi" to "स्वर्ण")
        )

        /** Wordings that make a bare number a quantity of substance rather than an element number. */
        private val MOLE_FRAMES = listOf(
            "mole", "moles", "moli", "mols", "mol ", " mol", "avogadro",
            "मोल", "مول", "摩尔"
        )

        /** Wordings that frame a bare number as an element's place in the table. */
        private val ELEMENT_NUMBER_FRAMES = listOf(
            "element", "atomic number", "number", "grundämne", "atomnummer",
            "ordnungszahl", "número atómico", "numéro atomique", "numero atomico",
            "número atômico", "परमाणु", "جوہری", "原子序数", "atoomgetal", "atomikong"
        )

        /** Longest grammatical ending accepted on an element name, e.g. Swedish "guld" + "ets". */
        private const val MAX_INFLECTION_SUFFIX = 3

        /** Chinese function characters that separate words rather than joining them. */
        private val HAN_PARTICLES = setOf(
            '的', '是', '有', '和', '與', '与', '在', '中', '了', '嗎', '吗', '呢', '把', '被',
            '為', '为', '之', '或', '及', '而', '就', '都', '也', '很', '多', '少', '個', '个',
            // Question vocabulary. Chinese writes no spaces, so an element character sitting next
            // to one of these is inside a "longer run of Han characters" by the rule above and was
            // silently dropped: 比较金和银 resolved silver but not gold, and 砷危险吗 resolved
            // nothing at all. These characters never begin an element name, so admitting them as
            // boundaries costs nothing and makes the element stand free.
            '比', '較', '较', '危', '險', '险', '更', '最', '種', '种', '元', '素',
            '熔', '點', '点', '密', '度', '沸', '重', '輕', '轻', '致', '些', '哪', '幾', '几',
            '以', '上', '下', '大', '小', '於', '于', '同', '位', '放', '射', '性', '質', '质'
        )

        /**
         * Whether a short token is an ordinary word in the active language rather than a symbol.
         *
         * Preserved verbatim from the old keyword router; `EntityResolverTest` asserts these exact
         * cases, including the Swedish `är`->`ar` argon collision.
         */
        /** Shortest element name that may carry a grammatical ending. */
        private const val MIN_INFLECTABLE_NAME = 3

        /** Shortest name accepted from a language other than the active one or English. */
        private const val MIN_FOREIGN_NAME = 3

        /** Shortest stem that has to survive an inflectional edit in a non-Latin script. */
        private const val MIN_NON_LATIN_STEM = 3

        /**
         * Nouns a Germanic language compounds directly onto an element name.
         *
         * "Uranatom", "kolatomen", "Wasserstoffkern" — the element is the modifier and one of these
         * is the head. Written out rather than derived, because any rule permissive enough to find
         * them by shape would also find "goldfish".
         */
        private val COMPOUND_HEADS = listOf(
            "atom", "atomen", "atomer", "atomet", "atoms", "atome", "atomes",
            "karna", "karnan", "karnor", "kern", "kerne", "kerns",
            "gas", "gasen", "gaser", "molekyl", "molekyler", "molekul", "molekule"
        )

        /**
         * Phrases that contain an element's name without naming the element.
         *
         * Spanish calls radium *radio* and the atomic radius *radio atómico*, so every Spanish
         * question about a radius resolved two elements and became a comparison. The same shape as
         * the [symbolBlocklist] check, and deliberately as short: a phrase earns a place here only
         * when the collision is total, not merely possible.
         */
        val NAME_EXEMPT_PHRASES = listOf(
            "radio atomico", "radio ionico", "radio covalente", "radio de van der waals",
            "radio nuclear", "radio metalico"
        )

        /** Shortest name worth trying to syncopate; below this the result is not a stem. */
        private const val MIN_SYNCOPE_LENGTH = 5

        private const val SYNCOPE_VOWELS = "aeiouyaaeoau"

        /**
         * Symbols up to this length must be capitalised to count in a sentence.
         *
         * Two, because every collision observed is one or two characters — "s", "or", "ar", "in",
         * "no". At three the surfaces stop looking like ordinary words in the supported languages.
         */
        private const val MAX_CASE_SENSITIVE_SYMBOL = 2

        /** The language whose names are the canonical keys, always eligible for inflection. */
        private const val BASE_LANGUAGE = "en"

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
                // The "element"/"symbol" escape exists so "what element is Es" still resolves. It
                // has to be narrow: "wie viele Elemente gibt es" contains "element", which disabled
                // the guard and let German "es" resolve to einsteinium — turning a count of the
                // whole table into a question about one element. A genuine "which element is X"
                // is short; a sentence that merely mentions elements is not.
                // Only "symbol" counts. "What element has the symbol In" is someone naming a
                // symbol; "wie viele Elemente gibt es" merely mentions elements, and letting that
                // through disabled the guard so German "es" resolved to einsteinium — a count of
                // the whole table became a question about one element. Length cannot separate the
                // two: both are five words.
                if (queryWords.size > 1 && !lowerQuery.contains("symbol")) return true
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
                    .let { list ->
                        // Deduped by language as well as by element and kind. Without it, the first
                        // language to claim a shared spelling was the only one that kept it:
                        // German is indexed before Swedish, so "Uran" was registered as a *German*
                        // name and a Swedish query got the foreign-name path, which forbids
                        // inflection. "Vad väger en uranatom" therefore resolved no element at all
                        // — and so did every other question about a name the two share, which is
                        // most of them.
                        if (list.none {
                                it.key == alias.key && it.kind == alias.kind &&
                                        it.language == alias.language
                            }
                        ) list.add(alias)
                    }
            }

            // Names a speaker actually writes, where the shipped table gives a transliteration.
            // Urdu ships "آئرن" for iron and "آرسینک" for arsenic — English words in Urdu script —
            // so a user writing لوہا or سنکھیا resolved nothing. Registered under the same language
            // so the inflection tolerance applies to them too.
            for ((key, native) in NATIVE_SYNONYMS) {
                for ((language, surface) in native) {
                    val normalized = TextMatching.normalizeForLookup(surface)
                    put(normalized, ElementAlias(normalized, key, language, ElementAlias.Kind.NAME))
                }
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
