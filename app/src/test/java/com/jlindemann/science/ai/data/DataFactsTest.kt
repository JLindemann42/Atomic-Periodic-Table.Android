package com.jlindemann.science.ai.data

import com.jlindemann.science.ai.chat.ChatFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Facts read off the data for the widgets and the chat (roadmap 2.7). */
class DataFactsTest {

    @Before
    fun setUp() {
        assumeTrue(ChatFixture.available)
    }

    private val store get() = ChatFixture.service.store
    private val facts get() = DataFacts(store)

    @Test
    fun everyFieldHasATopAndABottom() {
        val extremes = facts.extremes()
        assertEquals(DataFacts.FIELDS.size * 2, extremes.size)
        assertTrue(extremes.all { it.rank == 1 && it.of >= 20 })
    }

    /** Seaborgium's predicted 35 g/cm³ would make it the densest; past fermium is left out. */
    @Test
    fun theExtremesAreTheKnownOnes() {
        val byField = facts.extremes().groupBy { it.fieldId }
        fun top(field: String) = byField.getValue(field).single { it.highest }.elementKey
        fun bottom(field: String) = byField.getValue(field).single { !it.highest }.elementKey
        assertEquals("osmium", top("density"))
        assertEquals("hydrogen", bottom("density"))
        assertEquals("tungsten", top("melting_point"))
        assertEquals("fluorine", top("electronegativity"))
        assertEquals("caesium", bottom("electronegativity"))
        assertEquals("helium", top("ionization_energy"))
        assertEquals("silver", top("thermal_conductivity"))
        assertFalse(facts.extremes().any { (store.element(it.elementKey)?.atomicNumber ?: 0) > DataFacts.LAST_MEASURED })
    }

    @Test
    fun anElementsFactIsItsBestPlace() {
        val osmium = facts.forElement("osmium")
        assertNotNull(osmium)
        assertEquals("density", osmium!!.fieldId)
        assertEquals(1, osmium.rank)
        // An element near neither end of any field has no fact.
        assertNull(facts.forElement("seaborgium"))
    }

    @Test
    fun theSentenceReadsInEachLanguage() {
        val osmium = facts.extremes().first { it.fieldId == "density" && it.highest }
        val en = facts.render(osmium, ChatFixture.strings("en"), ChatFixture.service.localized("en"))
        assertTrue(en, en.startsWith("**Osmium** has the highest density of the ${osmium.of} elements"))
        assertTrue(en, en.contains("22.5"))
        val sv = facts.render(osmium, ChatFixture.strings("sv"), ChatFixture.service.localized("sv"))
        assertTrue(sv, sv.startsWith("**Osmium** har högst"))
        assertFalse(sv, sv.contains("str:"))
    }

    @Test
    fun theDayCyclesThroughEveryFact() {
        val all = facts.extremes()
        assertEquals(all.toSet(), (0 until all.size).map { facts.forDay(it) }.toSet())
        assertEquals(facts.forDay(0), facts.forDay(all.size))
    }

    @Test
    fun theChatsFunFactsIncludeDataFacts() {
        val asked = (0 until 40).map { ChatFixture.chat(it).ask("tell me a fun fact").text }
        assertTrue(asked.any { it.contains("elements with a recorded value") })
    }
}
