package com.jlindemann.science.ai.data

import androidx.annotation.StringRes
import com.jlindemann.science.R

/** How a field's values should be interpreted. */
enum class FieldKind { NUMERIC, ENUM, TEXT, STRUCT, ID, LINK }

/** Broad grouping, used to answer "tell me about its thermal properties" and to order overviews. */
enum class FieldCategory {
    IDENTITY, THERMO, ATOMIC, ELECTROMAGNETIC, CRYSTAL, MECHANICAL, NUCLEAR, ABUNDANCE, SAFETY, IDS
}

/** Physical dimension, so the agent knows which units are interchangeable. */
enum class Dimension {
    TEMPERATURE, MASS, DENSITY, LENGTH, ENERGY, ENERGY_PER_MOL, PRESSURE, POWER, TIME,
    VELOCITY, RESISTIVITY, CONDUCTIVITY, VOLUME_PER_MOL, HEAT_CAPACITY, CONCENTRATION,
    AREA, DIMENSIONLESS
}

/**
 * What a user must own to see a field's value.
 *
 * The element screens already gate these; the agent has to gate the same set or it becomes a way
 * around the paywall — "what is the Young's modulus of gold" would answer in full what the Mechanical
 * section shows as a lock. Declared alongside the field rather than as a list somewhere else, so the
 * entitlement travels with the thing it protects and a new field cannot quietly default to free
 * without someone writing it down.
 */
enum class Tier {
    FREE,

    /** Behind the PRO subscription, matching `InfoExtension`'s `proPrefValue == 100` blocks. */
    PRO,

    /** Behind PRO+, matching the compound-analysis gate in the legacy handlers. */
    PRO_PLUS
}

/** Where a citation for this field should send the user. */
enum class DeepLinkTarget {
    ELEMENT_INFO, NUCLIDE, EMISSION, CONSTANTS, DICTIONARY, EQUATIONS, POISSON, GEOLOGY,
    SOLUBILITY, ION, ELECTRODE, PH, MOLAR_MASS_CALC, UNIT_CONVERTER, IDEAL_GAS,
    REACTION_BALANCER, LEARNING_GAMES, FLASHCARDS, RESEND_QUERY,

    /** The PRO page, for an answer withheld behind the paywall. */
    PRO_PAGE
}

/**
 * Everything the agent needs to know about one queryable field.
 *
 * @property id canonical snake_case identifier — the only field name used in a QueryPlan
 * @property jsonKeys the JSON key(s) backing it; multi-key fields such as melting point carry
 *   their Kelvin/Celsius/Fahrenheit variants together so a unit request is a pure lookup
 * @property labelRes an existing app string, which gives free localized aliases in all 17 locales
 * @property allowsRange whether a hyphen in this field means "to" rather than "minus"
 * @property localized whether the value differs between language files
 * @property higherIsMore whether "most"/"highest" means the numerically largest value
 * @property ordinalRange for banks such as ionization energies, the valid 1-based slot range
 */
