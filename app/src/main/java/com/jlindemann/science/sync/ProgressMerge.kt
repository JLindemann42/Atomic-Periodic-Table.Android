package com.jlindemann.science.sync

import com.jlindemann.science.util.XpManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Combines local and cloud progress.
 *
 * **This is the one place in the app where a bug destroys user data irrecoverably**, so it is pure,
 * it has no Android imports, and it is unit tested. Every rule below is monotonic - a merge can
 * only ever move a counter forward - which is what makes it safe to run on every launch and safe to
 * run twice.
 *
 * The rules match the Windows app's `ProgressMerge.Merge` exactly. A disagreement would not show up
 * as an error; it would show up as a number that flips back and forth depending on which device
 * synced last.
 */
object ProgressMerge {

    fun merge(local: ProgressSnapshot, cloud: ProgressSnapshot): ProgressSnapshot {
        val mergedXp = maxOf(local.xp, cloud.xp)

        return ProgressSnapshot(
            xp = mergedXp,

            // Derived from the merged XP rather than max(local, cloud). Taking the larger stored
            // level could keep a level its own XP no longer supports, and both platforms compute it
            // from the same curve.
            level = XpManager.getLevel(mergedXp.toInt()),

            streak = maxOf(local.streak, cloud.streak),
            streakLastPlay = laterDay(local.streakLastPlay, cloud.streakLastPlay),
            achievements = mergeAchievements(local.achievements, cloud.achievements),
            statistics = mergeStatistics(local.statistics, cloud.statistics),
            notes = NotesCodec.merge(local.notes, cloud.notes),

            // Each platform is the authority on its own tier, so the local value wins for Android
            // and the cloud value is simply carried for Windows. Taking a maximum across them would
            // let a PRO+ PC silently unlock a phone that has paid for nothing - which is what the
            // Ultimate product is for, and it says so explicitly rather than by leaking through a
            // merge.
            androidTier = local.androidTier ?: cloud.androidTier,
            windowsTier = cloud.windowsTier ?: local.windowsTier,

            // Ultimate is bought once and honoured everywhere, so it only ever grants.
            ultimate = local.ultimate || cloud.ultimate,

            study = mergeStudy(local.study, cloud.study),
        )
    }

    /**
     * Per card, the later review wins; on a tie the one with more reps, then the cloud's. Cards
     * are never deleted, so a card on either side is kept (shared data `sync/study.md`).
     */
    fun mergeStudy(
        local: Map<String, com.jlindemann.science.study.CardState>,
        cloud: Map<String, com.jlindemann.science.study.CardState>
    ): Map<String, com.jlindemann.science.study.CardState> =
        (local.keys + cloud.keys).associateWith { key ->
            com.jlindemann.science.study.StudyDeck.newer(local[key], cloud[key])!!
        }

    /**
     * The later of two ISO days.
     *
     * Compared as parsed dates, not as strings, and independently of the streak count. String
     * comparison happens to work for `yyyy-MM-dd`, but it also silently "works" for malformed
     * input, ordering "not-a-date" after every real one - and that value would then be written back
     * to both devices.
     */
    fun laterDay(local: String?, cloud: String?): String? {
        val localDay = parseDay(local)
        val cloudDay = parseDay(cloud)

        if (localDay == null) return if (cloudDay == null) null else cloud
        if (cloudDay == null) return local

        return if (cloudDay.after(localDay)) cloud else local
    }

    /** Null for anything that is not a real `yyyy-MM-dd` day. */
    fun parseDay(day: String?): Date? {
        if (day.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(day)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Per id, the higher progress; the union of ids.
     *
     * An achievement present on one side and absent on the other is *kept*, never treated as a
     * reset. Absence means "that device has not heard of it", which happens whenever one platform
     * ships an achievement before the other.
     */
    private fun mergeAchievements(
        local: List<AchievementProgress>,
        cloud: List<AchievementProgress>,
    ): List<AchievementProgress> {
        val merged = LinkedHashMap<Int, AchievementProgress>()

        for (achievement in local + cloud) {
            val existing = merged[achievement.id]
            merged[achievement.id] = if (existing == null) {
                achievement
            } else {
                existing.copy(
                    progress = maxOf(existing.progress, achievement.progress),
                    // The larger ceiling wins: a platform that has raised an achievement's target
                    // must not have it clamped back by one that has not.
                    maxProgress = maxOf(existing.maxProgress, achievement.maxProgress),
                    // Whichever side has one. A blank title from a platform that does not send them
                    // must not erase the other's.
                    title = if (existing.title.isNotEmpty()) existing.title else achievement.title,
                )
            }
        }

        return merged.values.sortedBy { it.id }
    }

    private fun mergeStatistics(
        local: Map<Int, Long>,
        cloud: Map<Int, Long>,
    ): Map<Int, Long> {
        val merged = LinkedHashMap(local)
        for ((id, value) in cloud) {
            val existing = merged[id]
            merged[id] = if (existing == null) value else maxOf(existing, value)
        }
        return merged
    }
}
