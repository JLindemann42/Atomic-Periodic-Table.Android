package com.jlindemann.science.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class AIAgentManagerTest {

    @Test
    fun testLevenshteinDistance() {
        val method: Method = AIAgentManager::class.java.getDeclaredMethod("levenshteinDistance", String::class.java, String::class.java)
        method.isAccessible = true
        val manager = AIAgentManager(null)
        
        assertEquals(0, method.invoke(manager, "oxygen", "oxygen"))
        assertEquals(1, method.invoke(manager, "oxygen", "oxigen"))
        assertEquals(2, method.invoke(manager, "oxygen", "oxigeno"))
    }

    @Test
    fun testHasKeyword() {
        val method: Method = AIAgentManager::class.java.getDeclaredMethod("hasKeyword", String::class.java, List::class.java)
        method.isAccessible = true
        val manager = AIAgentManager(null)
        
        assertTrue(method.invoke(manager, "what is the atomic number?", listOf("atomic", "number")) as Boolean)
        assertTrue(method.invoke(manager, "tell me about its mass", listOf("mass", "weight")) as Boolean)
        assertFalse(method.invoke(manager, "hello there", listOf("atomic", "number")) as Boolean)
    }

    @Test
    fun testIsAffirmative() {
        val method: Method = AIAgentManager::class.java.getDeclaredMethod("isAffirmative", String::class.java)
        method.isAccessible = true
        val manager = AIAgentManager(null)
        
        assertTrue(method.invoke(manager, "yes") as Boolean)
        assertTrue(method.invoke(manager, "mer") as Boolean)
        assertTrue(method.invoke(manager, "aur") as Boolean)
        assertTrue(method.invoke(manager, "plus") as Boolean)
        assertTrue(method.invoke(manager, "告诉我更多") as Boolean)
        assertTrue(method.invoke(manager, "magkwento ka pa") as Boolean)
        assertTrue(method.invoke(manager, "mi dica di più") as Boolean)
        assertFalse(method.invoke(manager, "not exactly") as Boolean)
    }

    @Test
    fun testCommonWordCollision() {
        val method: Method = AIAgentManager::class.java.getDeclaredMethod("isCommonWordCollision", String::class.java, String::class.java, List::class.java)
        method.isAccessible = true
        val manager = AIAgentManager(null)
        
        val langField = AIAgentManager::class.java.getDeclaredField("activeLanguage")
        langField.isAccessible = true
        
        langField.set(manager, "sv")
        assertTrue(method.invoke(manager, "ar", "är väte radioaktivt", listOf("ar", "väte", "radioaktivt")) as Boolean)
        assertFalse(method.invoke(manager, "ar", "ar", listOf("ar")) as Boolean)
        
        langField.set(manager, "en")
        assertTrue(method.invoke(manager, "be", "to be or not to be", listOf("to", "be", "or", "not", "to", "be")) as Boolean)

        langField.set(manager, "hi")
        assertTrue(method.invoke(manager, "se", "हाइड्रोजन से क्या होता है", listOf("हाइड्रोजन", "se", "क्या", "होता", "है")) as Boolean)

        langField.set(manager, "fil")
        assertTrue(method.invoke(manager, "ng", "ano ang gamit ng hydrogen", listOf("ano", "ang", "gamit", "ng", "hydrogen")) as Boolean)

        langField.set(manager, "it")
        assertTrue(method.invoke(manager, "al", "aggiungi acqua al composto", listOf("aggiungi", "acqua", "al", "composto")) as Boolean)

        langField.set(manager, "af")
        assertTrue(method.invoke(manager, "as", "wat gebeur as dit warm word", listOf("wat", "gebeur", "as", "dit", "warm", "word")) as Boolean)
    }

    @Test
    fun testClearConversation() {
        val manager = AIAgentManager(null)
        
        val elementField = AIAgentManager::class.java.getDeclaredField("currentElement")
        elementField.isAccessible = true
        elementField.set(manager, "Gold")
        
        val topicField = AIAgentManager::class.java.getDeclaredField("currentTopic")
        topicField.isAccessible = true
        topicField.set(manager, "history")
        
        val propsField = AIAgentManager::class.java.getDeclaredField("sharedProperties")
        propsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val props = propsField.get(manager) as MutableSet<String>
        props.add("mass")
        
        assertEquals("Gold", elementField.get(manager))
        assertEquals("history", topicField.get(manager))
        assertFalse(props.isEmpty())
        
        manager.clearConversation()
        
        assertNull(elementField.get(manager))
        assertNull(topicField.get(manager))
        assertTrue(props.isEmpty())
    }
}