data class FieldSpec(
    val id: String,
    val jsonKeys: List<String>,
    val kind: FieldKind,
    val category: FieldCategory,
    @StringRes val labelRes: Int,
    val dimension: Dimension? = null,
    val canonicalUnit: String? = null,
    val deepLink: DeepLinkTarget = DeepLinkTarget.ELEMENT_INFO,
    val allowsRange: Boolean = false,
    val localized: Boolean = false,
    val higherIsMore: Boolean = true,
    val ordinalRange: IntRange? = null,
    val enumValues: List<String> = emptyList(),
    /**
     * A label that reads correctly inside a sentence, when [labelRes] does not.
     *
     * Reusing the element screens' labels gives free aliases in every locale and is excellent for
     * *parsing*. It is wrong for *rendering*: those labels are table headings, and the abundance
     * ones are prepositional phrases. Dropping "I jordskorpan" into the property template produced
     * "Titan:s I jordskorpan är 4500" — two defects in one sentence. Null means [labelRes] already
     * reads as a noun phrase.
     */
    @StringRes val sentenceLabelRes: Int? = null,
    /**
     * The unit as a reader should see it, when that differs from [canonicalUnit].
     *
     * [canonicalUnit] is a language-invariant token chosen so values can be grouped and converted;
     * it is not always something to put in a sentence. The abundance fields are the case that
     * forced the split: the solar reservoirs are counted in atoms against a silicon reference,
     * which is prose, not a symbol.
     */
    @StringRes val unitLabelRes: Int? = null,
    /**
     * Whether to state the unit when the authored value did not carry one.
     *
     * Off by default, because most of the element JSON writes its units into the value
     * (`"2743 (K)"`, `"19.3 (g/cm³)"`) and [Quantity.display] echoing the source verbatim is the
     * right behaviour there. The abundance reservoirs are stored as bare numbers, so without this
     * the agent answers "gold's abundance in sea water is 0.011" and the figure means nothing.
     */
    val surfaceUnit: Boolean = false,
    /** What the user must own to be told this value. See [Tier]. */
    val tier: Tier = Tier.FREE
) {
    /** The label to use inside a sentence, which is [sentenceLabelRes] when one is defined. */
    @StringRes fun sentenceLabel(): Int = sentenceLabelRes ?: labelRes

    /** The JSON key for a 1-based slot of a banked field, or the primary key when not banked. */
    fun jsonKeyForOrdinal(ordinal: Int?): String {
        val range = ordinalRange ?: return jsonKeys.first()
        val slot = (ordinal ?: 1).coerceIn(range)
        return jsonKeys.first().removeSuffix("1") + slot
    }

    /** Canonicalise an ENUM value; null when it is one of the absent-value sentinels. */
    fun canonicalise(raw: String): String? {
        if (ValueParser.isNullToken(raw)) return null
        return when (id) {
            "series" -> SeriesCanon.series(raw).takeIf { it != SeriesId.UNKNOWN }?.name
            "block" -> SeriesCanon.block(raw).takeIf { it != Block.UNKNOWN }?.name
            "phase" -> SeriesCanon.phase(raw)
            "radioactive" -> if (SeriesCanon.radioactive(raw)) "YES" else "NO"
            else -> raw.trim().lowercase().ifEmpty { null }
        }
    }

    /** A copy that parses as a plain scalar, used when recursing into STRUCT parts. */
    fun asScalar(): FieldSpec =
        if (kind == FieldKind.STRUCT) copy(kind = FieldKind.NUMERIC) else this

    /**
     * [Quantity.display] with its unit stated, for the fields that opt into that.
     *
     * Adds nothing when the authored value already carries a unit, so `"8400 mg/kg"` never comes
     * back doubled — that was the element screen's long-standing bug, and it is the reason this
     * decision lives in one place rather than at each call site.
     *
     * @param unitLabel resolves a string resource. A lambda rather than a `StringProvider` or a
     *   `Context` so this layer stays free of both; the element screen passes `getString`.
     */
    fun displayWithUnit(quantity: Quantity, unitLabel: (Int) -> String): String {
        if (!surfaceUnit || quantity.unitAuthored) return quantity.display
        val label = unitLabelRes?.let { runCatching { unitLabel(it) }.getOrNull() }?.trim()
        // FakeStrings resolves an unknown id to "str:<id>"; an unresolved label must never print.
        if (label.isNullOrEmpty() || label.startsWith("str:")) return quantity.display
        return "${quantity.display} $label"
    }
}

/**
 * The catalogue of every field the agent can answer about.
 *
 * Two fields the legacy agent read — `element_period` and `element_group_number` — do not exist
 * in the JSON at all (0 of 118 elements), so the period and group-number branches silently
 * answered with empty strings. They are derived here from atomic number instead.
 */
object FieldRegistry {

