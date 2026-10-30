package com.jlindemann.science.utils

import com.jlindemann.science.R

/**
 * The flashcard curriculum: the level boxes, the categories inside them, and the exam tests that
 * sit between some of the boxes.
 *
 * Both the flashcard screen (which draws the boxes) and the quiz activity (which has to know which
 * categories an exam draws from) read this, so the two cannot drift apart.
 */
object FlashcardCatalog {

    data class CategorySpec(val key: String, val labelRes: Int, val isPro: Boolean = false)

    data class LevelBoxSpec(val range: IntRange, val categories: List<CategorySpec>)

    /**
     * An exam sits directly after the level box that starts at [unlockLevel] and mixes questions
     * from every category up to and including that box.
     */
    data class ExamSpec(val key: String, val unlockLevel: Int, val isPro: Boolean)

    /** Bonus XP for finishing an exam, on top of the normal per-question and finish rewards. */
    const val EXAM_COMPLETION_XP = 75

    /** Share of correct answers needed for an exam to count as passed. */
    const val EXAM_PASS_PERCENT = 60

    val levelBoxes = listOf(
        LevelBoxSpec(0..4, listOf(
            CategorySpec("element_symbols", R.string.element_symbols),
            CategorySpec("element_names", R.string.element_names),
            CategorySpec("element_classifications", R.string.element_groups),
            CategorySpec("odd_one_out_series", R.string.game_odd_one_out),
            CategorySpec("discovered_by", R.string.discovered_by, isPro = true),
            CategorySpec("discovery_year", R.string.discovery_year, isPro = true)
        )),
        LevelBoxSpec(5..9, listOf(
            CategorySpec("appearance", R.string.appearance),
            CategorySpec("atomic_number", R.string.atomic_number),
            CategorySpec("periodic_period", R.string.game_period),
            CategorySpec("electrical_type", R.string.electrical_type, isPro = true),
            CategorySpec("radioactive", R.string.radioactive, isPro = true),
            CategorySpec("periodic_group", R.string.game_group, isPro = true)
        )),
        LevelBoxSpec(10..14, listOf(
            CategorySpec("atomic_mass", R.string.atomic_mass),
            CategorySpec("density", R.string.density),
            CategorySpec("compare_pair", R.string.game_compare_pair),
            CategorySpec("electronegativity", R.string.electronegativity, isPro = true),
            CategorySpec("block", R.string.block, isPro = true),
            CategorySpec("valence_electrons", R.string.game_valence_electrons, isPro = true)
        )),
        LevelBoxSpec(15..19, listOf(
            CategorySpec("magnetic_type", R.string.magnetic_type),
            CategorySpec("phase_stp", R.string.phase_stp),
            CategorySpec("crystal_structure", R.string.crystal_structure, isPro = true),
            CategorySpec("superconducting_point", R.string.superconducting_point, isPro = true)
        )),
        LevelBoxSpec(20..24, listOf(
            CategorySpec("neutron_cross_sectional", R.string.neutron_cross_sectional),
            CategorySpec("specific_heat_capacity", R.string.specific_heat_capacity),
            CategorySpec("molar_mass", R.string.game_molar_mass),
            CategorySpec("mohs_hardness", R.string.mohs_hardness, isPro = true),
            CategorySpec("vickers_hardness", R.string.vickers_hardness, isPro = true),
            CategorySpec("brinell_hardness", R.string.brinell_hardness, isPro = true)
        )),
        LevelBoxSpec(25..29, listOf(
            CategorySpec("element_boiling_celsius", R.string.boiling_point_celsius),
            CategorySpec("element_boiling_fahrenheit", R.string.boiling_point_fahrenheit),
            CategorySpec("element_boiling_kelvin", R.string.boiling_point_kelvin),
            CategorySpec("isotope_neutrons", R.string.game_isotope_neutrons),
            CategorySpec("element_melting_celsius", R.string.melting_point_celsius, isPro = true),
            CategorySpec("element_melting_fahrenheit", R.string.melting_point_fahrenheit, isPro = true),
            CategorySpec("element_melting_kelvin", R.string.melting_point_kelvin, isPro = true)
        )),
        LevelBoxSpec(30..34, listOf(
            CategorySpec("earth_crust", R.string.abundance_earth_crust),
            CategorySpec("earth_soils", R.string.abundance_earth_soils, isPro = true)
        )),
        LevelBoxSpec(35..39, listOf(
            CategorySpec("nfpa_health", R.string.hazard_health),
            CategorySpec("nfpa_flammability", R.string.hazard_flammability),
            CategorySpec("nfpa_instability", R.string.hazard_instability, isPro = true),
            CategorySpec("nfpa_diamond", R.string.hazard_diamond, isPro = true)
        )),
        // Comparison games. These rank elements against each other rather than asking for one
        // element's value, so they sit above the single-property boxes.
        LevelBoxSpec(40..44, listOf(
            CategorySpec("superlative_highest", R.string.game_highest),
            CategorySpec("superlative_lowest", R.string.game_lowest, isPro = true),
            // Ranks Tier.PRO fields, so this must stay PRO+ or the quiz leaks gated data.
            CategorySpec("superlative_advanced", R.string.game_extremes_advanced, isPro = true)
        )),
        LevelBoxSpec(45..49, listOf(
            CategorySpec("isotope_stability", R.string.game_isotope_stability),
            CategorySpec("isotope_half_life", R.string.game_isotope_half_life, isPro = true),
            CategorySpec("isotope_decay", R.string.game_isotope_decay, isPro = true)
        ))
    )

    val exams = listOf(
        // All exams are PRO+: every one of them mixes in categories that are PRO+ on their own, so
        // a free exam would have handed out paid content.
        ExamSpec("exam_5", 5, isPro = true),
        ExamSpec("exam_15", 15, isPro = true),
        ExamSpec("exam_25", 25, isPro = true),
        ExamSpec("exam_35", 35, isPro = true),
        ExamSpec("exam_45", 45, isPro = true)
    )

    /** The exam drawn below the box covering [range], if there is one. */
    fun examAfterBox(range: IntRange): ExamSpec? = exams.firstOrNull { it.unlockLevel == range.first }

    fun examFor(key: String): ExamSpec? = exams.firstOrNull { it.key == key }

    fun isExam(key: String): Boolean = examFor(key) != null

    private fun boxesCoveredBy(exam: ExamSpec): List<LevelBoxSpec> =
        levelBoxes.filter { it.range.first <= exam.unlockLevel }

    /** Every category the exam can ask about: all boxes up to and including the one above it. */
    fun categoriesForExam(exam: ExamSpec): List<CategorySpec> = boxesCoveredBy(exam).flatMap { it.categories }

    /** Highest level the exam covers, for the "Levels 0-9" subtitle. */
    fun topLevelForExam(exam: ExamSpec): Int = boxesCoveredBy(exam).maxOf { it.range.last }

    /**
     * How many questions an exam asks. Longer than a single-category game, since it covers more.
     *
     * Scaled by breadth: the last exam sweeps roughly four times as many categories as the first,
     * and at a flat length it would sample barely half of what it claims to cover.
     */
    fun examQuestionCount(exam: ExamSpec, difficulty: String): Int {
        val base = when (difficulty) {
            "medium" -> 20
            "hard" -> 30
            else -> 12
        }
        val breadth = categoriesForExam(exam).size
        val smallest = categoriesForExam(exams.first()).size
        return base + base * (breadth - smallest).coerceAtLeast(0) / 60
    }
}
