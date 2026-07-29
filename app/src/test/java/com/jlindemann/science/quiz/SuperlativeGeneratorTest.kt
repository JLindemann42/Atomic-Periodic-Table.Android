package com.jlindemann.science.quiz

import com.jlindemann.science.ai.core.FakeStrings
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Exercises the superlative generator against all 118 real elements.
 *
 * The property that matters is that the answer marked correct really is the extreme among the
 * options offered — a generator that quietly picked the wrong one would still look plausible on
 * screen, because every option is a real element name.
 */
class SuperlativeGeneratorTest {

    private lateinit var store: KnowledgeStore

    private val highestRes = 1
    private val lowestRes = 2

    @Before
    fun setUp() {
        assumeTrue(TestAssets.available())
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable())
    }

    private fun context(difficulty: String = "hard", seed: Int = 7) = GeneratorContext(
        store = store,
        names = null,
        strings = FakeStrings(mapOf(highestRes to "highest?", lowestRes to "lowest?")),
        difficulty = difficulty,
        random = Random(seed)
    )

    private fun fields(vararg ids: String) = ids.map { RankableField(it, highestRes, lowestRes) }

    @Test
    fun `marks the true maximum as correct`() {
        // One string id per field, so the question text says which field was drawn. Sharing an id
        // would leave the assertion guessing, and most elements have values for all four.
        val ids = listOf("density", "atomic_mass", "melting_point", "boiling_point")
        val resFor = ids.mapIndexed { i, id -> id to 100 + i }.toMap()
        val fields = ids.map { RankableField(it, resFor.getValue(it), resFor.getValue(it)) }
        val ctx = GeneratorContext(
            store = store,
            names = null,
            strings = FakeStrings(ids.associate { resFor.getValue(it) to it }),
            difficulty = "hard",
            random = Random(7)
        )
        val questions = SuperlativeGenerator("k", 90, fields, descending = true).generate(ctx, 40)

        assertTrue("expected a full run, got ${questions.size}", questions.size >= 30)
        for (q in questions) {
            val drawn = q.alternatives.map { name -> elementNamed(ctx, name) }
            val best = drawn.maxByOrNull { it.quantity(q.question)!!.mid }!!
            assertEquals("wrong winner for ${q.question}", ctx.name(best), q.correctAnswer)
        }
    }

    @Test
    fun `marks the true minimum as correct`() {
        val gen = SuperlativeGenerator("k", 90, fields("density"), descending = false)
        val ctx = context()
        for (q in gen.generate(ctx, 30)) {
            val drawn = q.alternatives.map { elementNamed(ctx, it) }
            val worst = drawn.minByOrNull { it.quantity("density")!!.mid }!!
            assertEquals(ctx.name(worst), q.correctAnswer)
        }
    }

    @Test
    fun `every question offers the correct answer exactly once`() {
        val gen = SuperlativeGenerator("k", 90, fields("density", "atomic_mass"), descending = true)
        for (q in gen.generate(context(), 40)) {
            assertEquals(4, q.alternatives.size)
            assertEquals("duplicate option in $q", 4, q.alternatives.distinct().size)
            assertEquals(1, q.alternatives.count { it == q.correctAnswer })
        }
    }

    @Test
    fun `never asks a question whose top two are within the difficulty margin`() {
        for ((difficulty, margin) in listOf("easy" to 0.25, "medium" to 0.12, "hard" to 0.05)) {
            val gen = SuperlativeGenerator("k", 90, fields("density"), descending = true)
            val ctx = context(difficulty)
            for (q in gen.generate(ctx, 30)) {
                val sorted = q.alternatives
                    .map { elementNamed(ctx, it).quantity("density")!!.mid }
                    .sortedDescending()
                val scale = max(abs(sorted[0]), abs(sorted[1]))
                assertTrue(
                    "$difficulty allowed a ${sorted[0]} vs ${sorted[1]} tie-break",
                    abs(sorted[0] - sorted[1]) / scale >= margin
                )
            }
        }
    }

    @Test
    fun `ranges are excluded so comparisons stay well defined`() {
        // mohs_hardness is authored with ranges for several elements ("1.5-2").
        val pool = context().rankableFor("mohs_hardness")
        assertTrue(pool.isNotEmpty())
        assertTrue(pool.none { it.second.isRange })
    }

    @Test
    fun `two-option mode produces pairwise questions`() {
        val gen = SuperlativeGenerator("k", 60, fields("density"), descending = true, optionCount = 2)
        val questions = gen.generate(context(), 20)
        assertTrue(questions.isNotEmpty())
        assertTrue(questions.all { it.alternatives.size == 2 })
    }

    private fun elementNamed(ctx: GeneratorContext, displayName: String) =
        store.elements.first { ctx.name(it) == displayName }
}
