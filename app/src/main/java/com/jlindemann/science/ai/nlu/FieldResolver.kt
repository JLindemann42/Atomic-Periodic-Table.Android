package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.FieldSpec
import com.jlindemann.science.ai.retrieval.TextMatching

/** A field the query referred to, with how it was recognised. */
data class FieldMatch(val spec: FieldSpec, val score: Double, val matched: String)

/**
 * Works out which element property a query is asking about.
 *
 * Aliases come from three layers, in priority order:
 *
 *  1. **The app's own localized labels.** Every field's [FieldSpec.labelRes] points at a string
 *     the app already ships in 17 locales (`density_colon`, `melting_point_colon`, …). Resolving
 *     those once per language yields a full set of translated field names for free — no new
 *     translation work, and strictly better coverage than hand-written keyword lists.
 *  2. **Colloquial English phrasings** that no label covers ("how heavy", "how hot does it melt").
 *  3. **The canonical field id itself**, so "atomic_mass" and "atomic mass" both work.
 */
class FieldResolver(private val strings: StringProvider) {

    /** normalised alias -> field id. Built once per language. */
    private val aliases: Map<String, String> by lazy { buildAliases() }

    /**
     * Every field the query mentions, best match first.
     * Longer aliases are matched first so "atomic radius" is not shadowed by "radius".
     */
    fun resolveAll(query: String, limit: Int = 3): List<FieldMatch> {
        val normalized = TextMatching.normalizeForLookup(query)
        val found = LinkedHashMap<String, FieldMatch>()

        // The two discovery fields share every alias they have, so no alias table can separate
        // them - only the interrogative can. Decided before the scan, and scored above any alias,
        // because otherwise "when was helium discovered" is answered with a person's name.
        discoveryField(normalized)?.let { fieldId ->
            FieldRegistry.byId[fieldId]?.let { found[fieldId] = FieldMatch(it, 0.95, "discovery") }
        }

        // Fields whose words the sentence pulls apart, before the contiguous aliases are tried so
        // the more specific reading wins.
        for ((words, fieldId) in SPLIT_FRAMES) {
            if (found.size >= limit) break
            if (found.containsKey(fieldId)) continue
            if (words.any { !TextMatching.containsWord(normalized, it) }) continue
            FieldRegistry.byId[fieldId]?.let { found[fieldId] = FieldMatch(it, 0.9, words.joinToString(" ")) }
        }

        for ((alias, fieldId) in aliases.entries.sortedByDescending { it.key.length }) {
            if (found.size >= limit) break
            if (found.containsKey(fieldId)) continue
            if (!mentions(normalized, alias)) continue
            val spec = FieldRegistry.byId[fieldId] ?: continue
            // Longer aliases are more specific, so they score higher.
            found[fieldId] = FieldMatch(spec, 0.6 + alias.length.coerceAtMost(20) / 50.0, alias)
        }
        return found.values.sortedByDescending { it.score }.take(limit)
    }

    fun resolve(query: String): FieldMatch? = resolveAll(query, 1).firstOrNull()

    /**
     * Which discovery field a question is really asking for, or null when it is not asking.
     *
     * Requires an interrogative as well as the discovery word, so "where is gold found" - which is
     * about abundance and shares the word "found" - falls through to the alias table untouched.
     */
    private fun discoveryField(normalized: String): String? {
        if (Lexicon.DISCOVERY_WORDS.none { TextMatching.containsWord(normalized, it) }) return null
        if (Lexicon.WHEN_WORDS.any { TextMatching.containsWord(normalized, it) }) return "year_discovered"
        if (Lexicon.WHO_WORDS.any { TextMatching.containsWord(normalized, it) }) return "discovered_by"
        return null
    }

    /**
     * Whether an alias appears in the query.
     *
     * Also accepts a short grammatical ending on the alias, because the shipped labels give the
     * base form while users write the inflected one — Swedish "smältpunkten" for the label
     * "Smältpunkt", German "Dichte" in "der Dichte des Goldes".
     */
    private fun mentions(normalizedQuery: String, alias: String): Boolean {
        if (alias.length < minimumLength(alias)) return false
        if (TextMatching.containsToken(normalizedQuery, normalizedQuery, alias)) return true
        // A multiword alias inflects on its last word: "atomic mass" becomes "atomic masses",
        // "melting point" becomes "melting points". Requiring the whole phrase verbatim meant every
        // plural form of the most-asked fields resolved nothing, which is how "sum of the atomic
        // masses of the halogens" ended up retrieving a dataset row.
        if (alias.contains(' ')) return mentionsInflectedPhrase(normalizedQuery, alias)
        if (alias.length < 5) return false
        return TextMatching.splitQueryTokens(normalizedQuery).any { word -> inflects(word, alias) }
    }

