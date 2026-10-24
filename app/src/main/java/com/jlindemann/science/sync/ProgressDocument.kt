package com.jlindemann.science.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * Maps a [ProgressSnapshot] to and from the Firestore document at `users/{uid}`.
 *
 * **The document is co-owned with the Windows app.** That single fact drives the design: this build
 * must write only the fields it understands and leave everything else alone. Windows enforces it
 * with `updateMask.fieldPaths` on its REST PATCH; the equivalent here is `SetOptions.merge()`, which
 * touches only the keys present in the map and **deep-merges nested maps**. A bare `set()` on this
 * document would delete whatever the PC had written, so there must never be one.
 *
 * Field names are wire identifiers, not display strings - renaming one silently forks the two apps'
 * data.
 */
object ProgressDocument {

    const val XP = "xp"
    const val LEVEL = "level"
    const val STREAK = "streak"
    const val STREAK_LAST_PLAY = "streakLastPlay"
    const val ACHIEVEMENTS = "achievements"
    const val STATISTICS = "statistics"
    const val NOTES = "notes"
    const val LAST_UPDATED = "lastUpdated"

    /**
     * The per-platform entitlement map: `entitlements.android`, `entitlements.windows`,
     * `entitlements.ultimate`.
     *
     * A map rather than three top-level fields so the platforms stay visibly parallel, and so a
     * third one costs a key rather than a schema change.
     */
    const val ENTITLEMENTS = "entitlements"

    const val ANDROID_TIER = "android"
    const val WINDOWS_TIER = "windows"
    const val ULTIMATE = "ultimate"

    // ---- Reading -----------------------------------------------------------------------------

    /**
     * Read a document into a snapshot.
     *
     * Every field is optional. A document written by an older build, or by a platform that has not
     * shipped a feature yet, is a normal thing to meet - so a missing or oddly-typed field becomes
     * a default rather than an exception.
     */
    fun fromSnapshot(snapshot: DocumentSnapshot?): ProgressSnapshot {
        if (snapshot == null || !snapshot.exists()) return ProgressSnapshot.EMPTY

        val entitlements = snapshot.get(ENTITLEMENTS) as? Map<*, *>

        return ProgressSnapshot(
            xp = readLong(snapshot.get(XP)) ?: 0L,
            level = readInt(snapshot.get(LEVEL)) ?: 0,
            streak = readInt(snapshot.get(STREAK)) ?: 0,
            streakLastPlay = snapshot.getString(STREAK_LAST_PLAY),
            achievements = readAchievements(snapshot.get(ACHIEVEMENTS)),
            statistics = readStatistics(snapshot.get(STATISTICS)),
            notes = snapshot.getString(NOTES),
            androidTier = entitlements?.get(ANDROID_TIER) as? String,
            windowsTier = entitlements?.get(WINDOWS_TIER) as? String,
            ultimate = entitlements?.get(ULTIMATE) as? Boolean ?: false,
        )
    }

    /**
     * Achievement entries, whichever type the writer used for the numbers.
     *
     * An entry with no usable id cannot be merged against anything, so it is dropped rather than
     * carried as a phantom.
     */
    private fun readAchievements(raw: Any?): List<AchievementProgress> =
        (raw as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val id = readInt(map["id"]) ?: return@mapNotNull null
            val progress = readInt(map["progress"]) ?: return@mapNotNull null
            AchievementProgress(
                id = id,
                progress = progress,
                maxProgress = readInt(map["maxProgress"]) ?: progress,
                title = map["title"] as? String ?: "",
            )
        } ?: emptyList()

    private fun readStatistics(raw: Any?): Map<Int, Long> =
        (raw as? Map<*, *>)?.mapNotNull { (key, value) ->
            val id = when (key) {
                is String -> key.toIntOrNull()
                is Number -> key.toInt()
                else -> null
            } ?: return@mapNotNull null
            val progress = readLong(value) ?: return@mapNotNull null
            id to progress
        }?.toMap() ?: emptyMap()

    private fun readInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun readLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    // ---- Writing -----------------------------------------------------------------------------

    /**
     * The body for a `set(..., SetOptions.merge())`.
     *
     * Only the fields this build owns appear. `chats` is absent on purpose - `ChatHistoryManager`
     * owns it - and so is `entitlements.windows`, which the PC owns.
     */
    fun toFirestoreMap(snapshot: ProgressSnapshot): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            XP to snapshot.xp,
            LEVEL to snapshot.level,
            STREAK to snapshot.streak,
            LAST_UPDATED to FieldValue.serverTimestamp(),
            ENTITLEMENTS to entitlementsMap(snapshot),
        )

        snapshot.streakLastPlay?.let { data[STREAK_LAST_PLAY] = it }
        snapshot.notes?.let { data[NOTES] = it }

        data[ACHIEVEMENTS] = snapshot.achievements.map { achievement ->
            mapOf(
                // A number, because that is what Windows now writes and expects. It reads a string
                // id too, but writing one back would leave the field's type flip-flopping with
                // whichever device synced last.
                "id" to achievement.id,
                "title" to achievement.title,
                "progress" to achievement.progress,
                "maxProgress" to achievement.maxProgress,
            )
        }

        // String keys, which is what Windows reads them as.
        data[STATISTICS] = snapshot.statistics.entries.associate { (id, progress) ->
            id.toString() to progress
        }

        return data
    }

    /**
     * The two sub-keys this platform owns.
     *
     * `windows` is never written: it is read and displayed, but speaking for a device this app
     * knows nothing about is exactly what the co-ownership rule forbids. `SetOptions.merge()`
     * deep-merges this map, so the PC's key survives.
     *
     * **`ultimate` is written only when true.** A false local flag means "no Ultimate seen" - the
     * document has not been read yet, or the phone is offline - and writing that over a true would
     * revoke a purchase made on the PC.
     */
    private fun entitlementsMap(snapshot: ProgressSnapshot): Map<String, Any> = buildMap {
        put(ANDROID_TIER, snapshot.androidTier ?: "free")
        if (snapshot.ultimate) put(ULTIMATE, true)
    }
}
