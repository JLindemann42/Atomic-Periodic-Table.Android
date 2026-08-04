package com.jlindemann.science.preferences

import org.junit.Assert.*
import org.junit.Test

/**
 * A tool added after a user's counter string was first written is invisible to them.
 *
 * The counter is only ever rewritten in place — a tool bumps its own entry and nothing appends —
 * so a new id that is not already in the string is never counted, never sorts anywhere, and never
 * reaches the most-used row. That failure is silent on exactly the installs that matter: everyone
 * who had the app before the tool shipped.
 */
class MostUsedToolPreferenceTest {

    /** The string the app shipped before the equation balancer existed. */
    private val legacy = "cal=0.1, uni=0.2, fla=0.3, gas=0.4, dic=0.5"

    @Test
    fun anExistingCounterGainsTheToolsAddedSinceItWasWritten() {
        val migrated = MostUsedToolPreference.withNewTools(legacy)
        assertTrue("balancer missing from '$migrated'", migrated.contains("bal="))
        // Everything already there keeps the count it had earned.
        assertTrue(migrated.startsWith(legacy))
    }

    @Test
    fun anUpToDateCounterIsLeftExactlyAsItIs() {
        val current = MostUsedToolPreference.DEFAULT
        assertEquals(current, MostUsedToolPreference.withNewTools(current))
    }

    /** Counts users have accumulated must survive the migration untouched. */
    @Test
    fun accumulatedCountsAreNotReset() {
        val used = "cal=14.1, uni=3.2, fla=0.3, gas=9.4, dic=1.5"
        val migrated = MostUsedToolPreference.withNewTools(used)
        assertTrue(migrated.contains("cal=14.1"))
        assertTrue(migrated.contains("gas=9.4"))
        assertTrue(migrated.contains("bal=0.6"))
    }

    /** The result has to stay readable by the `(\w{3})=(\d+\.\d+)` parser the row uses. */
    @Test
    fun theMigratedStringStillParses() {
        val migrated = MostUsedToolPreference.withNewTools(legacy)
        val parsed = Regex("""(\w{3})=(\d+\.\d+)""").findAll(migrated)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf("cal", "uni", "fla", "gas", "dic", "bal"), parsed)
    }

    @Test
    fun everyToolTheRowCanShowIsInTheDefault() {
        for (id in listOf("cal", "uni", "bal", "gas", "dic")) {
            assertTrue("$id missing from the default", MostUsedToolPreference.DEFAULT.contains("$id="))
        }
    }
}