    /** Sentinel keys for values computed rather than read. Never present in the JSON. */
    const val DERIVED_PERIOD = "__derived_period"
    const val DERIVED_GROUP = "__derived_group"
    const val DERIVED_VALENCE = "__derived_valence"
    const val DERIVED_SYNTHETIC = "__derived_synthetic"

    /**
     * Elements with no stable isotopes that do not occur naturally in appreciable amounts.
     *
     * Technetium (43) and promethium (61) are the two gaps below uranium; everything heavier
     * than uranium (92) is synthetic. Neptunium and plutonium occur in trace amounts in uranium
     * ore but are conventionally counted as synthetic, which is the convention used here.
     *
     * This matters for correctness, not just completeness: without it "the heaviest naturally
     * occurring element" answers tennessine, which has only ever existed a few atoms at a time
     * in an accelerator. The right answer is uranium.
     */
    fun isSynthetic(atomicNumber: Int): Boolean =
        atomicNumber == 43 || atomicNumber == 61 || atomicNumber > 92

    /**
     * Valence electrons for a main-group element, or null where the concept does not apply
     * cleanly — the d-block and f-block, where inner shells are still filling.
     *
     * Groups 1 and 2 give 1 and 2; groups 13 to 18 give 3 to 8. Helium is the exception: it sits
     * in group 18 but has only 2 electrons in total.
     */
    fun valenceElectrons(atomicNumber: Int): Int? {
        if (atomicNumber == 2) return 2
        val group = groupOf(atomicNumber) ?: return null
        return when (group) {
            1, 2 -> group
            in 13..18 -> group - 10
            else -> null
        }
    }