    /**
     * Whether [word] is [alias] carrying a grammatical ending.
     *
     * Three shapes, because three of the supported languages need three different ones:
     *  - a plain suffix ("melting point" -> "melting points", "smältpunkt" -> "smältpunkten");
     *  - the English `y`/`ies` plural, without which "and their **densities**" resolved no field at
     *    all and the follow-up inherited the previous turn's boiling point;
     *  - syncope, where an unstressed final vowel is dropped before the ending. Swedish
     *    "atomnummer" becomes "atomnumret", which is the *same length* as the alias, so the suffix
     *    rule could not see it and every "vad är atomnumret för guld" was declined.
     */
    private fun inflects(word: String, alias: String): Boolean {
        if (word.length > alias.length &&
            word.length - alias.length <= MAX_INFLECTION_SUFFIX &&
            word.startsWith(alias)
        ) return true

        if (alias.endsWith("y") && word == alias.dropLast(1) + "ies") return true

        // Germanic compounds put the head noun last and write the whole thing as one word:
        // "medianatommassan" is "median" + "atommassa" + the definite ending, "mediandensitet" is
        // "median" + "densitet". The field is in there, at the end, and nothing that matched from
        // the front could see it — so the aggregation was recognised and the field it applied to
        // was not. Only long aliases qualify, and only with a real word in front of them.
        if (alias.length >= MIN_COMPOUND_HEAD) {
            val at = word.lastIndexOf(alias)
            if (at >= MIN_COMPOUND_MODIFIER && word.length - (at + alias.length) <= MAX_INFLECTION_SUFFIX) {
                return true
            }
        }

        val syncopated = syncopate(alias) ?: return false
        return word.length > syncopated.length &&
                word.length - syncopated.length <= MAX_INFLECTION_SUFFIX &&
                word.startsWith(syncopated)
    }

    /** [alias] with the vowel of its final syllable elided, or null when it has no such syllable. */
    private fun syncopate(alias: String): String? {
        if (alias.length < MIN_SYNCOPE_LENGTH) return null
        val vowelAt = alias.length - 2
        if (alias[vowelAt] !in SYNCOPE_VOWELS) return null
        if (alias.last() in SYNCOPE_VOWELS) return null
        if (alias[vowelAt - 1] in SYNCOPE_VOWELS) return null
        return alias.removeRange(vowelAt, vowelAt + 1)
    }

    /**
     * A multiword alias whose final word carries a grammatical ending.
     *
     * Only the last word may inflect, and only by a short suffix, so "melting point" matches
     * "melting points" without "melting" matching anything it should not.
     */
    private fun mentionsInflectedPhrase(normalizedQuery: String, alias: String): Boolean {
        val parts = alias.split(' ').filter { it.isNotEmpty() }
        if (parts.size < 2) return false
        val words = TextMatching.splitQueryTokens(normalizedQuery)
        for (start in 0..words.size - parts.size) {
            val window = words.subList(start, start + parts.size)
            if (inflectsAt(window, parts, parts.size - 1)) return true
            // The Romance languages put the head noun first and inflect *it*: "point de fusion"
            // pluralises to "points de fusion", not "point de fusions". Only the tail was allowed to
            // carry an ending, so "et leurs points de fusion" resolved no melting point and the
            // follow-up silently answered with the previous turn's field instead.
            if (inflectsAt(window, parts, 0)) return true
        }
        return false
    }

    /** The window matches the alias exactly but for a short ending on the word at [index]. */
    private fun inflectsAt(window: List<String>, parts: List<String>, index: Int): Boolean {
        val stem = parts[index]
        if (stem.length < MIN_INFLECTABLE_WORD) return false
        for (i in parts.indices) {
            if (i == index) continue
            if (window[i] != parts[i]) return false
        }
        val actual = window[index]
        // `>=`, not `>`: an *exact* window match has to succeed here too. The literal check in
        // `mentions` compares strings, so it misses a phrase the user wrote with different
        // punctuation — French writes "énergie d'ionisation" and the alias is spelled with a space,
        // so the whole phrase resolved nothing. Matching over tokens is punctuation-blind.
        return actual.length >= stem.length &&
                actual.length - stem.length <= MAX_INFLECTION_SUFFIX &&
                actual.startsWith(stem)
    }

    private fun buildAliases(): Map<String, String> {
        val map = HashMap<String, String>(512)

        fun add(alias: String, fieldId: String) {
            val key = TextMatching.normalizeForLookup(alias).trim()
            if (key.length < minimumLength(key)) return
            // First writer wins, so the localized label outranks the generic fallbacks.
            map.putIfAbsent(key, fieldId)
        }

        // Layer 0 — the handful of aliases a shipped label claims but does not mean.
        //
        // `series` uses the label "Group:" (as in "Group: Transition metal"), so Layer 1 registers
        // the bare word "group" to it and, because the first writer wins, nothing later can correct
        // that. But "what group is gold in" asks for the numbered group. Registering these first is
        // the only way to override a label without changing what the element screen displays.
        for ((alias, fieldId) in PRIORITY_ALIASES) add(alias, fieldId)

        // Layer 1 — the app's own localized labels, in whatever language the provider resolves.
        for (spec in FieldRegistry.ALL) {
            val label = runCatching { strings.get(spec.labelRes) }.getOrNull() ?: continue
            val cleaned = label.replace(":", "").replace("：", "").trim()
            if (cleaned.isNotEmpty() && !cleaned.startsWith("str:")) add(cleaned, spec.id)
        }

        // Layer 2 — colloquial phrasings the labels do not cover.
        for ((alias, fieldId) in COLLOQUIAL) add(alias, fieldId)

        // Layer 2a: words naming a value of a categorical field, which is how a yes/no question
        // refers to that field.
        for ((alias, fieldId) in VALUE_WORD_ALIASES) add(alias, fieldId)

        // Layer 2b — near-cognates for the most-asked fields. Several locales ship an
        // incomplete strings.xml and fall back to the English label, so a user writing their
        // own language would otherwise get nothing; Filipino, for instance, displays "Density".
        for ((alias, fieldId) in COGNATES) add(alias, fieldId)

        // Layer 3 — the canonical ids themselves.
        for (spec in FieldRegistry.ALL) {
            add(spec.id.replace('_', ' '), spec.id)
            add(spec.id, spec.id)
        }

        return map
    }

