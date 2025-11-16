package com.jlindemann.science.utils

import java.util.Calendar

object ProPlusTimeUtil {
    /**
     * Check if the current date is before January 1, 2026
     * @return true if current date is before Jan 1, 2026, false otherwise
     */
    fun isBeforeJanuary2026(): Boolean {
        val currentDate = Calendar.getInstance()
        val targetDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return currentDate.before(targetDate)
    }
}
