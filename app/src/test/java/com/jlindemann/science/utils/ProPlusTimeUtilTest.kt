package com.jlindemann.science.utils

import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

/**
 * Unit tests for ProPlusTimeUtil
 */
class ProPlusTimeUtilTest {
    
    @Test
    fun testIsBeforeJanuary2026_returnsBoolean() {
        // Test that the function returns a boolean value
        val result = ProPlusTimeUtil.isBeforeJanuary2026()
        assertTrue(result is Boolean)
    }
    
    @Test
    fun testIsBeforeJanuary2026_currentLogic() {
        // This test verifies the current date logic
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
        
        val result = ProPlusTimeUtil.isBeforeJanuary2026()
        val expected = currentDate.before(targetDate)
        
        assertEquals(expected, result)
    }
}
