package com.jlindemann.science.ai.data

/**
 * Exact rational arithmetic, for the equation balancer.
 *
 * Doubles cannot do this job. Gaussian elimination over a stoichiometry matrix produces
 * denominators like 1/3 and 2/7 constantly, and a 1e-12 residue left by the division is
 * indistinguishable from a genuine zero pivot — which is the difference between "this equation
 * balances" and "it has no solution". An epsilon threshold picked to tell them apart is a guess
 * that will be wrong for some equation, so there is no epsilon here at all.
 *
 * Always held in lowest terms with a positive denominator, so equality is structural and
 * `Fraction.of(2, 4) == Fraction.of(1, 2)`.
 *
 * Overflow returns null rather than wrapping. A wrapped numerator would balance an equation with
 * confident, silently wrong coefficients; the caller aborts instead.
 */
class Fraction private constructor(val num: Long, val den: Long) : Comparable<Fraction> {

    val isZero: Boolean get() = num == 0L
    val isPositive: Boolean get() = num > 0L
    val isNegative: Boolean get() = num < 0L

    operator fun plus(other: Fraction): Fraction? = runCatching {
        of(
            Math.addExact(Math.multiplyExact(num, other.den), Math.multiplyExact(other.num, den)),
            Math.multiplyExact(den, other.den)
        )
    }.getOrNull()

    operator fun minus(other: Fraction): Fraction? = this + (-other)

    operator fun times(other: Fraction): Fraction? = runCatching {
        of(Math.multiplyExact(num, other.num), Math.multiplyExact(den, other.den))
    }.getOrNull()

    /** @return null when [other] is zero, or when the result overflows. */
    operator fun div(other: Fraction): Fraction? {
        if (other.isZero) return null
        return runCatching {
            of(Math.multiplyExact(num, other.den), Math.multiplyExact(den, other.num))
        }.getOrNull()
    }

    operator fun unaryMinus(): Fraction = Fraction(-num, den)

    fun abs(): Fraction = if (num < 0) -this else this

    override fun compareTo(other: Fraction): Int = runCatching {
        Math.multiplyExact(num, other.den).compareTo(Math.multiplyExact(other.num, den))
    }.getOrElse { toDouble().compareTo(other.toDouble()) }

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    override fun equals(other: Any?): Boolean =
        other is Fraction && num == other.num && den == other.den

    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()

    override fun toString(): String = if (den == 1L) "$num" else "$num/$den"

    companion object {
        val ZERO = Fraction(0L, 1L)
        val ONE = Fraction(1L, 1L)

        /**
         * A fraction in lowest terms.
         *
         * @return null when [d] is zero, or when reducing overflows — which only `Long.MIN_VALUE`
         *   can do, since negating it has no positive counterpart.
         */
        fun of(n: Long, d: Long = 1L): Fraction? {
            if (d == 0L) return null
            if (n == Long.MIN_VALUE || d == Long.MIN_VALUE) return null
            if (n == 0L) return ZERO
            val sign = if ((n < 0) != (d < 0)) -1L else 1L
            val an = kotlin.math.abs(n)
            val ad = kotlin.math.abs(d)
            val g = gcd(an, ad)
            return Fraction(sign * (an / g), ad / g)
        }

        fun of(n: Int): Fraction = of(n.toLong()) ?: ZERO

        tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) kotlin.math.abs(a) else gcd(b, a % b)

        /** Least common multiple, or null on overflow. */
        fun lcm(a: Long, b: Long): Long? {
            if (a == 0L || b == 0L) return 0L
            val g = gcd(a, b)
            return runCatching { Math.multiplyExact(kotlin.math.abs(a) / g, kotlin.math.abs(b)) }
                .getOrNull()
        }
    }
}