    private companion object {
        /** Shortest final word of a phrase that may carry an ending; below this it over-matches. */
        const val MIN_INFLECTABLE_WORD = 4

        /** Longest grammatical ending accepted on a field label, e.g. "smältpunkt" + "en". */
        const val MAX_INFLECTION_SUFFIX = 3

        /** Shortest alias worth trying to syncopate; below this the result is not a stem. */
        const val MIN_SYNCOPE_LENGTH = 5

        /** Shortest alias that may be the head of a Germanic compound. */
        const val MIN_COMPOUND_HEAD = 7

        /** Shortest modifier that may precede that head, so "density" is not found in "adensity". */
        const val MIN_COMPOUND_MODIFIER = 4

        const val SYNCOPE_VOWELS = "aeiouy"

        /**
         * Shortest alias worth indexing, by script.
         *
         * Three characters is a sensible floor for alphabetic scripts, but a Han word is often
         * exactly two characters — 密度 is "density" in full — so the Latin threshold would
         * discard the entire Chinese label set.
         */
        fun minimumLength(alias: String): Int =
            if (alias.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) 2 else 3

        /**
         * Aliases that must outrank the shipped label that would otherwise claim them.
         *
         * Deliberately tiny. Each entry is a case where a UI label is ambiguous as a *question*,
         * and the longer, more specific phrasings ("group of elements" for the family) still win on
         * their own because [resolveAll] matches the longest alias first.
         */
        /**
         * Fields named by words the sentence separates.
         *
         * A contiguous alias cannot reach these. Swedish and German put the subject between the
         * verb and its object — "hur bra **leder** koppar **värme**" is "how well does copper
         * conduct heat" — and English puts the substance inside the phrase: "speed of sound in
         * **helium** gas" resolved the *solid* variant, because "speed of sound in gas" was not
         * contiguous and the generic alias won.
         *
         * Every word must be present; order and distance do not matter. Deliberately few: this is
         * the loosest matcher in the resolver, so it earns entries only where the language
         * genuinely forbids adjacency.
         */
        val SPLIT_FRAMES: List<Pair<List<String>, String>> = listOf(
            listOf("speed", "sound", "gas") to "speed_of_sound_gas",
            listOf("speed", "sound", "liquid") to "speed_of_sound_liquid",
            listOf("leder", "varme") to "thermal_conductivity",
            listOf("leder", "el") to "electrical_conductivity",
            listOf("leitet", "warme") to "thermal_conductivity",
            listOf("geleiden", "warmte") to "thermal_conductivity",
            listOf("conduce", "calor") to "thermal_conductivity",
            listOf("conduit", "chaleur") to "thermal_conductivity",
            listOf("conduce", "calore") to "thermal_conductivity",
            listOf("conduz", "calor") to "thermal_conductivity"
        )

        val PRIORITY_ALIASES: List<Pair<String, String>> = listOf(
            "group" to "group_number",
            "grupp" to "group_number",
            "gruppe" to "group_number",
            "grupo" to "group_number",
            "groupe" to "group_number",
            // The five languages whose word for "group" was missing here, so their shipped "Group:"
            // label claimed it for `series` first and "in quale gruppo è il calcio" answered with
            // the family name rather than the group number.
            "gruppo" to "group_number",
            "groep" to "group_number",
            "समूह" to "group_number",
            "گروپ" to "group_number",
            "哪一族" to "group_number",
            "哪族" to "group_number",
            "第几族" to "group_number",
            // Valence electrons must outrank the bare "electrons" label in every language, for the
            // same reason the English "valence electrons" alias exists: the label is a prefix of
            // the phrase, and first writer wins.
            "valenselektroner" to "valence_electrons",
            "valenselektron" to "valence_electrons",
            "valenselektrone" to "valence_electrons",
            "valenzelektronen" to "valence_electrons",
            "valenzelektron" to "valence_electrons",
            "electrones de valencia" to "valence_electrons",
            "electrons de valence" to "valence_electrons",
            "elettroni di valenza" to "valence_electrons",
            "eletrons de valencia" to "valence_electrons",
            "elektrons van valensie" to "valence_electrons",
            "valence electron" to "valence_electrons",
            "संयोजी इलेक्ट्रॉन" to "valence_electrons",
            "ویلنس الیکٹران" to "valence_electrons",
            "价电子" to "valence_electrons",
            // Same shape: the configuration is named with the word for "electron" plus a noun, and
            // the bare label won.
            "elektronkonfiguration" to "electron_configuration",
            "elektronenkonfiguration" to "electron_configuration",
            "elektronkonfigurasie" to "electron_configuration",
            "configuracion electronica" to "electron_configuration",
            "configuration electronique" to "electron_configuration",
            "configurazione elettronica" to "electron_configuration",
            "configuracao eletronica" to "electron_configuration",
            "इलेक्ट्रॉन विन्यास" to "electron_configuration",
            "الیکٹران ترتیب" to "electron_configuration",
            "电子排布" to "electron_configuration",
            "电子构型" to "electron_configuration"
        )

        /**
         * Cross-language spellings of the highest-traffic fields.
         *
         * Kept deliberately small: the localized labels already cover the formal name in every
         * locale that ships a complete translation. This only backstops the fields users ask
         * about most, in the languages whose spelling differs from English.
         */
        /**
         * Words that name a *value* of a categorical field rather than the field itself.
         *
         * "Is bromine a liquid" never says "phase", and "is silicon a conductor" never says
         * "electrical type" - but each is a plain lookup of that field, and without these the
         * question resolved no field at all and was declined.
         */
        val VALUE_WORD_ALIASES: List<Pair<String, String>> = listOf(
            "liquid" to "phase", "gaseous" to "phase",
            "vatska" to "phase", "flytande" to "phase",
            "flussig" to "phase", "liquide" to "phase", "liquido" to "phase",
            "conductor" to "electrical_type", "semiconductor" to "electrical_type",
            "insulator" to "electrical_type", "halvledare" to "electrical_type",
            "halbleiter" to "electrical_type",
            "man made" to "synthetic", "manmade" to "synthetic", "artificial" to "synthetic",
            "konstgjord" to "synthetic", "kunstlich" to "synthetic",
            "artificiel" to "synthetic", "artificiale" to "synthetic",
            "conducts heat" to "thermal_conductivity", "conduct heat" to "thermal_conductivity",
            "heat conduction" to "thermal_conductivity",
            "leder varme" to "thermal_conductivity", "varmeledning" to "thermal_conductivity",
            "leitet warme" to "thermal_conductivity",
            "conducts electricity" to "electrical_conductivity",
            "how long" to "half_life", "lasts" to "half_life", "hur lange" to "half_life",
            "جوہری وزن" to "atomic_mass", "کثیف" to "density",
            // "What is the symbol of tin" resolved nothing in eight of the eleven languages.
            "simbolo" to "symbol", "simbool" to "symbol", "symbole" to "symbol",
            "प्रतीक" to "symbol", "علامت" to "symbol",
            "符号" to "symbol",
            // The bare adjectives a superlative follow-up is built from: "which is the heaviest".
            "pesante" to "atomic_mass", "pesado" to "atomic_mass", "mabigat" to "atomic_mass",
            // Filipino builds the superlative by prefixing the adjective, so the whole word is a
            // single token and the bare adjective above never matched it.
            "pinakamabigat" to "atomic_mass", "pinakamagaan" to "density",
            "pinakadensidad" to "density",
            "pinakamadensidad" to "density", "pinakamainit" to "melting_point",
            "swaarste" to "atomic_mass", "भारी" to "atomic_mass",
            "भारीपन" to "atomic_mass",
            "भारी कौन" to "atomic_mass",
            "भारी कौन सा" to "atomic_mass",
            "بھاری" to "atomic_mass", "重" to "atomic_mass",
            "denso" to "density", "densa" to "density", "densidade" to "density",
            "madensidad" to "density", "digtheid" to "density", "digste" to "density",
            "घना" to "density", "कثیف" to "density",
            "致密" to "density",
            // Urdu, Hindi and Chinese: the fields these languages name differently from their
            // shipped labels, or that the shipped label leaves untranslated.
            "جوہری عدد" to "atomic_number", "परमाणु क्रमांक" to "atomic_number",
            "परमाणु संख्या" to "atomic_number", "原子序数" to "atomic_number",
            "نقطہ پگھلاؤ" to "melting_point", "गलनांक" to "melting_point",
            "पिघलते" to "melting_point", "पिघलने" to "melting_point",
            "پگھلتے" to "melting_point", "熔化" to "melting_point", "熔点" to "melting_point",
            "نقطہ ابال" to "boiling_point", "क्वथनांक" to "boiling_point", "沸点" to "boiling_point",
            "کثافت" to "density", "घनत्व" to "density", "密度" to "density",
            "جوہری وزن" to "atomic_mass", "परमाणु द्रव्यमान" to "atomic_mass",
            "原子量" to "atomic_mass", "摩尔质量" to "atomic_mass"
        )

        val COGNATES: List<Pair<String, String>> = listOf(
            "densidad" to "density", "densidade" to "density", "densita" to "density",
            "densite" to "density", "dichte" to "density", "densitet" to "density",
            "digtheid" to "density", "घनत्व" to "density", "کثافت" to "density", "密度" to "density",
            "masa atomica" to "atomic_mass", "atommassa" to "atomic_mass",
            "atommasse" to "atomic_mass", "massa atomica" to "atomic_mass",
            "punto de fusion" to "melting_point", "smaltpunkt" to "melting_point",
            "schmelzpunkt" to "melting_point", "point de fusion" to "melting_point",
            "ponto de fusao" to "melting_point", "punto di fusione" to "melting_point",
            "smeltpunt" to "melting_point", "熔点" to "melting_point",
            "punto de ebullicion" to "boiling_point", "kokpunkt" to "boiling_point",
            "siedepunkt" to "boiling_point", "point d ebullition" to "boiling_point",
            "kookpunt" to "boiling_point", "沸点" to "boiling_point",
            "electronegatividad" to "electronegativity", "elektronegativitet" to "electronegativity",
            "elektronegativitat" to "electronegativity", "electronegativite" to "electronegativity",
            "elettronegativita" to "electronegativity", "电负性" to "electronegativity",
            "numero atomico" to "atomic_number", "atomnummer" to "atomic_number",
            // Swedish degeminates as well as syncopating before the definite ending — "nummer"
            // becomes "numret", not "nummret" — so no general stem rule reaches this form, and
            // "vad är atomnumret för guld" resolved no field at all.
            "atomnumret" to "atomic_number", "atomnumren" to "atomic_number",
            "ordnungszahl" to "atomic_number", "numero atomique" to "atomic_number",
            "原子序数" to "atomic_number",
            // Discovery. Both of these are real stored fields, but nothing outside English resolved
            // them, so "Vem upptäckte järn" resolved no field at all and inherited the previous
            // turn's density instead — answering a question about history with a number.
            "upptackte" to "discovered_by", "upptackt av" to "discovered_by",
            "entdeckt" to "discovered_by", "entdecker" to "discovered_by",
            "decouvert" to "discovered_by", "descubierto" to "discovered_by",
            "descubridor" to "discovered_by", "scoperto" to "discovered_by",
            "descoberto" to "discovered_by", "ontdek" to "discovered_by",
            "khoj" to "discovered_by", "发现者" to "discovered_by",
            "upptacktes" to "year_discovered", "entdeckt wurde" to "year_discovered",
            "ano de descubrimiento" to "year_discovered", "发现年份" to "year_discovered",
            // Appearance and phase are localized enum/text fields users ask about directly.
            "utseende" to "appearance", "aussehen" to "appearance",
            "apariencia" to "appearance", "aspetto" to "appearance",
            "aparencia" to "appearance", "外观" to "appearance",
            // Poisson's ratio is named after a person, so most languages keep "Poisson" and change
            // only the noun — which means the English alias never matches.
            "poissons tal" to "poisson_ratio", "poissontal" to "poisson_ratio",
            "poissonzahl" to "poisson_ratio", "querkontraktionszahl" to "poisson_ratio",
            "coefficient de poisson" to "poisson_ratio", "coeficiente de poisson" to "poisson_ratio",
            "modulo di poisson" to "poisson_ratio", "coeficiente de poisson" to "poisson_ratio",
            "泊松比" to "poisson_ratio",
            // Positive-degree adjectives name the property: "how dense is osmium", "hur tätt är
            // guld", "wie dicht ist blei". Field aliases rather than comparatives, because a
            // comparative reading turns the question into a list of everything denser.
            // Each abundance reservoir by name. All nine labels contain the word "abundance", so a
            // bare "abundance of carbon in the solar system" resolved whichever of them happened to
            // be registered first — always the crust. Naming the reservoir is what disambiguates.
            "earth soils" to "abundance_earth_soils", "in soil" to "abundance_earth_soils",
            "urban soils" to "abundance_urban_soils", "urban soil" to "abundance_urban_soils",
            "sea water" to "abundance_sea_water", "seawater" to "abundance_sea_water",
            "crustal rocks" to "abundance_crustal_rocks",
            "solar system" to "abundance_solar_system",
            "meteorites" to "abundance_meteorites",
            "human body" to "abundance_human_body", "in the body" to "abundance_human_body",
            "earth crust" to "abundance_earth_crust", "earths crust" to "abundance_earth_crust",
            // The possessive is written with an apostrophe, which the tokenizer splits on, so
            // "the earth's crust" is three tokens and neither two-word alias above spans it. The
            // bare noun is unambiguous in a periodic-table app and reaches every phrasing.
            "crust" to "abundance_earth_crust", "jordskorpa" to "abundance_earth_crust",
            // "What kind of element is hydrogen" asks for the family. Swedish frames it with a
            // trailing "för grundämne", which no contiguous alias reached.
            "for grundamne" to "series", "for slags grundamne" to "series",
            "havsvatten" to "abundance_sea_water", "jordskorpan" to "abundance_earth_crust",
            "solsystemet" to "abundance_solar_system", "meteoriter" to "abundance_meteorites",
            "erdkruste" to "abundance_earth_crust", "meerwasser" to "abundance_sea_water",
            // "Group" alone means the group number; the periodic *family* is asked for as
            // "group of elements", which is longer and therefore wins where it applies.
            "group of elements" to "series", "grundamnesgrupp" to "series",
            "elementgrupp" to "series", "elementgruppe" to "series",
            // Specific variants, which must outrank their generic sibling. resolveAll matches the
            // longest alias first, so these only needed to exist: "the empirical atomic radius"
            // was resolving plain atomic_radius, and "negative oxidation states" the positive set.
            "allen electronegativity" to "electronegativity_allen",
            "electronegativity allen" to "electronegativity_allen",
            "empirical atomic radius" to "atomic_radius_empirical",
            "atomic radius empirical" to "atomic_radius_empirical",
            "negative oxidation" to "oxidation_states_negative",
            "electrons arranged" to "electron_configuration",
            "electron arrangement" to "electron_configuration",
            "elektroner ordnade" to "electron_configuration",
            "speed of sound in liquid" to "speed_of_sound_liquid",
            "liquid speed of sound" to "speed_of_sound_liquid",
            "speed of sound in gas" to "speed_of_sound_gas",
            "gas speed of sound" to "speed_of_sound_gas",
            // Family words that name one field. "Heat" is also a THERMO category word, so without
            // these the category branch claimed the question and answered with the whole family.
            "heat of fusion" to "fusion_heat", "fusion heat" to "fusion_heat",
            "heat of vaporization" to "vaporization_heat",
            "heat of vaporisation" to "vaporization_heat",
            "vaporization heat" to "vaporization_heat",
            "specific heat" to "specific_heat_capacity",
            "molar heat" to "molar_heat_capacity",
            "conduct heat" to "thermal_conductivity", "conducts heat" to "thermal_conductivity",
            // Hindi and Urdu field names. These could not have worked before the script fixes —
            // normalisation stripped the vowels and the tokenizer split on them — so the whole
            // vocabulary was untestable until now.
            "घना" to "density", "घनत्व" to "density",
            "कثیف" to "density", "کثافت" to "density",
            "परमाणु संख्या" to "atomic_number", "जوہری نمبر" to "atomic_number",
            "جوہری نمبر" to "atomic_number",
            "परमाणु द्रव्यमान" to "atomic_mass", "جوہری وزن" to "atomic_mass",
            "गलनांक" to "melting_point", "نقطہ پگھلاؤ" to "melting_point",
            "क्वथनांक" to "boiling_point",
            "समस्थानिक" to "isotopes",
            "farg" to "appearance", "ser ut" to "appearance", "utseende" to "appearance",
            "aussehen" to "appearance", "farbe" to "appearance", "color" to "appearance",
            "fas" to "phase", "elektrisk typ" to "electrical_type",
            "magnetisk typ" to "magnetic_type",
            "manniskokroppen" to "abundance_human_body", "kroppen" to "abundance_human_body",
            "dense" to "density", "tat" to "density", "tatt" to "density",
            // Plural and Filipino forms of the bare adjective. "Quali elementi sono più densi
            // dell'oro" and "gaano kadensidad" resolved no field, so the threshold-from-element
            // filter had nothing to compare and the question was declined.
            "densi" to "density", "densos" to "density", "densas" to "density",
            "kadensidad" to "density", "madensidad" to "density", "घने" to "density",
            "dicht" to "density", "denso" to "density", "densa" to "density",
            "electronegative" to "electronegativity", "elektronegativ" to "electronegativity",
            "elektronegativt" to "electronegativity", "electronegativo" to "electronegativity",
            "lattice" to "crystal_structure", "gitter" to "crystal_structure",
            "kristallgitter" to "crystal_structure", "reticolo" to "crystal_structure",
            "neutrons" to "common_neutrons", "neutron count" to "common_neutrons",
            // ---- Fields the shipped labels do not reach --------------------------------------
            // Layer 1 derives aliases from the element screens' labels, which covers the formal
            // name wherever a locale ships a complete strings.xml. These are the gaps that left
            // whole question shapes unanswerable in a language: the verb form users actually type
            // ("smälter", "kokar", "väger"), and fields whose label never became an alias.
            "atomradie" to "atomic_radius", "atomradius" to "atomic_radius",
            "radio atomico" to "atomic_radius", "rayon atomique" to "atomic_radius",
            "raggio atomico" to "atomic_radius", "raio atomico" to "atomic_radius",
            "joniseringsenergi" to "ionization_energy", "ionisierungsenergie" to "ionization_energy",
            "energia de ionizacion" to "ionization_energy", "energie d ionisation" to "ionization_energy",
            "energia di ionizzazione" to "ionization_energy",
            "varmeledningsformaga" to "thermal_conductivity", "varmeledning" to "thermal_conductivity",
            "warmeleitfahigkeit" to "thermal_conductivity",
            "conductividad termica" to "thermal_conductivity",
            "conductivite thermique" to "thermal_conductivity",
            "conducibilita termica" to "thermal_conductivity",
            "condutividade termica" to "thermal_conductivity",
            "resistivitet" to "resistivity", "resistividad" to "resistivity",
            "resistivite" to "resistivity", "resistivita" to "resistivity",
            "hardhet" to "mohs_hardness", "harte" to "mohs_hardness", "dureza" to "mohs_hardness",
            "durete" to "mohs_hardness", "durezza" to "mohs_hardness",
            "oxidationstal" to "oxidation_states", "oxidationszahl" to "oxidation_states",
            "estado de oxidacion" to "oxidation_states", "etat d oxydation" to "oxidation_states",
            "protoner" to "protons", "protonen" to "protons", "protones" to "protons",
            "elektroner" to "electrons", "elektronen" to "electrons", "electrones" to "electrons",
            "neutroner" to "common_neutrons", "neutronen" to "common_neutrons",
            "amnesgrupp" to "series", "grupo de elementos" to "series",
            "group" to "group_number", "grupp" to "group_number", "gruppe" to "group_number",
            // Verb phrasings. "At what temperature does tungsten melt" is the same question as
            // "what is tungsten's melting point", and only the noun form was ever recognised.
            "smalter" to "melting_point", "smalta" to "melting_point",
            "schmilzt" to "melting_point", "schmelzen" to "melting_point",
            "fond" to "melting_point", "funde" to "melting_point", "fonde" to "melting_point",
            // Third-person plural and the two languages whose verb was missing entirely: "quali
            // elementi fondono sopra 2000 kelvin", "watter elemente smelt bo 2000 kelvin",
            // "aling mga elemento ang natutunaw sa itaas ng 2000 kelvin".
            "fondono" to "melting_point", "smelt" to "melting_point", "smeltend" to "melting_point",
            "natutunaw" to "melting_point", "matunaw" to "melting_point",
            "tunaw" to "melting_point", "derretem" to "melting_point", "fundem" to "melting_point",
            "kokar" to "boiling_point", "kocht" to "boiling_point",
            "bout" to "boiling_point", "hierve" to "boiling_point", "bolle" to "boiling_point",
            // The verb "to boil" in the eight languages that had only the noun. Every threshold
            // question is phrased with the verb — "welche Elemente sieden unter 100 Kelvin" — so
            // the whole shape was unreachable outside English, Swedish and Spanish.
            "sieden" to "boiling_point", "siedet" to "boiling_point",
            "bouillent" to "boiling_point", "bouillir" to "boiling_point",
            "bollono" to "boiling_point", "bollire" to "boiling_point",
            "fervem" to "boiling_point", "ferve" to "boiling_point",
            "kook" to "boiling_point", "kumukulo" to "boiling_point",
            "उबलते" to "boiling_point", "उबलना" to "boiling_point",
            "ابلتے" to "boiling_point", "ابلنا" to "boiling_point",
            // Ionization energy, in the locales whose label never became an alias.
            "energia de ionizacao" to "ionization_energy",
            "ionisasie energie" to "ionization_energy",
            "ionisasie-energie" to "ionization_energy",
            "आयनन ऊर्जा" to "ionization_energy",
            "آئنائزیشن انرجی" to "ionization_energy",
            "电离能" to "ionization_energy",
            "vager" to "atomic_mass", "wiegt" to "atomic_mass", "pesa" to "atomic_mass",
            "leder varme" to "thermal_conductivity", "leitet warme" to "thermal_conductivity",
            "tillstand" to "phase", "aggregatzustand" to "phase", "zustand" to "phase",
            "estado fisico" to "phase", "stato fisico" to "phase", "状态" to "phase",

            // ---- The half of the registry the localized labels never reached ------------------
            // Every entry below is a field that resolved in English and in nothing else, found by
            // running the same thirty question shapes in all twelve languages
            // (`multilingual_wide.tsv`). Grouped by field so a gap in one language is visible as a
            // gap in one line.

            // Hardness. The labels give the noun; users write the adjective, and Swedish "hårt"
            // and German "hart" fold to the same string.
            "hart" to "mohs_hardness", "harde" to "mohs_hardness",
            "hardheid" to "mohs_hardness", "katigas" to "mohs_hardness",
            "duro" to "mohs_hardness", "dura" to "mohs_hardness", "dur" to "mohs_hardness",
            "कठोर" to "mohs_hardness", "कठोरता" to "mohs_hardness",
            "سخت" to "mohs_hardness", "سختی" to "mohs_hardness", "硬度" to "mohs_hardness",

            // Appearance. Swedish, German and Afrikaans put the subject inside the verb phrase —
            // "hur ser svavel ut", "wie sieht Schwefel aus" — so only the opening half is
            // contiguous and only it can be an alias.
            "hur ser" to "appearance", "wie sieht" to "appearance", "hoe lyk" to "appearance",
            "como se ve" to "appearance", "quoi ressemble" to "appearance",
            "che aspetto" to "appearance", "hitsura" to "appearance",
            "दिखता" to "appearance", "दिखते" to "appearance", "دکھتا" to "appearance",
            "样子" to "appearance",

            // Phase.
            "etat physique" to "phase", "toestand" to "phase", "aggregasietoestand" to "phase",
            "अवस्था" to "phase", "حالت" to "phase",

            // Atomic radius.
            "atoomradius" to "atomic_radius", "atomikong radius" to "atomic_radius",
            "परमाणु त्रिज्या" to "atomic_radius", "त्रिज्या" to "atomic_radius",
            "جوہری رداس" to "atomic_radius", "رداس" to "atomic_radius",
            "原子半径" to "atomic_radius",

            // Thermal conductivity.
            "geleidingsvermoe" to "thermal_conductivity",
            "termiese geleidingsvermoe" to "thermal_conductivity",
            "तापीय चालकता" to "thermal_conductivity", "चालकता" to "thermal_conductivity",
            "حرارتی موصلیت" to "thermal_conductivity", "موصلیت" to "thermal_conductivity",
            "导热系数" to "thermal_conductivity", "热导率" to "thermal_conductivity",

            // Oxidation states.
            "oksidasietoestande" to "oxidation_states",
            "estados de oxidacion" to "oxidation_states",
            "estados de oxidacao" to "oxidation_states",
            "stati di ossidazione" to "oxidation_states",
            "etats d oxydation" to "oxidation_states",
            "ऑक्सीकरण" to "oxidation_states", "آکسیڈیشن" to "oxidation_states",
            "氧化态" to "oxidation_states",

            // Period.
            "आवर्त" to "period", "دور" to "period", "周期" to "period",

            // Protons. Only the Swedish, German and Spanish plurals were listed.
            "protoni" to "protons", "protone" to "protons", "proton" to "protons",
            "protoes" to "protons", "प्रोटॉन" to "protons", "پروٹون" to "protons",
            "质子" to "protons",

            // Electronegativity.
            "elektronegatibiti" to "electronegativity",
            "elektronegatiwiteit" to "electronegativity",
            "विद्युत ऋणात्मकता" to "electronegativity",
            "برقی منفیت" to "electronegativity",

            // Atomic number and mass where the shipped label is untranslated.
            "atoomgetal" to "atomic_number", "جوہری عدد" to "atomic_number",
            "atoommassa" to "atomic_mass"
        )

        /**
         * English colloquialisms mapped to field ids. Other languages fall back to their
         * localized label from layer 1, which covers the formal name in every locale.
         */
        val COLLOQUIAL: List<Pair<String, String>> = listOf(
            "how heavy" to "atomic_mass",
            "atomic weight" to "atomic_mass",
            "mass number" to "atomic_mass",
            "how dense" to "density",
            "how hot" to "melting_point",
            "melts at" to "melting_point",
            "melting" to "melting_point",
            "melt" to "melting_point",
            "melts" to "melting_point",
            "boils at" to "boiling_point",
            "boiling" to "boiling_point",
            "boil" to "boiling_point",
            "boils" to "boiling_point",
            "dense" to "density",
            "heavy" to "atomic_mass",
            "hard" to "mohs_hardness",
            "how big" to "atomic_radius",
            "size" to "atomic_radius",
            "radius" to "atomic_radius",
            "how hard" to "mohs_hardness",
            "hardness" to "mohs_hardness",
            "conducts heat" to "thermal_conductivity",
            "heat conduction" to "thermal_conductivity",
            "conducts electricity" to "electrical_type",
            "conductivity" to "thermal_conductivity",
            "resistance" to "resistivity",
            "magnetism" to "magnetic_type",
            "magnetic" to "magnetic_type",
            "electron config" to "electron_configuration",
            "configuration" to "electron_configuration",
            "shells" to "electron_shells",
            // Longer than the bare "electrons" label, so it wins and "how many valence electrons
            // does sulfur have" no longer answers with the total electron count.
            "valence electrons" to "valence_electrons",
            "valence electron" to "valence_electrons",
            "outer electrons" to "valence_electrons",
            "outermost electrons" to "valence_electrons",
            "valence" to "valence_electrons",
            "abundance" to "abundance_earth_crust",
            "in the crust" to "abundance_earth_crust",
            "in seawater" to "abundance_sea_water",
            "in the sun" to "abundance_sun",
            "in the universe" to "abundance_solar_system",
            "in the cosmos" to "abundance_solar_system",
            "universe" to "abundance_solar_system",
            "conduct electricity" to "electrical_type",
            "conducts heat" to "thermal_conductivity",
            "electrical conductor" to "electrical_type",
            "in the human body" to "abundance_human_body",
            "in the body" to "abundance_human_body",
            "discovered" to "year_discovered",
            "who discovered" to "discovered_by",
            "when discovered" to "year_discovered",
            "what does it look like" to "appearance",
            "look like" to "appearance",
            "looks like" to "appearance",
            "appearance" to "appearance",
            "colour" to "appearance",
            "color" to "appearance",
            "state of matter" to "phase",
            "oxidation state" to "oxidation_states",
            "oxidation number" to "oxidation_states",
            "valency" to "oxidation_states",
            "period" to "period",
            "row" to "period",
            "group number" to "group_number",
            "column" to "group_number",
            "stiffness" to "young_modulus",
            "elasticity" to "young_modulus",
            "speed of sound" to "speed_of_sound_solid",
            "crystal" to "crystal_structure",
            "structure" to "crystal_structure",
            // Longer aliases win, so this beats the bare "group" alias below.
            "space group" to "space_group_name",
            "half life" to "common_neutrons",
            "cas" to "cas_number",
            "family" to "series",
            "category" to "series",
            "group" to "series"
        )
    }
}
