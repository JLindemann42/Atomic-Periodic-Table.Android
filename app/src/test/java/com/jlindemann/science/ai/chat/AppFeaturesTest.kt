package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.nlu.Lexicon
import com.jlindemann.science.ai.retrieval.TextMatching
import org.junit.Assert.*
import org.junit.Test

/** Questions about the app itself. Absorbs the old `LocalKnowledgeManagerTest`. */
class AppFeaturesTest {

    private fun resolve(query: String) = AppFeatures.resolve(TextMatching.normalizeForLookup(query))

    /** The keyword lists were once English and Swedish only. */
    @Test fun appFeaturesResolveFromSeveralLanguages() {
        for (query in listOf(
            "table of nuclides", "vad ar en nuklidkarta", "was ist der einheitenumrechner",
            "espectro de emision", "配平", "मोलर द्रव्यमान कैलकुलेटर"
        )) assertNotNull(query, resolve(query))
    }

    /** A bare `contains` found "unit" inside "opportunity" and "reaction" inside "interaction". */
    @Test fun featureKeywordsMatchOnWordBoundaries() {
        assertNull(resolve("what is the opportunity cost"))
        assertNull(resolve("explain interaction between atoms"))
    }

    /** The keywords are folded ASCII, so the accented spelling a user types is folded first. */
    @Test fun accentedSpellingsMatchTheFoldedKeywords() {
        assertNotNull(resolve("espectro de emisión"))
        assertEquals(R.string.ai_feature_capabilities, resolve("vad kan du göra"))
    }

    /** "Help me with the reaction balancer" matches two; the first in order wins. */
    @Test fun theFirstFeatureInOrderWins() =
        assertEquals(R.string.ai_feature_reaction_balancer, resolve("help me with the reaction balancer"))

    /** A tenth feature added to the lexicon must be added to the order too, or it is unreachable. */
    @Test fun everyFeatureIsOrdered() = assertEquals(Lexicon.APP_FEATURE_WORDS.keys, AppFeatures.features.toSet())
}
