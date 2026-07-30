package com.jlindemann.science.quiz

import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ChemistryMath
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class IsotopeAndMolarMassGeneratorTest {

    private lateinit var store: KnowledgeStore

    /** Echoes decay labels back as the enum name so assertions can identify them. */
    private object Strings : StringProvider {
        override val language = "en"
        override fun array(id: Int) = emptyList<String>()
        override fun get(id: Int, vararg args: Any): String {
            DecayMode.values().firstOrNull { it.labelRes == id }?.let { return it.name }
            return if (args.isEmpty()) "q:$id" else "${args.joinToString()}"
        }
    }

    @Before
    fun setUp() {
        assumeTrue(TestAssets.available())
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable())
    }

    private fun ctx(seed: Int = 11) = GeneratorContext(store, null, Strings, "hard", Random(seed))

    /**
     * Resolves the symbol-mass label the generators show back to its record, mirroring the pool
     * rule. The authored `iso_N` names cannot be used here: 571 of them are the full English
     * element name and ten are duplicated across elements.
     */
    private fun isotope(label: String) =
        store.elements.flatMap { element ->
            element.isotopes.mapNotNull { iso ->
                if (iso.protons == element.atomicNumber && iso.nucleons != null &&
                    iso.protons?.plus(iso.neutrons ?: -1) == iso.nucleons
                ) "${element.symbol}-${iso.nucleons}" to iso else null
            }
        }.first { it.first == label }.second

    @Test
    fun `neutron counts match the source nuclide`() {
        val questions = IsotopeNeutronGenerator("n", 75).generate(ctx(), 40)
        assertTrue("expected a full run, got ${questions.size}", questions.size >= 30)
        for (q in questions) {
            val iso = isotope(q.question)
            assertEquals(iso.neutrons?.toString(), q.correctAnswer)
            assertEquals(4, q.alternatives.distinct().size)
            assertEquals(1, q.alternatives.count { it == q.correctAnswer })
        }
    }

    @Test
    fun `stability questions offer exactly one stable nuclide`() {
        val questions = IsotopeStabilityGenerator("s", 100).generate(ctx(), 30)
        assertTrue(questions.isNotEmpty())
        for (q in questions) {
            val drawn = q.alternatives.map { isotope(it) }
            assertEquals("more than one stable option in $q", 1, drawn.count { it.stable })
            assertTrue(isotope(q.correctAnswer).stable)
        }
    }

    @Test
    fun `half-life questions pick the true extreme and keep a fair gap`() {
        val questions = IsotopeHalfLifeGenerator("h", 110).generate(ctx(), 30)
        assertTrue(questions.isNotEmpty())
        for (q in questions) {
            val drawn = q.alternatives.map { isotope(it) }
            val lives = drawn.map { it.halfLifeSeconds!! }.sortedDescending()
            val winner = isotope(q.correctAnswer).halfLifeSeconds!!
            // The winner is at one end or the other, depending on which way the question was asked.
            assertTrue(winner == lives.first() || winner == lives.last())
            val ratio = if (winner == lives.first()) lives[0] / lives[1]
            else lives[lives.size - 2] / lives[lives.size - 1]
            assertTrue("half-lives too close in $q", ratio >= 2.0)
        }
    }

    @Test
    fun `decay questions never offer beta-plus against electron capture`() {
        val questions = IsotopeDecayGenerator("d", 110).generate(ctx(), 40)
        assertTrue("expected a full run, got ${questions.size}", questions.size >= 30)
        for (q in questions) {
            val modes = q.alternatives.map { DecayMode.valueOf(it) }
            assertEquals(4, modes.distinct().size)
            assertTrue(
                "confusable pair offered together in $q",
                !(DecayMode.BETA_PLUS in modes && DecayMode.ELECTRON_CAPTURE in modes)
            )
            assertEquals(DecayMode.of(isotope(q.question).decayType), DecayMode.valueOf(q.correctAnswer))
        }
    }

    @Test
    fun `decay canonicalisation folds every spelling in the corpus`() {
        val raw = store.elements.flatMap { it.isotopes }.mapNotNull { it.decayType }.distinct()
        // Everything either maps to a mode, is a stability marker, or is a multi-particle variant.
        val unmapped = raw.filter { DecayMode.of(it) == null }
        assertTrue(
            "unexpected decay spellings: $unmapped",
            unmapped.all { it.lowercase().contains("stable") || it.matches(Regex("\\d.*")) }
        )
    }

    @Test
    fun `every authored formula parses and weighs something`() {
        val atomicMass = { symbol: String -> store.bySymbol(symbol)?.quantity("atomic_mass")?.value }
        for (formula in Compounds.FORMULAS) {
            val result = ChemistryMath.parseFormula(formula, atomicMass)
            assertNotNull("could not parse $formula", result)
            assertTrue("$formula weighed ${result!!.molarMass}", result.molarMass > 0.0)
        }
    }

    @Test
    fun `molar mass answers match a recomputed parse`() {
        val atomicMass = { symbol: String -> store.bySymbol(symbol)?.quantity("atomic_mass")?.value }
        val questions = MolarMassGenerator("m", 60).generate(ctx(), 30)
        assertTrue("expected a full run, got ${questions.size}", questions.size >= 25)
        for (q in questions) {
            val expected = ChemistryMath.parseFormula(q.question, atomicMass)!!.molarMass
            val answered = q.correctAnswer.removeSuffix(" g/mol").replace(',', '.').toDouble()
            assertEquals("wrong mass for ${q.question}", expected, answered, 0.01)
            assertEquals(4, q.alternatives.distinct().size)
        }
    }
}
