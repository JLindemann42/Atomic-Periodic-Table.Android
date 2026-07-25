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
     * Whether an alias appears in the query.
     *
     * Also accepts a short grammatical ending on the alias, because the shipped labels give the
     * base form while users write the inflected one — Swedish "smältpunkten" for the label
     * "Smältpunkt", German "Dichte" in "der Dichte des Goldes".
     */
    private fun mentions(normalizedQuery: String, alias: String): Boolean {
        if (alias.length < minimumLength(alias)) return false
        if (TextMatching.containsToken(normalizedQuery, normalizedQuery, alias)) return true
        if (alias.length < 5 || alias.contains(' ')) return false
        return TextMatching.splitQueryTokens(normalizedQuery).any { word ->
            word.length > alias.length &&
                    word.length - alias.length <= MAX_INFLECTION_SUFFIX &&
                    word.startsWith(alias)
        }
    }

    private fun buildAliases(): Map<String, String> {
        val map = HashMap<String, String>(512)

        fun add(alias: String, fieldId: String) {
            val key = TextMatching.normalizeForLookup(alias).trim()
            if (key.length < minimumLength(key)) return
            // First writer wins, so the localized label outranks the generic fallbacks.
            map.putIfAbsent(key, fieldId)
        }

        // Layer 1 — the app's own localized labels, in whatever language the provider resolves.
        for (spec in FieldRegistry.ALL) {
            val label = runCatching { strings.get(spec.labelRes) }.getOrNull() ?: continue
            val cleaned = label.replace(":", "").replace("：", "").trim()
            if (cleaned.isNotEmpty() && !cleaned.startsWith("str:")) add(cleaned, spec.id)
        }

        // Layer 2 — colloquial phrasings the labels do not cover.
        for ((alias, fieldId) in COLLOQUIAL) add(alias, fieldId)

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
        /** Longest grammatical ending accepted on a field label, e.g. "smältpunkt" + "en". */
        const val MAX_INFLECTION_SUFFIX = 3

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
         * Cross-language spellings of the highest-traffic fields.
         *
         * Kept deliberately small: the localized labels already cover the formal name in every
         * locale that ships a complete translation. This only backstops the fields users ask
         * about most, in the languages whose spelling differs from English.
         */
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
            "ordnungszahl" to "atomic_number", "numero atomique" to "atomic_number",
            "原子序数" to "atomic_number"
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
            "abundance" to "abundance_earth_crust",
            "in the crust" to "abundance_earth_crust",
            "in seawater" to "abundance_sea_water",
            "in the sun" to "abundance_sun",
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
