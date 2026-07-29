package com.jlindemann.science.ai.corpus

import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.FieldRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Guards the corpus against rotting.
 *
 * A corpus is only a scoreboard for what it actually covers. As fields, datasets and languages get
 * added, this is what forces the corpus to grow with them instead of quietly measuring a shrinking
 * fraction of the agent.
 */
class CorpusCoverageTest {

    private lateinit var all: List<CorpusCase>

    @Before
    fun setUp() {
        assumeTrue("corpus not reachable", QuestionCorpus.available)
        all = QuestionCorpus.allCases()
    }

    @Test
    fun idsAreUnique() {
        val duplicates = all.groupBy { it.id }.filterValues { it.size > 1 }
            .map { (id, cases) -> "$id (${cases.joinToString { it.where }})" }
        assertTrue("duplicate corpus ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun everyExpectedFieldIsARealField() {
        val known = FieldRegistry.byId.keys
        val unknown = all.flatMap { case ->
            case.expectFields
                // A banked field is addressed as `ionization_energy#3`; the bank itself is the field.
                .map { it.substringBefore('#').substringBefore('@') }
                .filterNot { it in known }
                .map { "${case.where} -> $it" }
        }
        assertTrue("corpus references unknown field ids: $unknown", unknown.isEmpty())
    }

    @Test
    fun everyExpectedIntentIsRealOrAPseudoIntent() {
        val known = Intent.values().map { it.name }.toSet() + setOf("COMPOUND", "NOSPLIT")
        val unknown = all.mapNotNull { case ->
            case.expectIntent?.takeIf { it !in known }?.let { "${case.where} -> $it" }
        }
        assertTrue("corpus references unknown intents: $unknown", unknown.isEmpty())
    }

    /**
     * Every queryable field should be asked about at least once.
     *
     * Fields with no natural question are listed explicitly rather than silently tolerated, so the
     * exemption is a decision on the record instead of a gap.
     */
    @Test
    fun everyQueryableFieldIsExercised() {
        val exercised = all.flatMap { it.expectFields }
            .map { it.substringBefore('#').substringBefore('@') }.toSet()
        val exempt = setOf(
            // Internal plumbing rather than something a user asks for.
            "name", "description", "wikilink",
            // Addressed through the `sublimation_point` multi-unit field only when data exists;
            // almost no element has it, so a question would assert NoData rather than coverage.
            "sublimation_point",
            // Answered as part of the SAFETY result shape, never as a standalone field.
            "nfpa_health", "nfpa_flammability", "nfpa_instability", "nfpa_special"
        )
        val missing = (FieldRegistry.byId.keys - exercised - exempt).sorted()
        assertTrue("fields with no corpus question: $missing", missing.isEmpty())
    }

    @Test
    fun everyDatasetIsExercised() {
        val queries = all.joinToString(" ") { it.query.lowercase() }
        // Each dataset needs at least one row whose notes name it, so the mapping is explicit.
        val noted = all.mapNotNull { it.notes.lowercase().takeIf { n -> n.isNotEmpty() } }
            .joinToString(" ")
        val datasets = DatasetIndex.build().rows.map { it.dataset }.distinct()
        val missing = datasets.filterNot { it in noted || it in queries }
        assertTrue("datasets with no corpus question: $missing", missing.isEmpty())
    }

    @Test
    fun swedishAndEnglishAreBothWellCovered() {
        val byLang = all.groupingBy { it.lang }.eachCount()
        assertTrue("too few English cases: ${byLang["en"]}", (byLang["en"] ?: 0) >= 200)
        assertTrue("too few Swedish cases: ${byLang["sv"]}", (byLang["sv"] ?: 0) >= 100)
    }

    @Test
    fun everyShippedLanguageHasAtLeastOneCase() {
        val covered = all.map { it.lang }.toSet()
        val shipped = setOf("en", "sv", "de", "fr", "es", "it", "pt", "hi", "ur", "zh", "af", "fil")
        val missing = (shipped - covered).sorted()
        assertTrue("languages with no corpus case: $missing", missing.isEmpty())
    }

    @Test
    fun mustDeferCasesExistInQuantity() {
        // Deferral is the assertion the old suite could not make, because it ran without a
        // retriever. It needs real weight behind it.
        val deferrals = all.count { it.mustDefer }
        assertTrue("too few must-defer cases: $deferrals", deferrals >= 100)
    }
}
