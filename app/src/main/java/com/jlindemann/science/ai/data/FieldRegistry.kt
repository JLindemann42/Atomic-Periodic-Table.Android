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

/** Where a citation for this field should send the user. */
enum class DeepLinkTarget {
    ELEMENT_INFO, NUCLIDE, EMISSION, CONSTANTS, DICTIONARY, EQUATIONS, POISSON, GEOLOGY,
    SOLUBILITY, ION, ELECTRODE, PH, MOLAR_MASS_CALC, UNIT_CONVERTER, IDEAL_GAS,
    REACTION_BALANCER, LEARNING_GAMES, FLASHCARDS, RESEND_QUERY
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
    val enumValues: List<String> = emptyList()
) {
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
}

/**
 * The catalogue of every field the agent can answer about.
 *
 * Two fields the legacy agent read — `element_period` and `element_group_number` — do not exist
 * in the JSON at all (0 of 118 elements), so the period and group-number branches silently
 * answered with empty strings. They are derived here from atomic number instead.
 */
object FieldRegistry {

    val ALL: List<FieldSpec> = buildList {

        // ---- Identity -------------------------------------------------------------------
        add(spec("name", "element", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.english_name_colon, localized = true))
        add(spec("symbol", "short", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.element_symbols))
        add(spec("atomic_number", "element_atomic_number", FieldKind.NUMERIC, FieldCategory.IDENTITY, R.string.atomic_number_label))
        add(spec("description", "description", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.description_colon, localized = true))
        add(spec("appearance", "element_appearance", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.appearance_colon, localized = true))
        add(spec("year_discovered", "element_year", FieldKind.NUMERIC, FieldCategory.IDENTITY, R.string.year_discovered_colon))
        add(spec("discovered_by", "element_discovered_name", FieldKind.TEXT, FieldCategory.IDENTITY, R.string.discovered_by_colon))
        add(spec("series", "element_group", FieldKind.ENUM, FieldCategory.IDENTITY, R.string.group_label, localized = true))
        add(spec("block", "element_block", FieldKind.ENUM, FieldCategory.IDENTITY, R.string.block_colon))
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
        add(spec("electron_affinity", "electron_affinity", FieldKind.NUMERIC, FieldCategory.ATOMIC, R.string.electron_affinity_colon, Dimension.ENERGY_PER_MOL, "kJ/mol"))
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
        add(spec("curie_point", "curie_point", FieldKind.NUMERIC, FieldCategory.ELECTROMAGNETIC, R.string.curie_point_colon, Dimension.TEMPERATURE, "K"))
        add(spec("neel_point", "neel_point", FieldKind.NUMERIC, FieldCategory.ELECTROMAGNETIC, R.string.neel_point_colon, Dimension.TEMPERATURE, "K"))
        add(spec("refractive_index", "refractive_index", FieldKind.NUMERIC, FieldCategory.ELECTROMAGNETIC, R.string.refractive_index_colon, Dimension.DIMENSIONLESS, allowsRange = true))

        // ---- Crystal --------------------------------------------------------------------
        add(spec("crystal_structure", "crystal_structure", FieldKind.ENUM, FieldCategory.CRYSTAL, R.string.crystal_structure))
        add(spec("lattice_constants", "lattice_constants", FieldKind.STRUCT, FieldCategory.CRYSTAL, R.string.crystal_structure, Dimension.LENGTH, "Å"))
        add(spec("space_group_name", "space_group_name", FieldKind.TEXT, FieldCategory.CRYSTAL, R.string.space_group_name_colon))
        add(spec("space_group_number", "space_group_number", FieldKind.NUMERIC, FieldCategory.CRYSTAL, R.string.space_group_number_colon))

        // ---- Mechanical -----------------------------------------------------------------
        add(spec("young_modulus", "young_modulus", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_young_modulus, Dimension.PRESSURE, "GPa", allowsRange = true))
        add(spec("bulk_modulus", "bulk_modulus", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_bulk_modulus, Dimension.PRESSURE, "GPa", allowsRange = true))
        add(spec("shear_modulus", "shear_modulus", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_shear_modulus, Dimension.PRESSURE, "GPa", allowsRange = true))
        add(spec("poisson_ratio", "poisson_ratio", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_poisson_ratio, Dimension.DIMENSIONLESS, allowsRange = true, deepLink = DeepLinkTarget.POISSON))
        add(spec("mohs_hardness", "mohs_hardness", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.mohs_hardness_colon, Dimension.DIMENSIONLESS, allowsRange = true))
        add(spec("vickers_hardness", "vickers_hardness", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.vickers_hardness_colon, Dimension.PRESSURE, "MPa", allowsRange = true))
        add(spec("brinell_hardness", "brinell_hardness", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.brinell_hardness_colon, Dimension.PRESSURE, "MPa", allowsRange = true))
        add(spec("speed_of_sound_solid", "speed_of_sound_solid", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_speed_sound, Dimension.VELOCITY, "m/s", allowsRange = true))
        add(spec("speed_of_sound_liquid", "speed_of_sound_liquid", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_speed_sound, Dimension.VELOCITY, "m/s", allowsRange = true))
        add(spec("speed_of_sound_gas", "speed_of_sound_gas", FieldKind.NUMERIC, FieldCategory.MECHANICAL, R.string.element_speed_sound, Dimension.VELOCITY, "m/s", allowsRange = true))

        // ---- Nuclear --------------------------------------------------------------------
        add(spec("radioactive", "radioactive", FieldKind.ENUM, FieldCategory.NUCLEAR, R.string.radioactive_colon))
        add(spec("neutron_cross_section", "neutron_cross_sectional", FieldKind.NUMERIC, FieldCategory.NUCLEAR, R.string.neutron_cross_sectional_colon, Dimension.AREA, "b", deepLink = DeepLinkTarget.NUCLIDE))
        add(spec("common_neutrons", "element_neutron_common", FieldKind.NUMERIC, FieldCategory.NUCLEAR, R.string.isotopes_colon, deepLink = DeepLinkTarget.NUCLIDE))

        // ---- Abundance ------------------------------------------------------------------
        add(abundance("abundance_earth_crust", "earth_crust", R.string.abundance_earth_crust))
        add(abundance("abundance_earth_soils", "earth_soils", R.string.abundance_earth_soils))
        add(abundance("abundance_urban_soils", "urban_soils", R.string.abundance_urban_soil))
        add(abundance("abundance_sea_water", "sea_water", R.string.abundance_sea_water))
        add(abundance("abundance_crustal_rocks", "crustal_rocks", R.string.abundance_crustal_rocks))
        add(abundance("abundance_sun", "sun", R.string.abundance_sun))
        add(abundance("abundance_solar_system", "solar_system", R.string.abundance_solar_system))
        add(abundance("abundance_meteorites", "meteorites", R.string.abundance_meteorites))
        add(abundance("abundance_human_body", "human_body", R.string.abundance_human_body))

        // ---- Safety (NFPA 704) ----------------------------------------------------------
        add(spec("nfpa_health", "health", FieldKind.NUMERIC, FieldCategory.SAFETY, R.string.ai_safety_health))
        add(spec("nfpa_flammability", "flammability", FieldKind.NUMERIC, FieldCategory.SAFETY, R.string.ai_safety_flammability))
        add(spec("nfpa_instability", "instability", FieldKind.NUMERIC, FieldCategory.SAFETY, R.string.ai_safety_instability))
        add(spec("nfpa_special", "special", FieldKind.TEXT, FieldCategory.SAFETY, R.string.ai_safety_health))

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
        higherIsMore: Boolean = true
    ) = FieldSpec(
        id, listOf(jsonKey), kind, category, labelRes, dimension, canonicalUnit,
        deepLink, allowsRange, localized, higherIsMore
    )

    /** Temperature fields that ship all three scales, so a unit request needs no conversion. */
    private fun multiUnit(id: String, jsonKeys: List<String>, @StringRes labelRes: Int) =
        FieldSpec(
            id, jsonKeys, FieldKind.NUMERIC, FieldCategory.THERMO, labelRes,
            Dimension.TEMPERATURE, "K"
        )

    private fun abundance(id: String, jsonKey: String, @StringRes labelRes: Int) =
        FieldSpec(
            id, listOf(jsonKey), FieldKind.NUMERIC, FieldCategory.ABUNDANCE, labelRes,
            Dimension.CONCENTRATION, "mg/kg"
        )
}