    val ALL: List<FieldSpec> = buildList {

        // ---- Identity -------------------------------------------------------------------
        add(spec("name", "element", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.english_name_colon, localized = true))
        add(spec("symbol", "short", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.symbol_colon))
        add(spec("atomic_number", "element_atomic_number", FieldKind.NUMERIC, FieldCategory.IDENTITY, R.string.atomic_number_label))
        add(spec("description", "description", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.description_colon, localized = true))
        add(spec("appearance", "element_appearance", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.appearance_colon, localized = true))
        add(spec("year_discovered", "element_year", FieldKind.NUMERIC, FieldCategory.IDENTITY, R.string.year_discovered_colon))
        add(spec("discovered_by", "element_discovered_name", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.discovered_by_colon))
        add(spec("series", "element_group", FieldKind.ENUM, FieldCategory.IDENTITY, R.string.group_label, localized = true))
        add(spec("block", "element_block", FieldKind.ENUM, FieldCategory.IDENTITY, R.string.block_colon))
        // Period and group are computed from atomic number, not stored: the JSON has no
        // element_period or element_group_number key on any of the 118 elements. They are
        // declared here so they can still be asked about, filtered on and cited.
        add(spec("period", DERIVED_PERIOD, FieldKind.NUMERIC, FieldCategory.IDENTITY, R.string.period_colon))
        add(spec("group_number", DERIVED_GROUP, FieldKind.NUMERIC, FieldCategory.IDENTITY, R.string.group_number_colon))
        add(spec("valence_electrons", DERIVED_VALENCE, FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.valence_electrons_colon))
        add(spec("synthetic", DERIVED_SYNTHETIC, FieldKind.ENUM, FieldCategory.IDENTITY, R.string.synthetic_colon))
        add(spec("phase", "element_phase", FieldKind.ENUM, FieldCategory.IDENTITY, R.string.phase_stp_colon, localized = true))
        add(spec("wikilink", "wikilink", FieldKind.LINK, FieldCategory.IDENTITY, R.string.description_colon))

        // ---- Atomic ---------------------------------------------------------------------
        add(spec("atomic_mass", "element_atomicmass", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.atomic_mass_colon, Dimension.MASS, "u"))
        add(spec("density", "element_density", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.density_colon, Dimension.DENSITY, "g/cm³"))
        add(spec("electronegativity", "element_electronegativty", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.electronegativity_colon, Dimension.DIMENSIONLESS))
        add(spec("electronegativity_allen", "electronegativity_allen", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.electronegativity_allen_colon, Dimension.DIMENSIONLESS))
        add(spec("electron_configuration", "element_electron_config", FieldKind.TEXT, FieldCategory.ATOMIC, R.string.electron_configuration_colon))
        add(spec("electron_shells", "element_shells_electrons", FieldKind.TEXT, FieldCategory.ATOMIC, R.string.electron_shell_colon))
        add(spec("electrons", "element_electrons", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.element_electrons))
        add(spec("protons", "element_protons", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.atomic_number_label))
        add(spec("atomic_radius", "element_atomic_radius", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.atomic_radius_calculated_colon, Dimension.LENGTH, "pm"))
        add(spec("atomic_radius_empirical", "element_atomic_radius_e", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.atomic_radius_empirical_colon, Dimension.LENGTH, "pm"))
        add(spec("covalent_radius", "element_covalent_radius", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.covalent_radius_colon, Dimension.LENGTH, "pm"))
        add(spec("van_der_waals_radius", "element_van_der_waals", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.van_der_waals_radius_colon, Dimension.LENGTH, "pm"))
        add(spec("electron_affinity", "electron_affinity", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.electron_affinity_colon, Dimension.ENERGY_PER_MOL, "kJ/mol", tier = Tier.PRO))
        add(spec("work_function", "work_function", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.work_function_colon, Dimension.ENERGY, "eV"))
        add(spec("molar_volume", "molar_volume", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.molar_volume_colon, Dimension.VOLUME_PER_MOL, "cm³/mol"))
        add(spec("ion_charge", "element_ion_charge", FieldKind.TEXT, FieldCategory.ATOMIC, R.string.ion_charge_colon))
        add(spec("oxidation_states", "oxidation_state_pos", FieldKind.TEXT, FieldCategory.ATOMIC, R.string.oxidation_states_colon))
        add(spec("oxidation_states_negative", "oxidation_state_neg", FieldKind.TEXT, FieldCategory.ATOMIC, R.string.oxidation_states_colon))
        add(
            FieldSpec(
                "ionization_energy", (1..30).map { "element_ionization_energy$it" },
                FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.ionization_energies_colon,
                Dimension.ENERGY, "eV", DeepLinkTarget.ION, ordinalRange = 1..30
            )
        )

        // ---- Thermodynamic --------------------------------------------------------------
        add(multiUnit("melting_point", listOf("element_melting_kelvin", "element_melting_celsius", "element_melting_fahrenheit"), R.string.melting_point_colon))
        add(multiUnit("boiling_point", listOf("element_boiling_kelvin", "element_boiling_celsius", "element_boiling_fahrenheit"), R.string.boiling_point_colon))
        add(multiUnit("sublimation_point", listOf("element_sublimation_kelvin", "element_sublimation_celsius", "element_sublimation_fahrenheit"), R.string.melting_point_colon))
        add(spec("fusion_heat", "element_fusion_heat", FieldKind.NUMERIC, FieldCategory.THERMO, R.string.fusion_heat_colon, Dimension.ENERGY_PER_MOL, "kJ/mol"))
        add(spec("vaporization_heat", "element_vaporization_heat", FieldKind.NUMERIC, FieldCategory.THERMO, R.string.vaporization_heat_colon, Dimension.ENERGY_PER_MOL, "kJ/mol"))
        add(spec("specific_heat_capacity", "element_specific_heat_capacity", FieldKind.NUMERIC, FieldCategory.THERMO, R.string.specific_heat_capacity_colon, Dimension.HEAT_CAPACITY, "J/(g·K)"))
        add(spec("molar_heat_capacity", "molar_heat_capacity", FieldKind.NUMERIC, FieldCategory.THERMO, R.string.molar_heat_capacity_colon, Dimension.HEAT_CAPACITY, "J/(mol·K)"))
        add(spec("thermal_conductivity", "thermal_conductivity", FieldKind.NUMERIC, FieldCategory.THERMO, R.string.thermal_conductivity_colon, Dimension.CONDUCTIVITY, "W/(m·K)"))
        // Greek mu, not the micro sign: ValueParser applies NFKC, which folds U+00B5 to U+03BC.
        add(spec("thermal_expansion", "thermal_expansion", FieldKind.NUMERIC, FieldCategory.THERMO, R.string.thermal_expansion_colon, Dimension.DIMENSIONLESS, "μm/(m·K)"))
        add(spec("debye_temperature", "debye_temperature", FieldKind.STRUCT, FieldCategory.THERMO, R.string.debye_temperature_room_temperature, Dimension.TEMPERATURE, "K"))

        // ---- Electromagnetic ------------------------------------------------------------
        add(spec("electrical_type", "electrical_type", FieldKind.ENUM, FieldCategory.ELECTROMAGNETIC, R.string.electrical_type_colon, localized = true))
        add(spec("resistivity", "resistivity", FieldKind.NUMERIC, FieldCategory.ELECTROMAGNETIC, R.string.electrical_resistivity_colon, Dimension.RESISTIVITY, "nΩm"))
        add(spec("magnetic_type", "magnetic_type", FieldKind.ENUM, FieldCategory.ELECTROMAGNETIC, R.string.magnetic_type_colon, localized = true))
        add(spec("superconducting_point", "superconducting_point", FieldKind.NUMERIC, FieldCategory.ELECTROMAGNETIC, R.string.superconducting_point_colon, Dimension.TEMPERATURE, "K", allowsRange = true))
        add(spec("curie_point", "curie_point", FieldKind.NUMERIC, FieldCategory.ELECTROMAGNETIC, R.string.curie_point_colon, Dimension.TEMPERATURE, "K", tier = Tier.PRO))
        add(spec("neel_point", "neel_point", FieldKind.NUMERIC, FieldCategory.ELECTROMAGNETIC, R.string.neel_point_colon, Dimension.TEMPERATURE, "K", tier = Tier.PRO))
        add(spec("refractive_index", "refractive_index", FieldKind.NUMERIC, FieldCategory.ELECTROMAGNETIC, R.string.refractive_index_colon, Dimension.DIMENSIONLESS, allowsRange = true, tier = Tier.PRO))

        // ---- Crystal --------------------------------------------------------------------
        add(spec("crystal_structure", "crystal_structure", FieldKind.ENUM, FieldCategory.CRYSTAL, R.string.crystal_structure))
        add(spec("lattice_constants", "lattice_constants", FieldKind.STRUCT, FieldCategory.CRYSTAL, R.string.crystal_structure, Dimension.LENGTH, "Å"))
        add(spec("space_group_name", "space_group_name", FieldKind.TEXT, FieldCategory.CRYSTAL, R.string.space_group_name_colon, tier = Tier.PRO))
        add(spec("space_group_number", "space_group_number", FieldKind.NUMERIC, FieldCategory.CRYSTAL, R.string.space_group_number_colon, tier = Tier.PRO))

        // ---- Mechanical -----------------------------------------------------------------
        add(spec("young_modulus", "young_modulus", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_young_modulus, Dimension.PRESSURE, "GPa", allowsRange = true, tier = Tier.PRO))
        add(spec("bulk_modulus", "bulk_modulus", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_bulk_modulus, Dimension.PRESSURE, "GPa", allowsRange = true, tier = Tier.PRO))
        add(spec("shear_modulus", "shear_modulus", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_shear_modulus, Dimension.PRESSURE, "GPa", allowsRange = true, tier = Tier.PRO))
        add(spec("poisson_ratio", "poisson_ratio", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_poisson_ratio, Dimension.DIMENSIONLESS, allowsRange = true, deepLink = DeepLinkTarget.POISSON, tier = Tier.PRO))
        add(spec("mohs_hardness", "mohs_hardness", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.mohs_hardness_colon, Dimension.DIMENSIONLESS, allowsRange = true, tier = Tier.PRO))
        add(spec("vickers_hardness", "vickers_hardness", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.vickers_hardness_colon, Dimension.PRESSURE, "MPa", allowsRange = true, tier = Tier.PRO))
        add(spec("brinell_hardness", "brinell_hardness", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.brinell_hardness_colon, Dimension.PRESSURE, "MPa", allowsRange = true, tier = Tier.PRO))
        add(spec("speed_of_sound_solid", "speed_of_sound_solid", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_speed_sound, Dimension.VELOCITY, "m/s", allowsRange = true, tier = Tier.PRO))
        add(spec("speed_of_sound_liquid", "speed_of_sound_liquid", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_speed_sound, Dimension.VELOCITY, "m/s", allowsRange = true, tier = Tier.PRO))
        add(spec("speed_of_sound_gas", "speed_of_sound_gas", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_speed_sound, Dimension.VELOCITY, "m/s", allowsRange = true, tier = Tier.PRO))

        // ---- Nuclear --------------------------------------------------------------------
        add(spec("radioactive", "radioactive", FieldKind.ENUM, FieldCategory.NUCLEAR, R.string.radioactive_colon))
        add(spec("neutron_cross_section", "neutron_cross_sectional", FieldKind.NUMERIC, FieldCategory.NUCLEAR, R.string.neutron_cross_sectional_colon, Dimension.AREA, "b", deepLink = DeepLinkTarget.NUCLIDE))
        add(spec("common_neutrons", "element_neutron_common", FieldKind.NUMERIC, FieldCategory.NUCLEAR, R.string.isotopes_colon, deepLink = DeepLinkTarget.NUCLIDE))

        // ---- Abundance ------------------------------------------------------------------
        // Every abundance label is a table heading phrased as a prepositional phrase — "In the
        // Earth's crust", "I jordskorpan" — so each carries a sentence form as well.
        //
        // The units are taken from the element info screen, which is the app's source of truth for
        // them, and they are not all the same. Stamping mg/kg on all nine — as this table used to —
        // labelled sea water and the two solar reservoirs with a unit they are not measured in.
        add(abundance("abundance_earth_crust", "earth_crust", R.string.abundance_earth_crust, R.string.ai_field_abundance_earth_crust))
        add(abundance("abundance_earth_soils", "earth_soils", R.string.abundance_earth_soils, R.string.ai_field_abundance_earth_soils))
        add(abundance("abundance_urban_soils", "urban_soils", R.string.abundance_urban_soil, R.string.ai_field_abundance_urban_soils))
        add(abundance("abundance_sea_water", "sea_water", R.string.abundance_sea_water, R.string.ai_field_abundance_sea_water,
            canonicalUnit = UNIT_MICROGRAM_PER_LITRE, unitLabelRes = R.string.ai_unit_ug_per_l))
        add(abundance("abundance_crustal_rocks", "crustal_rocks", R.string.abundance_crustal_rocks, R.string.ai_field_abundance_crustal_rocks))
        add(abundance("abundance_sun", "sun", R.string.abundance_sun, R.string.ai_field_abundance_sun,
            dimension = Dimension.DIMENSIONLESS, canonicalUnit = UNIT_ATOMS_PER_SILICON, unitLabelRes = R.string.ai_unit_atoms_per_si))
        add(abundance("abundance_solar_system", "solar_system", R.string.abundance_solar_system, R.string.ai_field_abundance_solar_system,
            dimension = Dimension.DIMENSIONLESS, canonicalUnit = UNIT_ATOMS_PER_SILICON, unitLabelRes = R.string.ai_unit_atoms_per_si))
        // Meteorite and human-body values author their own unit — "8400 mg/kg", "65% (by mass)" —
        // so the label below is only a fallback for the rare cell that does not.
        add(abundance("abundance_meteorites", "meteorites", R.string.abundance_meteorites, R.string.ai_field_abundance_meteorites))
        add(abundance("abundance_human_body", "human_body", R.string.abundance_human_body, R.string.ai_field_abundance_human_body))

        // ---- Safety (NFPA 704) ----------------------------------------------------------
        // Labels, not sentence templates. These four used to point at `ai_safety_health` and its
        // siblings — "• **Health:** %1$d/4 (%2$s)" — which a label lookup resolves with no
        // arguments, so the placeholders reached the screen verbatim. `nfpa_special` pointed at the
        // *health* template on top of that, so it was mislabelled as well as malformed.
        add(spec("nfpa_health", "health", FieldKind.NUMERIC, FieldCategory.SAFETY, R.string.ai_field_nfpa_health, tier = Tier.PRO))
        add(spec("nfpa_flammability", "flammability", FieldKind.NUMERIC, FieldCategory.SAFETY, R.string.ai_field_nfpa_flammability, tier = Tier.PRO))
        add(spec("nfpa_instability", "instability", FieldKind.NUMERIC, FieldCategory.SAFETY, R.string.ai_field_nfpa_instability, tier = Tier.PRO))
        add(spec("nfpa_special", "special", FieldKind.TEXT, FieldCategory.SAFETY, R.string.ai_field_nfpa_special, tier = Tier.PRO))

        // ---- Identifiers ----------------------------------------------------------------
        add(spec("cas_number", "cas_number", FieldKind.ID, FieldCategory.IDS, R.string.cas_number_colon))
        add(spec("eg_number", "eg_number", FieldKind.ID, FieldCategory.IDS, R.string.eg_number_colon))
    }

