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

    private fun resolveFeature(query: String): String? {
        val manager = LocalKnowledgeManager(null as android.content.Context?)
        val method = LocalKnowledgeManager::class.java
            .getDeclaredMethod("resolveAppFeature", String::class.java)
        method.isAccessible = true
        val result = method.invoke(manager, query) ?: return null
        return LocalKnowledgeManager.QueryResult::class.java
            .getMethod("getResponse").invoke(result) as String
    }

    /**
     * The keyword lists used to be English and Swedish only, so ten of the twelve shipped languages
     * could not reach a single one of these answers.
     */
    @Test
    fun appFeaturesResolveFromSeveralLanguages() {
        for (query in listOf(
            "table of nuclides",           // en
            "vad ar en nuklidkarta",       // sv
            "was ist der einheitenumrechner", // de
            "espectro de emision",         // es
            "配平",                          // zh
            "मोलर द्रव्यमान कैलकुलेटर"              // hi
        )) {
            assertNotNull("no app feature resolved for \"$query\"", resolveFeature(query))
        }
    }

    /**
     * A bare `contains` matched "unit" inside "opportunity" and "reaction" inside "interaction",
     * and answered a chemistry question with a tour of the app.
     */
    @Test
    fun featureKeywordsMatchOnWordBoundaries() {
        assertNull(resolveFeature("what is the opportunity cost"))
        assertNull(resolveFeature("explain interaction between atoms"))
    }

    /** With no Context the English fallback still has to be a sentence, not a resource id. */
    @Test
    fun aNullContextStillAnswersInEnglish() {
        val answer = resolveFeature("table of nuclides")
        assertNotNull(answer)
        assertTrue(answer!!.isNotBlank())
        assertFalse(answer.startsWith("str:"))
    }
}
