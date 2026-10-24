package com.jlindemann.science.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge is the one place in the app where a bug destroys user data irrecoverably, and it has to
 * agree exactly with the Windows implementation - a disagreement would not show up as an error, it
 * would show up as a number that flips back and forth depending on which device synced last.
 */
class ProgressMergeTest {

    // ---- The three properties everything else rests on ------------------------------------------

    private val a = ProgressSnapshot(
        xp = 1200,
        streak = 4,
        streakLastPlay = "2026-08-20",
        achievements = listOf(
            AchievementProgress(1, progress = 5, maxProgress = 10, title = "Explorer"),
            AchievementProgress(3, progress = 1, maxProgress = 50),
        ),
        statistics = mapOf(1 to 40L, 2 to 3L),
        notes = "<h>hydrogen note</h>",
        androidTier = "pro",
        ultimate = false,
    )

    private val b = ProgressSnapshot(
        xp = 900,
        streak = 7,
        streakLastPlay = "2026-08-18",
        achievements = listOf(
            AchievementProgress(1, progress = 9, maxProgress = 10),
            AchievementProgress(2, progress = 2, maxProgress = 8, title = "Chemist"),
        ),
        statistics = mapOf(1 to 12L, 3 to 99L),
        notes = "<h>a much longer hydrogen note</h><he>helium</he>",
        windowsTier = "pro+",
        ultimate = true,
    )

    @Test
    fun `merge is commutative for everything but platform tiers`() {
        val ab = ProgressMerge.merge(a, b)
        val ba = ProgressMerge.merge(b, a)

        assertEquals(ab.xp, ba.xp)
        assertEquals(ab.level, ba.level)
        assertEquals(ab.streak, ba.streak)
        assertEquals(ab.streakLastPlay, ba.streakLastPlay)
        assertEquals(ab.achievements, ba.achievements)
        assertEquals(ab.statistics, ba.statistics)
        assertEquals(ab.notes, ba.notes)
        assertEquals(ab.ultimate, ba.ultimate)
    }

    @Test
    fun `merge is idempotent`() {
        val once = ProgressMerge.merge(a, b)
        val twice = ProgressMerge.merge(once, once)
        assertEquals(once, twice)
    }

    @Test
    fun `merging with an empty snapshot changes nothing that matters`() {
        val merged = ProgressMerge.merge(a, ProgressSnapshot.EMPTY)

        assertEquals(a.xp, merged.xp)
        assertEquals(a.streak, merged.streak)
        assertEquals(a.streakLastPlay, merged.streakLastPlay)
        assertEquals(a.statistics, merged.statistics)
        assertEquals(a.achievements.map { it.id }, merged.achievements.map { it.id })
    }

    // ---- Counters ------------------------------------------------------------------------------

    @Test
    fun `counters take the maximum`() {
        val merged = ProgressMerge.merge(a, b)
        assertEquals(1200L, merged.xp)
        assertEquals(7, merged.streak)
    }

    @Test
    fun `level is derived from merged xp not from the stored levels`() {
        // A stored level that its own XP no longer supports must not survive the merge.
        val inflated = ProgressSnapshot(xp = 0, level = 80)
        val merged = ProgressMerge.merge(inflated, ProgressSnapshot(xp = 0, level = 0))
        assertEquals(1, merged.level)
    }

    // ---- Streak date: the rule that changed to match Windows -------------------------------------

    @Test
    fun `the later day wins independently of the streak count`() {
        // The old rule took the date belonging to whichever side had the higher streak, so this
        // pair produced 2026-08-18 here and 2026-08-20 on the PC.
        val merged = ProgressMerge.merge(a, b)
        assertEquals(7, merged.streak)
        assertEquals("2026-08-20", merged.streakLastPlay)
    }

    @Test
    fun `a malformed day never wins`() {
        // String comparison would order "not-a-date" after every real day, and that value would
        // then be written back to both devices.
        assertEquals("2026-08-20", ProgressMerge.laterDay("2026-08-20", "not-a-date"))
        assertEquals("2026-08-20", ProgressMerge.laterDay("not-a-date", "2026-08-20"))
        assertNull(ProgressMerge.laterDay("not-a-date", null))
    }

    @Test
    fun `an impossible day is not a day`() {
        assertNull(ProgressMerge.parseDay("2026-13-45"))
        assertNull(ProgressMerge.parseDay(""))
        assertNull(ProgressMerge.parseDay(null))
    }

    // ---- Achievements --------------------------------------------------------------------------