    val byId: Map<String, FieldSpec> = ALL.associateBy { it.id }

    /** Reverse lookup from any backing JSON key to its spec. */
    val byJsonKey: Map<String, FieldSpec> =
        ALL.flatMap { spec -> spec.jsonKeys.map { it to spec } }.toMap()

    /** Fields that can be ranked, filtered and aggregated. */
    val numericFields: List<FieldSpec> = ALL.filter { it.kind == FieldKind.NUMERIC }

    fun byCategory(category: FieldCategory): List<FieldSpec> = ALL.filter { it.category == category }

    /**
     * Period for an atomic number. The JSON has no `element_period` key, so it is derived from
     * where each period ends: 2, 10, 18, 36, 54, 86, 118.
     */
    fun periodOf(atomicNumber: Int): Int = when {
        atomicNumber <= 0 -> 0
        atomicNumber <= 2 -> 1
        atomicNumber <= 10 -> 2
        atomicNumber <= 18 -> 3
        atomicNumber <= 36 -> 4
        atomicNumber <= 54 -> 5
        atomicNumber <= 86 -> 6
        atomicNumber <= 118 -> 7
        else -> 0
    }

    /**
     * Group (column) 1..18 for an atomic number, or null for the f-block, which sits outside
     * the numbered groups. Also derived, since the JSON has no `element_group_number` key.
     */
    fun groupOf(atomicNumber: Int): Int? = GROUP_OF_Z.getOrNull(atomicNumber)?.takeIf { it > 0 }

