package com.jlindemann.science.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The electrochemical series (roadmap 0.8). Windows' `SeriesModelTest` asserts the same things
 * against the same 33 couples.
 */
class SeriesModelTest {

    private val series = ArrayList<Series>().also { SeriesModel.getList(it) }

    @Test fun thereAre33Couples() = assertEquals(33, series.size)

    /** The series is read top to bottom; erbium at -3.0 above lithium at -3.04 was out of order. */
    @Test fun itIsStrictlyMostNegativeFirst() {
        for ((above, below) in series.zipWithNext()) {
            assertTrue("${above.nameKey} ${above.voltage} sits above ${below.nameKey} ${below.voltage}",
                above.voltage < below.voltage)
        }
    }

    /**
     * Five couples were wrong in both apps. Copper's -0.159 V would make it more reactive than tin
     * and lead; the four lanthanide and actinide values were up to 1.7 V too negative.
     */
    @Test fun theCorrectedCouplesHoldTheHandbookValues() {
        val volts = series.associate { it.nameKey to it.voltage }
        assertEquals(0.3419, volts.getValue("copper"), 1e-9)
        assertEquals(-1.899, volts.getValue("thorium"), 1e-9)
        assertEquals(-2.353, volts.getValue("praseodymium"), 1e-9)
        assertEquals(-2.331, volts.getValue("erbium"), 1e-9)
        assertEquals(-2.379, volts.getValue("lanthanum"), 1e-9)
    }

    /** Copper sits above hydrogen: it does not displace hydrogen from dilute acid. */
    @Test fun copperIsMoreNobleThanHydrogen() {
        val volts = series.associate { it.nameKey to it.voltage }
        assertTrue(volts.getValue("copper") > 0)
        assertTrue(volts.getValue("lead") < 0)
    }
}
