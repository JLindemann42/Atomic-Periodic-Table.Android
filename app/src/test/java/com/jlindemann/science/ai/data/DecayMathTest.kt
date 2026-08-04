package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

class DecayMathTest {

    /** Carbon-14, 5730 years, as the element data authors it. */
    private val carbon14 = 5730.0 * 3.15576e7

    @Test
    fun twoHalfLivesLeaveAQuarter() {
        val elapsed = 11460.0 * 3.15576e7
        assertEquals(2.0, DecayMath.halfLives(elapsed, carbon14)!!, 1e-9)
        assertEquals(0.25, DecayMath.fractionRemaining(2.0), 1e-12)
        assertEquals(25.0, DecayMath.remaining(100.0, elapsed, carbon14)!!, 1e-6)
    }

    @Test
    fun halvingTakesExactlyOneHalfLife() {
        assertEquals(carbon14, DecayMath.elapsedFor(1.0, 0.5, carbon14)!!, 1.0)
        assertEquals(2 * carbon14, DecayMath.elapsedFor(100.0, 25.0, carbon14)!!, 1.0)
    }

    /** Asking how long until a sample grows has no answer, and ln would happily give a negative one. */
    @Test
    fun aSampleCannotGrow() {
        assertNull(DecayMath.elapsedFor(1.0, 2.0, carbon14))
        assertNull(DecayMath.elapsedFor(0.0, 1.0, carbon14))
        assertNull(DecayMath.elapsedFor(1.0, 0.0, carbon14))
    }

    @Test
    fun aNonPositiveHalfLifeHasNoArithmetic() {
        assertNull(DecayMath.halfLives(100.0, 0.0))
        assertNull(DecayMath.halfLives(100.0, -5.0))
        assertNull(DecayMath.remaining(100.0, 10.0, 0.0))
        assertNull(DecayMath.elapsedFor(1.0, 0.5, 0.0))
        assertNull(DecayMath.decayConstant(0.0))
        assertNull(DecayMath.decayConstant(Double.POSITIVE_INFINITY))
    }

    @Test
    fun theDecayConstantIsLn2OverTheHalfLife() {
        assertEquals(kotlin.math.ln(2.0) / carbon14, DecayMath.decayConstant(carbon14)!!, 1e-24)
    }

    @Test
    fun zeroElapsedLeavesTheWholeSample() {
        assertEquals(100.0, DecayMath.remaining(100.0, 0.0, carbon14)!!, 1e-12)
        assertNull(DecayMath.halfLives(-1.0, carbon14))
    }
}