    /** Index is the atomic number; 0 means "no numbered group" (lanthanoids and actinides). */
    private val GROUP_OF_Z: IntArray = IntArray(119).also { g ->
        fun set(range: IntRange, group: Int) = range.forEach { g[it] = group }
        g[1] = 1; g[2] = 18
        set(3..3, 1); set(4..4, 2); set(5..5, 13); set(6..6, 14)
        set(7..7, 15); set(8..8, 16); set(9..9, 17); set(10..10, 18)
        set(11..11, 1); set(12..12, 2); set(13..13, 13); set(14..14, 14)
        set(15..15, 15); set(16..16, 16); set(17..17, 17); set(18..18, 18)
        set(19..19, 1); set(20..20, 2)
        for (z in 21..30) g[z] = z - 18            // Sc..Zn -> 3..12
        for (z in 31..36) g[z] = z - 18            // Ga..Kr -> 13..18
        set(37..37, 1); set(38..38, 2)
        for (z in 39..48) g[z] = z - 36            // Y..Cd  -> 3..12
        for (z in 49..54) g[z] = z - 36            // In..Xe -> 13..18
        set(55..55, 1); set(56..56, 2)
        // 57..71 lanthanoids: no numbered group, left at 0
        for (z in 72..80) g[z] = z - 68            // Hf..Hg -> 4..12
        set(71..71, 3)                             // Lu is conventionally group 3
        for (z in 81..86) g[z] = z - 68            // Tl..Rn -> 13..18
        set(87..87, 1); set(88..88, 2)
        // 89..102 actinides: no numbered group
        set(103..103, 3)                           // Lr, group 3
        for (z in 104..112) g[z] = z - 100         // Rf..Cn -> 4..12
        for (z in 113..118) g[z] = z - 100         // Nh..Og -> 13..18
    }

