package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

class SolutionMathTest {

    private val nacl = 58.44

    @Test
    fun completesTheThirdQuantityFromAnyTwo() {
        val (moles, molarity, litres) = SolutionMath.complete(null, 0.5, 0.250)!!
        assertEquals(0.125, moles, 1e-9)
        assertEquals(0.5, molarity, 1e-9)
        assertEquals(0.250, litres, 1e-9)

        assertEquals(0.5, SolutionMath.complete(0.125, null, 0.250)!!.second, 1e-9)
        assertEquals(0.250, SolutionMath.complete(0.125, 0.5, null)!!.third, 1e-9)
    }

    /** One known value leaves a family of answers, not an answer. */
    @Test
    fun fewerThanTwoKnownsHasNoAnswer() {
        assertNull(SolutionMath.complete(0.125, null, null))
        assertNull(SolutionMath.complete(null, null, null))
    }

    @Test
    fun nonPositiveInputsAreRefused() {
        assertNull(SolutionMath.complete(null, 0.5, 0.0))
        assertNull(SolutionMath.complete(null, -0.5, 0.250))
        assertNull(SolutionMath.complete(null, 0.5, Double.NaN))
    }

    @Test
    fun massFollowsFromMolesAndMolarMass() {
        assertEquals(7.305, SolutionMath.massOf(0.125, nacl)!!, 1e-3)
        assertEquals(0.125, SolutionMath.molesOf(7.305, nacl)!!, 1e-4)
        assertNull(SolutionMath.massOf(0.125, 0.0))
    }

    @Test
    fun dilutionSolvesForTheOneMissingValue() {
        val d = SolutionMath.dilution(c1 = 2.0, v1 = 0.050, c2 = 0.5, v2 = null)!!
        assertEquals(0.200, d.v2, 1e-9)
        assertEquals(2.0 * 0.050, d.c2 * d.v2, 1e-9)

        assertEquals(0.050, SolutionMath.dilution(2.0, null, 0.5, 0.200)!!.v1, 1e-9)
        assertEquals(0.5, SolutionMath.dilution(2.0, 0.050, null, 0.200)!!.c2, 1e-9)
        assertEquals(2.0, SolutionMath.dilution(null, 0.050, 0.5, 0.200)!!.c1, 1e-9)
    }

    /** Exactly three knowns: four is not a question, two is not an equation. */
    @Test
    fun dilutionNeedsExactlyThreeKnowns() {
        assertNull(SolutionMath.dilution(2.0, 0.050, 0.5, 0.200))
        assertNull(SolutionMath.dilution(2.0, 0.050, null, null))
        assertNull(SolutionMath.dilution(null, null, null, null))
    }

    /** A negative answer means "concentrate this", which is a real answer rather than an error. */
    @Test
    fun solventToAddCanBeNegative() {
        assertEquals(0.150, SolutionMath.solventToAdd(0.050, 0.200), 1e-9)
        assertTrue(SolutionMath.solventToAdd(0.200, 0.050) < 0)
    }
}
