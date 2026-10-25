package com.jlindemann.science.ai.chat

import org.junit.Assert.*
import org.junit.Test

class InterestProfileTest {

    @Test fun theMostAskedAboutElementIsTheFavourite() {
        val profile = InterestProfile()
        profile.track("gold"); profile.track("iron"); profile.track("Gold")
        assertEquals("gold", profile.favourite)
    }

    /** Ties break on the key, so the greeting is the same on every launch. */
    @Test fun aTieBreaksOnTheKey() {
        val profile = InterestProfile()
        profile.track("iron"); profile.track("gold")
        assertEquals("gold", profile.favourite)
    }

    @Test fun nothingTrackedMeansNoFavourite() {
        val profile = InterestProfile()
        profile.track(null); profile.track(" ")
        assertNull(profile.favourite)
    }

    /** Restored from the counts the old `AILearningManager` saved, keys and all. */
    @Test fun aSavedProfileIsRestored() {
        val profile = InterestProfile()
        profile.load(mapOf("Gold" to 3, "iron" to 0, "" to 9))
        assertEquals(mapOf("gold" to 3), profile.elements)
    }
}
