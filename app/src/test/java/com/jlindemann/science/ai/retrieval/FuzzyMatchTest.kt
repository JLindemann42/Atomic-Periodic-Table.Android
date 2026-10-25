package com.jlindemann.science.ai.retrieval

import org.junit.Assert.*
import org.junit.Test

/** The cases the old `AIAgentManagerTest` asserted by reflection, against their new homes. */
class FuzzyMatchTest {

    @Test fun levenshteinDistance() {
        assertEquals(0, TextMatching.levenshtein("oxygen", "oxygen"))
        assertEquals(1, TextMatching.levenshtein("oxygen", "oxigen"))
        assertEquals(2, TextMatching.levenshtein("oxygen", "oxigeno"))
    }

    @Test fun hasKeyword() {
        assertTrue(TextMatching.hasKeyword("what is the atomic number?", listOf("atomic", "number")))
        assertTrue(TextMatching.hasKeyword("tell me about its mass", listOf("mass", "weight")))
        assertFalse(TextMatching.hasKeyword("hello there", listOf("atomic", "number")))
    }

    @Test fun isAffirmative() {
        for (yes in listOf("yes", "mer", "aur", "plus", "告诉我更多", "magkwento ka pa", "mi dica di più", "gärna", "sí")) {
            assertTrue(yes, FuzzyMatch.isAffirmative(yes))
        }
        assertFalse(FuzzyMatch.isAffirmative("not exactly"))
        assertFalse(FuzzyMatch.isAffirmative(""))
    }

    /** Word boundaries: the "ja" inside "japan" and the "ha" inside "hafnium" are not a yes. */
    @Test fun affirmativesMatchWholeWordsOnly() {
        assertFalse(FuzzyMatch.isAffirmative("japan"))
        assertFalse(FuzzyMatch.isAffirmative("hafnium"))
    }
}
