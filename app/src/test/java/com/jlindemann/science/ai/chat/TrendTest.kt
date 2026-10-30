package com.jlindemann.science.ai.chat

import com.jlindemann.science.ai.cards.ChatCardKind
import com.jlindemann.science.ai.cards.HeatMapReducer
import com.jlindemann.science.ai.cards.TrendSeriesReducer
import com.jlindemann.science.ai.core.Filter
import com.jlindemann.science.ai.core.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** One field across a set of elements, or the whole table (roadmap 2.3). */
class TrendTest {

    @Before
    fun setUp() {
        assumeTrue(ChatFixture.available)
    }

    @Test
    fun aPeriodIsALineInAtomicNumberOrder() {
        val reply = ChatFixture.chat().ask("electronegativity across period 3")
        assertEquals(Intent.TREND, reply.plan?.intent)
        assertEquals(listOf(Filter.InPeriod(3)), reply.plan?.filters)
        assertTrue(reply.text, reply.text.contains("Na 0.93 · Mg 1.31"))
        assertTrue(reply.text, reply.text.contains("It rises overall, from Sodium (0.93) to Chlorine (3.16)."))
        assertEquals(ChatCardKind.TREND_LINE, reply.card?.kind)
    }

    @Test
    fun aGroupReadsDownwards() {
        val reply = ChatFixture.chat().ask("how does atomic radius change down group 1")
        assertEquals(Intent.TREND, reply.plan?.intent)
        assertEquals(ChatCardKind.TREND_LINE, reply.card?.kind)
    }

    @Test
    fun aSeriesIsALineToo() {
        val reply = ChatFixture.chat().ask("show the electronegativity trend of the halogens")
        assertEquals(Intent.TREND, reply.plan?.intent)
        assertTrue(reply.text, reply.text.contains("It falls overall"))
    }

    @Test
    fun theWholeTableIsAMap() {
        val reply = ChatFixture.chat().ask("heat map of density")
        assertEquals(Intent.TREND, reply.plan?.intent)
        assertEquals(ChatCardKind.HEAT_MAP, reply.card?.kind)
        assertTrue(reply.text, reply.text.contains("Highest: ") && reply.text.contains("Lowest: Hydrogen"))
    }

    @Test
    fun aRankingInAPeriodIsStillARanking() {
        val reply = ChatFixture.chat().ask("which element in period 3 has the highest electronegativity")
        assertNotEquals(Intent.TREND, reply.plan?.intent)
    }

    @Test
    fun theCardsDrawWhatTheAnswerStates() {
        val reply = ChatFixture.chat().ask("electronegativity across period 3")
        val series = assertNotNull(TrendSeriesReducer.of(reply.card!!, ChatFixture.service.store))
        assertEquals(listOf("Na", "Mg", "Al", "Si", "P", "S", "Cl"), series.points.map { it.symbol })
        val map = assertNotNull(HeatMapReducer.of("density", ChatFixture.service.store))
        // The element with the largest value is the darkest cell, whichever it is in the data.
        assertEquals(1.0, map.cells.filter { it.value != null }.maxBy { it.value!! }.fraction!!, 1e-9)
        assertEquals(6 to 8, map.cells.first { it.symbol == "Os" }.let { it.row to it.column })
        assertEquals(8 to 3, map.cells.first { it.symbol == "La" }.let { it.row to it.column })
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
