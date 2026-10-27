package com.jlindemann.science.ai.nlu

import org.junit.Assert.assertEquals
import org.junit.Test

/** The speller's rules (roadmap 1.6), on a vocabulary small enough to read. Windows' `SpellerTest` mirrors these. */
class SpellerTest {

    private val speller = Speller(
        mapOf(
            "density" to setOf("field:density"),
            "gold" to setOf("element:gold"), "goal" to setOf("element:other"),
            "plomb" to setOf("element:lead"), "plomo" to setOf("element:lead"),
            "atommassa" to setOf("field:atomic_mass"),
            "bore" to setOf("element:boron")
        ),
        known = setOf("more")
    )

    @Test
    fun aTranspositionIsOneEdit() {
        assertEquals("what is the density of gold", speller.correct("what is the denisty of gold"))
        assertEquals(1, Speller.distance("denisty", "density", 2))
    }

    @Test
    fun aLongWordMayBeTwoEditsAway() =
        assertEquals("vad är atommassa för silver", speller.correct("vad är atommasan för silver"))

    @Test
    fun twoSpellingsOfOneThingAreNoAmbiguity() =
        assertEquals("du plomb", speller.correct("du plomp"))

    @Test
    fun aWordBetweenTwoMeaningsIsLeftAlone() =
        assertEquals("goad", speller.correct("goad"))

    @Test
    fun protectedShortAndForeignWordsAreLeftAlone() {
        assertEquals("more", speller.correct("more"))
        assertEquals("gol", speller.correct("gol"))
        assertEquals("金的密度", speller.correct("金的密度"))
        assertEquals("density of gold", speller.correct("density of gold"))
    }
}
