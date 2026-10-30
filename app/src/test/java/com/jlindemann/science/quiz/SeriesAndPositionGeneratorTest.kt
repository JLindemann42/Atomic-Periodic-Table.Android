package com.jlindemann.science.quiz

import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.SeriesId
import com.jlindemann.science.ai.data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class SeriesAndPositionGeneratorTest {

    private lateinit var store: KnowledgeStore

    /**
     * Resolves series labels the way the real strings do, including the two collisions that make
     * these generators non-trivial: post-transition metals are labelled "Transition Metal", and
     * both nonmetal series plus UNKNOWN are labelled "Nonmetal".
     */
    private object Labels : StringProvider {
        override val language = "en"
        override fun array(id: Int) = emptyList<String>()
        override fun get(id: Int, vararg args: Any): String = when (id) {
            SeriesId.ALKALI_METAL.labelRes -> "Alkali Metal"
            SeriesId.ALKALINE_EARTH_METAL.labelRes -> "Alkaline Earth Metal"
            SeriesId.TRANSITION_METAL.labelRes -> "Transition Metal"
            SeriesId.METALLOID.labelRes -> "Metalloid"
            SeriesId.NOBLE_GAS.labelRes -> "Noble Gas"
            SeriesId.HALOGEN.labelRes -> "Halogen"
            SeriesId.LANTHANOID.labelRes -> "Lanthanide"
            SeriesId.ACTINIDE.labelRes -> "Actinide"
            SeriesId.OTHER_NONMETAL.labelRes -> "Nonmetal"
            else -> if (args.isEmpty()) "q:$id" else "q:$id:${args.joinToString()}"
        }
    }

    @Before
    fun setUp() {
        assumeTrue(TestAssets.available())
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable())
    }

    private fun ctx(seed: Int = 3) =
        GeneratorContext(store, null, Labels, "hard", Random(seed))

    /** The fake formats questions as "q:<id>:<element>", so the subject is the last segment. */
    private fun subjectOf(c: GeneratorContext, question: String) =
        question.substringAfterLast(":").let { name -> store.elements.first { c.name(it) == name } }

    @Test
    fun `classification never offers two options meaning the same family`() {
        val questions = ClassificationGenerator("c", 13).generate(ctx(), 40)
        assertTrue("expected a full run, got ${questions.size}", questions.size >= 30)
        for (q in questions) {
            assertEquals("duplicate family in $q", q.alternatives.size, q.alternatives.distinct().size)
            assertEquals(1, q.alternatives.count { it == q.correctAnswer })
        }
    }

    @Test
    fun `classification answers match the canonical series`() {
        val c = ctx()
        for (q in ClassificationGenerator("c", 13).generate(c, 40)) {
            val element = subjectOf(c, q.question)
            assertEquals(c.seriesLabel(element.series), q.correctAnswer)
        }
    }

    @Test
    fun `odd one out really is from a different family`() {
        val c = ctx()
        val questions = OddOneOutGenerator("o", 70).generate(c, 40)
        assertTrue("expected a full run, got ${questions.size}", questions.size >= 30)

        for (q in questions) {
            val drawn = q.alternatives.map { name -> store.elements.first { c.name(it) == name } }
            val oddLabel = c.seriesLabel(drawn.first { c.name(it) == q.correctAnswer }.series)
            val others = drawn.filter { c.name(it) != q.correctAnswer }.map { c.seriesLabel(it.series) }

            assertEquals("the three insiders must share a family", 1, others.distinct().size)
            assertTrue("odd one out shares the family it should differ from", oddLabel != others.first())
        }
    }

    @Test
    fun `odd one out never draws an unclassified element`() {
        val c = ctx()
        for (q in OddOneOutGenerator("o", 70).generate(c, 40)) {
            val drawn = q.alternatives.map { name -> store.elements.first { c.name(it) == name } }
            assertTrue(drawn.none { it.series == SeriesId.UNKNOWN })
        }
    }

    @Test
    fun `period questions answer with the real period`() {
        val c = ctx()
        for (q in PositionGenerator("p", 40, PositionAspect.PERIOD).generate(c, 30)) {
            val element = subjectOf(c, q.question)
            assertEquals(element.period.toString(), q.correctAnswer)
            assertTrue(q.alternatives.contains(q.correctAnswer))
        }
    }

    @Test
    fun `group and valence questions skip elements where the concept does not apply`() {
        val c = ctx()
        for (q in PositionGenerator("g", 50, PositionAspect.GROUP).generate(c, 30)) {
            val element = subjectOf(c, q.question)
            assertEquals(element.groupNumber?.toString(), q.correctAnswer)
        }
        for (q in PositionGenerator("v", 60, PositionAspect.VALENCE).generate(c, 20)) {
            val element = subjectOf(c, q.question)
            assertEquals(FieldRegistry.valenceElectrons(element.atomicNumber)?.toString(), q.correctAnswer)
        }
    }

    @Test
    fun `position options are distinct`() {
        val c = ctx()
        for (aspect in PositionAspect.values()) {
            for (q in PositionGenerator("k", 40, aspect).generate(c, 20)) {
                assertEquals(q.alternatives.size, q.alternatives.distinct().size)
                assertEquals(1, q.alternatives.count { it == q.correctAnswer })
            }
        }
    }
}
