package com.jlindemann.science.ai

import org.junit.Assert.*
import org.junit.Test

/**
 * Pins the members `AIAgentManagerTest` reaches through reflection.
 *
 * That test pokes private fields and methods directly, so a refactor that looks harmless can
 * break it only at *runtime*, with a `NoSuchFieldException` the compiler cannot warn about. In
 * particular, replacing any of these fields with a Kotlin custom-getter property removes its
 * backing field and breaks reflection silently.
 *
 * If this test fails, either restore the member or update `AIAgentManagerTest` deliberately.
 */
class AiPublicSurfaceTest {

    private val requiredFields = listOf(
        "currentElement", "currentTopic", "sharedProperties", "activeLanguage"
    )

    private val requiredMethods = mapOf(
        "levenshteinDistance" to listOf(String::class.java, String::class.java),
        "hasKeyword" to listOf(String::class.java, List::class.java),
        "isAffirmative" to listOf(String::class.java),
        "isCommonWordCollision" to listOf(String::class.java, String::class.java, List::class.java)
    )

    @Test
    fun reflectedFieldsStillExistAsRealBackingFields() {
        for (name in requiredFields) {
            val field = runCatching { AIAgentManager::class.java.getDeclaredField(name) }.getOrNull()
            assertNotNull(
                "AIAgentManager.$name is reached by reflection in AIAgentManagerTest. " +
                        "It must stay a real private field with a backing field — a custom-getter " +
                        "property would break that test at runtime only.",
                field
            )
        }
    }

    @Test
    fun reflectedMethodsStillExistWithTheSameSignature() {
        for ((name, params) in requiredMethods) {
            val method = runCatching {
                AIAgentManager::class.java.getDeclaredMethod(name, *params.toTypedArray())
            }.getOrNull()
            assertNotNull(
                "AIAgentManager.$name(${params.joinToString { it.simpleName }}) is reached by " +
                        "reflection in AIAgentManagerTest and must keep this exact signature. " +
                        "Delegating the body elsewhere is fine; changing the signature is not.",
                method
            )
        }
    }

    @Test
    fun publicApiUsedByTheHostActivitiesIsIntact() {
        val required = listOf(
            "generateResponse", "setCurrentElement", "initialize", "clearConversation",
            "getMessageLimitDisplay", "isHistoryEnabled", "setConversationHistory",
            "addToConversationHistory", "resetSession", "refreshLanguage", "setLanguage",
            "getSuggestedQuestions", "shouldShowMessageLimit"
        )
        val declared = AIAgentManager::class.java.methods.map { it.name }.toSet()
        for (name in required) {
            assertTrue("AIAgentManager.$name is used by MainActivity/ElementInfoActivity", name in declared)
        }
    }
}