    private fun spec(
        id: String,
        jsonKey: String,
        kind: FieldKind,
        category: FieldCategory,
        @StringRes labelRes: Int,
        dimension: Dimension? = null,
        canonicalUnit: String? = null,
        deepLink: DeepLinkTarget = DeepLinkTarget.ELEMENT_INFO,
        allowsRange: Boolean = false,
        localized: Boolean = false,
        higherIsMore: Boolean = true,
        /** What the user must own to be told this value; see [Tier]. */
        tier: Tier = Tier.FREE
    ) = FieldSpec(
        id, listOf(jsonKey), kind, category, labelRes, dimension, canonicalUnit,
        deepLink, allowsRange, localized, higherIsMore, tier = tier
    )

    /** Temperature fields that ship all three scales, so a unit request needs no conversion. */
    private fun multiUnit(id: String, jsonKeys: List<String>, @StringRes labelRes: Int) =
        FieldSpec(
            id, jsonKeys, FieldKind.NUMERIC, FieldCategory.THERMO, labelRes,
            Dimension.TEMPERATURE, "K"
        )

    /**
     * The invariant token for a reservoir measured in µg of element per litre of sea water.
     *
     * Deliberately absent from [UnitConverter]'s factor table: this is mass per *volume*, and
     * turning it into the mg/kg the other reservoirs use needs sea water's density. Leaving it
     * unconvertible makes a "in ppm" request fall back to the authored figure rather than
     * inventing a number.
     */
    const val UNIT_MICROGRAM_PER_LITRE = "µg/l"