    @Test
    fun `achievements union by id and take the higher progress`() {
        val merged = ProgressMerge.merge(a, b).achievements.associateBy { it.id }

        assertEquals(setOf(1, 2, 3), merged.keys)
        assertEquals(9, merged.getValue(1).progress)
        assertEquals(2, merged.getValue(2).progress)
        assertEquals(1, merged.getValue(3).progress)
    }

    @Test
    fun `an achievement only one side knows is kept never reset`() {
        val merged = ProgressMerge.merge(a, b).associateAchievements()
        assertEquals(2, merged.getValue(2).progress) // only in b
        assertEquals(1, merged.getValue(3).progress) // only in a
    }

    @Test
    fun `a blank title does not erase the other side's`() {
        val merged = ProgressMerge.merge(a, b).associateAchievements()
        assertEquals("Explorer", merged.getValue(1).title)
        assertEquals("Chemist", merged.getValue(2).title)
    }

    @Test
    fun `the larger ceiling wins`() {
        // A platform that has raised an achievement's target must not have it clamped back by one
        // that has not.
        val raised = ProgressSnapshot(achievements = listOf(AchievementProgress(1, 0, 100)))
        val old = ProgressSnapshot(achievements = listOf(AchievementProgress(1, 0, 10)))
        assertEquals(100, ProgressMerge.merge(raised, old).achievements.single().maxProgress)
    }

    @Test
    fun `achievements come back sorted by id`() {
        val ids = ProgressMerge.merge(a, b).achievements.map { it.id }
        assertEquals(ids.sorted(), ids)
    }

    // ---- Statistics ----------------------------------------------------------------------------

    @Test
    fun `statistics union by id and take the higher progress`() {
        val merged = ProgressMerge.merge(a, b).statistics
        assertEquals(40L, merged[1])
        assertEquals(3L, merged[2])
        assertEquals(99L, merged[3])
    }

    // ---- Entitlements --------------------------------------------------------------------------

    @Test
    fun `this platform is the authority on its own tier`() {
        val local = ProgressSnapshot(androidTier = "pro")
        val cloud = ProgressSnapshot(androidTier = "free", windowsTier = "pro+")
        val merged = ProgressMerge.merge(local, cloud)

        assertEquals("pro", merged.androidTier)
        assertEquals("pro+", merged.windowsTier)
    }

    @Test
    fun `a pro plus PC does not unlock the phone`() {
        // Taking a maximum across platforms would let a paid-for PC silently unlock a phone that
        // has paid nothing. That is what Ultimate is for, and it says so explicitly.
        val merged = ProgressMerge.merge(
            ProgressSnapshot(androidTier = "free"),
            ProgressSnapshot(windowsTier = "pro+"),
        )
        assertEquals("free", merged.androidTier)
    }

    @Test
    fun `ultimate only ever grants`() {
        assertTrue(
            ProgressMerge.merge(
                ProgressSnapshot(ultimate = false),
                ProgressSnapshot(ultimate = true),
            ).ultimate
        )
        assertTrue(
            ProgressMerge.merge(
                ProgressSnapshot(ultimate = true),
                ProgressSnapshot(ultimate = false),
            ).ultimate
        )
    }

    // ---- Notes ---------------------------------------------------------------------------------

    @Test
    fun `notes merge per element with the longer note winning`() {
        val merged = NotesCodec.parse(ProgressMerge.merge(a, b).notes)
        assertEquals("a much longer hydrogen note", merged["h"])
        assertEquals("helium", merged["he"])
    }

    @Test
    fun `notes an element only one side has are kept`() {
        val merged = NotesCodec.merge("<h>one</h>", "<he>two</he>")
        assertEquals(mapOf("h" to "one", "he" to "two"), NotesCodec.parse(merged))
    }

    @Test
    fun `notes are emitted sorted so the blob is stable`() {
        // Two devices holding identical notes must produce byte-identical blobs; the merge rule
        // compares lengths and an unstable ordering would make that comparison meaningless.
        val one = NotesCodec.format(mapOf("he" to "b", "h" to "a"))
        val two = NotesCodec.format(mapOf("h" to "a", "he" to "b"))
        assertEquals(one, two)
        assertEquals("<h>a</h><he>b</he>", one)
    }

    @Test
    fun `notes survive newlines`() {
        val blob = "<h>line one\nline two</h>"
        assertEquals("line one\nline two", NotesCodec.parse(blob)["h"])
    }

    private fun ProgressSnapshot.associateAchievements() = achievements.associateBy { it.id }
}
