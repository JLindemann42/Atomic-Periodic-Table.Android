package com.jlindemann.science.ai.data

/**
 * The ideal gas law, PV = nRT (roadmap 2.8), in litres, atmospheres, moles and kelvin. Given any
 * three, it solves the fourth.
 */
object GasLaw {

    /** L·atm/(mol·K). */
    const val R = 0.082057366080960

    /** Standard temperature and pressure, IUPAC's older definition that textbooks still teach. */
    const val STP_KELVIN = 273.15
    const val STP_ATM = 1.0

    enum class Unknown { PRESSURE, VOLUME, MOLES, TEMPERATURE }

    /** All four values, and which one was solved for. */
    data class Solution(
        val unknown: Unknown,
        val atm: Double,
        val litres: Double,
        val moles: Double,
        val kelvin: Double
    ) {
        /** The value that was asked for, in its unit above. */
        val solved: Double
            get() = when (unknown) {
                Unknown.PRESSURE -> atm
                Unknown.VOLUME -> litres
                Unknown.MOLES -> moles
                Unknown.TEMPERATURE -> kelvin
            }
    }

    /**
     * Solve for the one value that is null. Null when not exactly one is missing, or when a given
     * value is not positive: a gas at zero kelvin or with no volume is not an ideal gas question.
     */
    fun solve(atm: Double?, litres: Double?, moles: Double?, kelvin: Double?): Solution? {
        val given = listOfNotNull(atm, litres, moles, kelvin)
        if (given.size != 3 || given.any { it <= 0.0 || it.isNaN() || it.isInfinite() }) return null
        return when {
            atm == null -> Solution(Unknown.PRESSURE, moles!! * R * kelvin!! / litres!!, litres, moles, kelvin)
            litres == null -> Solution(Unknown.VOLUME, atm, moles!! * R * kelvin!! / atm, moles, kelvin)
            moles == null -> Solution(Unknown.MOLES, atm, litres, atm * litres / (R * kelvin!!), kelvin)
            else -> Solution(Unknown.TEMPERATURE, atm, litres, moles, atm * litres / (moles * R))
        }
    }
}