    /**
     * The invariant token for the solar reservoirs, counted as atoms against silicon = 10⁶.
     *
     * Not a concentration at all, so it carries [Dimension.DIMENSIONLESS] and never converts.
     * Users see `ai_unit_atoms_per_si` instead of this token, which is why it can stay ASCII —
     * `UnitConverter.canonical` runs NFKC, and that folds a superscript ⁶ down to a plain 6.
     */
    const val UNIT_ATOMS_PER_SILICON = "Si=10^6"

    /**
     * An abundance reservoir.
     *
     * The nine reservoirs do **not** share a unit, which is why this takes one per field rather
     * than stamping `mg/kg` on all of them: sea water is µg/l, the sun and solar system are atom
     * counts against silicon, and the human-body figures are authored as either mg/kg or a
     * percentage. Every one of them is stored as a bare number in at least some elements, so they
     * all set [FieldSpec.surfaceUnit].
     */
    private fun abundance(
        id: String,
        jsonKey: String,
        @StringRes labelRes: Int,
        @StringRes sentenceLabelRes: Int? = null,
        dimension: Dimension = Dimension.CONCENTRATION,
        canonicalUnit: String = "mg/kg",
        @StringRes unitLabelRes: Int = R.string.ai_unit_mg_per_kg
    ) =
        FieldSpec(
            id, listOf(jsonKey), FieldKind.NUMERIC, FieldCategory.ABUNDANCE, labelRes,
            dimension, canonicalUnit, sentenceLabelRes = sentenceLabelRes,
            unitLabelRes = unitLabelRes, surfaceUnit = true
        )
}
