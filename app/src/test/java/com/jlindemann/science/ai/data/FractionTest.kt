package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

class FractionTest {

    @Test
    fun fractionsAreHeldInLowestTermsSoEqualityIsStructural() {
        assertEquals(Fraction.of(1, 2), Fraction.of(2, 4))
        assertEquals(Fraction.of(1, 2), Fraction.of(50, 100))
        assertEquals(Fraction.of(1, 2)!!.hashCode(), Fraction.of(3, 6)!!.hashCode())
    }

    @Test
    fun theSignLivesOnTheNumeratorSoTwoSpellingsOfMinusHalfAgree() {
        val a = Fraction.of(-1, 2)!!
        val b = Fraction.of(1, -2)!!
        assertEquals(a, b)
        assertEquals(-1L, a.num)
        assertEquals(2L, a.den)
        assertTrue(a.isNegative)
    }

    /** The whole reason this type exists: a double would leave 0.49999999999999994 here. */
    @Test
    fun additionIsExact() {
        val third = Fraction.of(1, 3)!!
        val sixth = Fraction.of(1, 6)!!
        assertEquals(Fraction.of(1, 2), (third + sixth))
        assertEquals(Fraction.ZERO, (third - third))
    }

    @Test
    fun multiplicationAndDivisionReduceAsTheyGo() {
        assertEquals(Fraction.of(1, 6), Fraction.of(1, 2)!! * Fraction.of(1, 3)!!)
        assertEquals(Fraction.of(3, 2), Fraction.of(1, 2)!! / Fraction.of(1, 3)!!)
        assertNull(Fraction.of(1, 2)!! / Fraction.ZERO)
    }

    @Test
    fun aZeroDenominatorIsNotAFraction() {
        assertNull(Fraction.of(1, 0))
    }

    @Test
    fun comparisonOrdersByValueNotByNumerator() {
        assertTrue(Fraction.of(1, 3)!! < Fraction.of(1, 2)!!)
        assertTrue(Fraction.of(-5, 1)!! < Fraction.ZERO)
        assertEquals(0, Fraction.of(2, 4)!!.compareTo(Fraction.of(1, 2)!!))
    }

    @Test
    fun gcdAndLcmAgreeWithTheirDefinitions() {
        assertEquals(6L, Fraction.gcd(12, 18))
        assertEquals(7L, Fraction.gcd(7, 0))
        assertEquals(36L, Fraction.lcm(12, 18))
        assertEquals(0L, Fraction.lcm(0, 5))
    }

    /**
     * Overflow must surface as null. A wrapped numerator would balance an equation with confident,
     * silently wrong coefficients — the one outcome the balancer must never produce.
     */
    @Test
    fun overflowReturnsNullRatherThanWrapping() {
        val big = Fraction.of(Long.MAX_VALUE / 2, 1)!!
        assertNull((big + big)!! + big)
        assertNull(big * big)
        // Consecutive integers are coprime, so the lcm is their product and cannot fit.
        assertNull(Fraction.lcm(Long.MAX_VALUE, Long.MAX_VALUE - 1))
        assertNull(Fraction.of(Long.MIN_VALUE, 1))
    }

    @Test
    fun zeroAndOneBehave() {
        assertTrue(Fraction.ZERO.isZero)
        assertFalse(Fraction.ONE.isZero)
        assertTrue(Fraction.ONE.isPositive)
        assertEquals(Fraction.ONE, Fraction.of(5, 5))
        assertEquals(Fraction.ZERO, Fraction.of(0, 7))
        assertEquals("1/2", Fraction.of(1, 2).toString())
        assertEquals("3", Fraction.of(6, 2).toString())
    }
}
