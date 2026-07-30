package com.jlindemann.science.quiz

import com.jlindemann.science.R

/**
 * The categories whose questions come from a generator rather than the field-lookup `when` in
 * `LearningGamesActivity`. Anything not registered here still takes the legacy path.
 */
object QuestionGenerators {

    /**
     * Free-tier numeric fields with coverage above ~85 of 118 elements, where "which is highest"
     * is a real question.
     *
     * Excluded on purpose: `atomic_number`, `protons`, `electrons`, `period`, `group_number`,
     * `year_discovered` and `valence_electrons` — ordering them is either trivial from the
     * periodic table or meaningless. This mirrors `QueryExecutor`'s UNRANKABLE_FIELDS.
     */
    private val BASIC_FIELDS = listOf(
        RankableField("atomic_mass", R.string.question_highest_atomic_mass, R.string.question_lowest_atomic_mass),
        RankableField("density", R.string.question_highest_density, R.string.question_lowest_density),
        RankableField("melting_point", R.string.question_highest_melting_point, R.string.question_lowest_melting_point),
        RankableField("boiling_point", R.string.question_highest_boiling_point, R.string.question_lowest_boiling_point),
        RankableField("electronegativity", R.string.question_highest_electronegativity, R.string.question_lowest_electronegativity),
        RankableField("fusion_heat", R.string.question_highest_fusion_heat, R.string.question_lowest_fusion_heat),
        RankableField("vaporization_heat", R.string.question_highest_vaporization_heat, R.string.question_lowest_vaporization_heat),
        RankableField("thermal_conductivity", R.string.question_highest_thermal_conductivity, R.string.question_lowest_thermal_conductivity),
        RankableField("molar_volume", R.string.question_largest_molar_volume, R.string.question_smallest_molar_volume),
        RankableField("atomic_radius", R.string.question_largest_atomic_radius, R.string.question_smallest_atomic_radius)
    )

    /**
     * `Tier.PRO` fields. Categories built on these must stay `isPro` in the catalogue, otherwise
     * the quiz hands out data the AI engine gates.
     */
    private val ADVANCED_FIELDS = listOf(
        RankableField("mohs_hardness", R.string.question_highest_mohs_hardness, R.string.question_lowest_mohs_hardness),
        RankableField("vickers_hardness", R.string.question_highest_vickers_hardness, R.string.question_lowest_vickers_hardness),
        RankableField("brinell_hardness", R.string.question_highest_brinell_hardness, R.string.question_lowest_brinell_hardness),
        RankableField("young_modulus", R.string.question_highest_young_modulus, R.string.question_lowest_young_modulus),
        RankableField("bulk_modulus", R.string.question_highest_bulk_modulus, R.string.question_lowest_bulk_modulus),
        RankableField("shear_modulus", R.string.question_highest_shear_modulus, R.string.question_lowest_shear_modulus),
        RankableField("speed_of_sound_solid", R.string.question_highest_speed_of_sound, R.string.question_lowest_speed_of_sound)
    )

    private val all: List<QuestionGenerator> = listOf(
        SuperlativeGenerator("superlative_highest", baseXp = 90, fields = BASIC_FIELDS, descending = true),
        SuperlativeGenerator("superlative_lowest", baseXp = 90, fields = BASIC_FIELDS, descending = false),
        SuperlativeGenerator("superlative_advanced", baseXp = 110, fields = ADVANCED_FIELDS, descending = null),
        // Two options rather than four, and either direction, so it plays as a head-to-head.
        SuperlativeGenerator("compare_pair", baseXp = 60, fields = BASIC_FIELDS, descending = null, optionCount = 2),
        OddOneOutGenerator("odd_one_out_series", baseXp = 20),
        // Takes over the existing category: the raw JSON spells several classifications two ways,
        // which the old lookup offered as separate answers.
        ClassificationGenerator("element_classifications", baseXp = 13),
        PositionGenerator("periodic_period", baseXp = 40, aspect = PositionAspect.PERIOD),
        PositionGenerator("periodic_group", baseXp = 50, aspect = PositionAspect.GROUP),
        PositionGenerator("valence_electrons", baseXp = 60, aspect = PositionAspect.VALENCE),
        // XP for these two sits in the band their box already uses rather than the end-game rate,
        // because they are placed mid-curriculum.
        MolarMassGenerator("molar_mass", baseXp = 60),
        IsotopeNeutronGenerator("isotope_neutrons", baseXp = 75),
        IsotopeStabilityGenerator("isotope_stability", baseXp = 100),
        IsotopeHalfLifeGenerator("isotope_half_life", baseXp = 110),
        IsotopeDecayGenerator("isotope_decay", baseXp = 110)
    )

    private val byKey: Map<String, QuestionGenerator> = all.associateBy { it.categoryKey }

    /** Category keys served by a generator. Used to check the curriculum wiring. */
    val keys: Set<String> get() = byKey.keys

    /** Categories that rank `Tier.PRO` fields and so must stay PRO+ in the curriculum. */
    val proFieldKeys: Set<String> = setOf("superlative_advanced")

    fun forCategory(key: String): QuestionGenerator? = byKey[key]
}
