package com.jlindemann.science.ai

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method

class LocalKnowledgeManagerTest {

    @Test
    fun testScoreMatchShortCandidate() {
        val method: Method = LocalKnowledgeManager::class.java.getDeclaredMethod("scoreMatch", String::class.java, String::class.java)
        method.isAccessible = true
        val manager = LocalKnowledgeManager(null as android.content.Context?)
        
        // "me" is the symbol for electron mass
        // "mer" should NOT match "me" because "me" is too short and lacks word boundaries in "mer"
        val scoreMer = method.invoke(manager, "mer", "me") as Int
        assertEquals(0, scoreMer)
        
        // Exact match should still work
        val scoreExact = method.invoke(manager, "me", "me") as Int
        assertEquals(100, scoreExact)
        
        // Word boundary match should work
        val scoreWithSpace = method.invoke(manager, "tell me", "me") as Int
        assertEquals(100, scoreWithSpace)
    }
}
