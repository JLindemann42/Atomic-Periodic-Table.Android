package com.jlindemann.science.quiz

import com.jlindemann.science.utils.FlashcardCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the join between the curriculum and the generators.
 *
 * A category key that exists in one but not the other fails silently at runtime: an unregistered
 * key falls through to the legacy `when`, whose `else` branch quietly serves element-symbol
 * questions, so a typo would ship as "the game works but asks the wrong thing".
 */
class CurriculumWiringTest {

    private val catalogKeys =
        FlashcardCatalog.levelBoxes.flatMap { it.categories }.map { it.key }

    @Test
    fun `every generator is reachable from a level box`() {
        val unreachable = QuestionGenerators.keys - catalogKeys.toSet()
        assertTrue("generators no level box offers: $unreachable", unreachable.isEmpty())
    }

    @Test
    fun `category keys are unique across the curriculum`() {
        assertEquals(catalogKeys.size, catalogKeys.distinct().size)
    }

    @Test
    fun `categories ranking PRO fields stay PRO+`() {
        val specs = FlashcardCatalog.levelBoxes.flatMap { it.categories }
        for (key in QuestionGenerators.proFieldKeys) {
            val spec = specs.first { it.key == key }
            assertTrue("$key ranks Tier.PRO data and must be gated", spec.isPro)
        }
    }

    @Test
    fun `every exam covers the boxes above it and nothing below`() {
        for (exam in FlashcardCatalog.exams) {
            val covered = FlashcardCatalog.categoriesForExam(exam).map { it.key }
            assertEquals("an exam must not repeat a category", covered.size, covered.distinct().size)
            assertTrue(covered.isNotEmpty())
            // The box the exam sits under is included; the one after it is not.
            val nextBox = FlashcardCatalog.levelBoxes.firstOrNull { it.range.first > exam.unlockLevel }
            if (nextBox != null) {
                assertTrue(
                    "exam ${exam.key} leaked a category from level ${nextBox.range.first}",
                    nextBox.categories.none { it.key in covered }
                )
            }
        }
    }

    @Test
    fun `exam length grows with the ground it covers`() {
        val lengths = FlashcardCatalog.exams.map {
            FlashcardCatalog.examQuestionCount(it, "hard")
        }
        assertEquals(lengths.sorted(), lengths)
        assertTrue("the first exam should keep its original length", lengths.first() == 30)
        assertTrue("exams should not become unplayably long", lengths.last() <= 60)
    }

    @Test
    fun `every level box offers at least one free category`() {
        for (box in FlashcardCatalog.levelBoxes) {
            assertTrue(
                "level ${box.range.first} is entirely PRO+",
                box.categories.any { !it.isPro }
            )
        }
    }
}
