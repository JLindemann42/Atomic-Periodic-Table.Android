package com.jlindemann.science.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class AIAgentManagerTest {

    private lateinit var aiAgentManager: AIAgentManager
    private lateinit var elementData: JSONObject

    @Before
    fun setup() {
        // We use a mock-like approach or reflection to set up the data without a real Context
        // Since AIAgentManager needs a Context in constructor, we might need a dummy or use reflection if possible
        // For this test, let's assume we can set the elementData field via reflection
        
        // Use a placeholder context (null might fail if constructor uses it immediately)
        // Actually, AIAgentManager only uses context in initialize() and getElementDataByLanguage()
        // We can bypass initialize() and inject elementData directly for logic testing.
        
        // For testing purposes, we'll try to instantiate it with null and see if it works for unit testing logic
        // This is a bit hacky but avoids needing Robolectric for a simple logic test.
    }

    @Test
    fun testLevenshteinDistance() {
        // Access private method for testing
        val method: Method = AIAgentManager::class.java.getDeclaredMethod("levenshteinDistance", String::class.java, String::class.java)
        method.isAccessible = true
        
        val manager = AIAgentManager(null) // Using updated nullable Context constructor
        
        assertEquals(0, method.invoke(manager, "oxygen", "oxygen"))
        assertEquals(1, method.invoke(manager, "oxygen", "oxigen"))
        assertEquals(2, method.invoke(manager, "oxygen", "oxigeno"))
        assertEquals(4, method.invoke(manager, "hydrogen", "hydra")) // hydr -> hydra is 1, ogen -> nothing is 4? Wait.
    }

    @Test
    fun testHasKeyword() {
        val method: Method = AIAgentManager::class.java.getDeclaredMethod("hasKeyword", String::class.java, List::class.java)
        method.isAccessible = true
        val manager = AIAgentManager(null as android.content.Context?)
        
        assertTrue(method.invoke(manager, "what is the atomic number?", listOf("atomic", "number")) as Boolean)
        assertTrue(method.invoke(manager, "tell me about its mass", listOf("mass", "weight")) as Boolean)
        assertFalse(method.invoke(manager, "hello there", listOf("atomic", "number")) as Boolean)
    }
}
