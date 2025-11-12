package com.jlindemann.science.sync

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.util.XpManager
import com.jlindemann.science.utils.StreakManager

object ProgressSyncManager {
    private const val TAG = "ProgressSyncManager"
    private val db by lazy { FirebaseFirestore.getInstance() }

    data class CloudAchievement(val id: Int = 0, val progress: Int = 0, val maxProgress: Int? = null)
    data class CloudProgress(
        val xp: Long = 0L,
        val level: Int = 1,
        val lastUpdated: Timestamp? = null,
        val achievements: List<CloudAchievement>? = null,
        val statistics: Map<Int, Long>? = null,
        val streak: Long? = null,
        val streakLastPlay: String? = null  // ISO date string (yyyy-MM-dd)
    )

    private fun docRefForUid(uid: String) = db.collection("users").document(uid)

    /**
     * Load cloud progress for uid and call callback. If document not present, callback with null.
     * Also attempts to read achievements (list of maps), statistics (map of id->progress) and streak.
     */
    fun loadCloudProgress(uid: String, onLoaded: (CloudProgress?) -> Unit) {
        docRefForUid(uid).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    try {
                        val xp = snap.getLong("xp") ?: 0L
                        val level = snap.getLong("level")?.toInt() ?: 1
                        val last = snap.getTimestamp("lastUpdated")

                        val cloudAchievements: List<CloudAchievement>? = (snap.get("achievements") as? List<*>)?.mapNotNull { item ->
                            (item as? Map<*, *>)?.let { map ->
                                val idAny = map["id"]
                                val progAny = map["progress"]
                                val maxAny = map["maxProgress"]
                                val id = when (idAny) {
                                    is Number -> idAny.toInt()
                                    is String -> idAny.toIntOrNull()
                                    else -> null
                                }
                                val prog = when (progAny) {
                                    is Number -> progAny.toInt()
                                    is String -> progAny.toIntOrNull()
                                    else -> null
                                }
                                val maxP = when (maxAny) {
                                    is Number -> maxAny.toInt()
                                    is String -> maxAny.toIntOrNull()
                                    else -> null
                                }
                                if (id != null && prog != null) {
                                    CloudAchievement(id = id, progress = prog, maxProgress = maxP)
                                } else null
                            }
                        }

                        val cloudStats: Map<Int, Long>? = (snap.get("statistics") as? Map<*, *>)?.mapNotNull { entry ->
                            val key = entry.key
                            val value = entry.value
                            val id = when (key) {
                                is String -> key.toIntOrNull()
                                is Number -> key.toInt()
                                else -> null
                            }
                            val progLong = when (value) {
                                is Number -> value.toLong()
                                is String -> value.toLongOrNull()
                                else -> null
                            }
                            if (id != null && progLong != null) Pair(id, progLong) else null
                        }?.toMap()

                        val cloudStreak: Long? = when (val s = snap.get("streak")) {
                            is Number -> s.toLong()
                            is String -> s.toLongOrNull()
                            else -> null
                        }

                        val cloudStreakLastPlay: String? = snap.getString("streakLastPlay")

                        onLoaded(CloudProgress(xp = xp, level = level, lastUpdated = last, achievements = cloudAchievements, statistics = cloudStats, streak = cloudStreak, streakLastPlay = cloudStreakLastPlay))
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to parse cloud progress", t)
                        onLoaded(null)
                    }
                } else {
                    onLoaded(null)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "loadCloudProgress failed", e)
                onLoaded(null)
            }
    }

    /**
     * Write progress and optionally achievements/statistics/streak/streakLastPlay to cloud (merge). lastUpdated will be server timestamp.
     */
    fun saveFullProgressToCloud(
        uid: String,
        xp: Long,
        level: Int,
        achievements: List<Achievement>? = null,
        statistics: List<Statistics>? = null,
        streak: Int? = null,
        streakLastPlay: String? = null,
        onComplete: ((Boolean, Exception?) -> Unit)? = null
    ) {
        val data = mutableMapOf<String, Any>(
            "xp" to xp,
            "level" to level,
            "lastUpdated" to FieldValue.serverTimestamp()
        )

        achievements?.let { list ->
            val achMaps = list.map { ach ->
                mapOf(
                    "id" to ach.id,
                    "title" to ach.title,
                    "progress" to ach.progress,
                    "maxProgress" to ach.maxProgress
                )
            }
            data["achievements"] = achMaps
        }

        statistics?.let { stats ->
            val statMap = stats.associate { stat ->
                stat.id.toString() to stat.progress
            }
            data["statistics"] = statMap
        }

        streak?.let {
            data["streak"] = it
        }

        streakLastPlay?.let {
            data["streakLastPlay"] = it
        }

        docRefForUid(uid).set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke(true, null) }
            .addOnFailureListener { e -> onComplete?.invoke(false, e) }
    }

    /**
     * Merge strategy (improved):
     * - For xp: keep the larger xp (cloud or local).
     * - For achievements/statistics: per-item, take the maximum progress between local and cloud.
     * - For streak: take the maximum of local and cloud.
     *
     * After computing merged state, write it to cloud and then update local storage:
     * - XP/local level will be updated to mergedXp (only increase local if merged is greater).
     * - Achievements/statistics are increased locally to match merged values (never reduced).
     * - Streak is increased locally if merged is greater.
     */
    fun mergeAndUploadLocalProgress(context: Context, uid: String, onComplete: ((Boolean) -> Unit)? = null) {
        // read local xp/level
        val localXp = XpManager.getXp(context)
        val localLevel = XpManager.getLevel(localXp)

        // read local achievements & statistics using existing model getters
        val localAchievements = ArrayList<Achievement>()
        AchievementModel.getList(context, localAchievements)
        // Ensure each item's progress is loaded (in case getList doesn't)
        localAchievements.forEach { it.loadProgress(context) }

        val localStatistics = ArrayList<Statistics>()
        StatisticsModel.getList(context, localStatistics)
        localStatistics.forEach { it.loadProgress(context) }

        // read local streak and last play date
        val localStreak = try {
            StreakManager.getCurrentStreak(context)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read local streak", t)
            0
        }
        val localLastPlayDate = try {
            StreakManager.getLastPlayDate(context)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read local last play date", t)
            null
        }

        loadCloudProgress(uid) { cloud ->
            try {
                // merged XP is per-item max
                val cloudXp = cloud?.xp ?: 0L
                val mergedXp: Long = maxOf(localXp.toLong(), cloudXp)
                // determine merged level from XP to ensure consistency
                val mergedLevel: Int = try {
                    XpManager.getLevel(mergedXp.toInt())
                } catch (_: Throwable) {
                    // fallback: prefer cloud level if available, else local
                    cloud?.level ?: localLevel
                }

                // Merge achievements: per-id max progress and pick sensible metadata
                val localById = localAchievements.associateBy { it.id }.toMutableMap()
                val cloudById = cloud?.achievements?.associateBy { it.id } ?: emptyMap()

                // collect all ids
                val allIds = (localById.keys + cloudById.keys).toSet()
                val mergedAchievements = allIds.map { id ->
                    val local = localById[id]
                    val cloudA = cloudById[id]

                    val localProg = local?.progress ?: 0
                    val cloudProg = cloudA?.progress ?: 0
                    val prog = maxOf(localProg, cloudProg)

                    val maxProgress = maxOf(local?.maxProgress ?: prog, cloudA?.maxProgress ?: prog)
                    val title = local?.title ?: ("achievement_$id")
                    val description = local?.description ?: ""

                    Achievement(id, title, description, prog, maxProgress)
                }

                // Merge statistics: per-id max progress
                val localStatsById = localStatistics.associateBy { it.id }.toMutableMap()
                val cloudStatsById = cloud?.statistics ?: emptyMap()
                val allStatIds = (localStatsById.keys + cloudStatsById.keys).toSet()
                val mergedStatistics = allStatIds.map { id ->
                    val local = localStatsById[id]
                    val cloudProgLong = cloudStatsById[id] ?: 0L
                    val cloudProg = cloudProgLong.toInt()
                    val localProg = local?.progress ?: 0
                    val prog = maxOf(localProg, cloudProg)
                    val title = local?.title ?: "stat_$id"
                    Statistics(id, title, prog)
                }

                // Merge streak: use max streak, but prefer the last play date from the higher streak
                val cloudStreakInt = cloud?.streak?.toInt() ?: 0
                val mergedStreak: Int
                var mergedLastPlayDate: String?
                
                if (localStreak > cloudStreakInt) {
                    // Local streak is higher, use local values
                    mergedStreak = localStreak
                    mergedLastPlayDate = localLastPlayDate
                } else if (cloudStreakInt > localStreak) {
                    // Cloud streak is higher, use cloud values
                    mergedStreak = cloudStreakInt
                    mergedLastPlayDate = cloud?.streakLastPlay
                } else {
                    // Equal streaks - prefer the most recent last play date
                    mergedStreak = localStreak
                    if (localLastPlayDate != null && cloud?.streakLastPlay != null) {
                        // Compare dates, use the more recent one
                        try {
                            val localDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(localLastPlayDate)
                            val cloudDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(cloud.streakLastPlay)
                            mergedLastPlayDate = if (localDate!!.after(cloudDate)) localLastPlayDate else cloud.streakLastPlay
                        } catch (e: Exception) {
                            mergedLastPlayDate = localLastPlayDate
                        }
                    } else {
                        mergedLastPlayDate = localLastPlayDate ?: cloud?.streakLastPlay
                    }
                }

                // persist merged to cloud (write full state)
                saveFullProgressToCloud(uid, mergedXp, mergedLevel, mergedAchievements, mergedStatistics, mergedStreak, mergedLastPlayDate) { success, _ ->
                    if (success) {
                        try {
                            // Update local XP if mergedXp is greater than local
                            if (mergedXp > localXp.toLong()) {
                                try {
                                    // Prefer an explicit setter if available
                                    XpManager.setXp(context, mergedXp.toInt())
                                } catch (t: Throwable) {
                                    // Fallback to adding the difference if setXp not available
                                    try {
                                        val diff = (mergedXp - localXp.toLong()).toInt()
                                        if (diff > 0) XpManager.addXp(context, diff)
                                    } catch (_: Throwable) {
                                        Log.w(TAG, "Unable to update local XP to merged value", t)
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to update local XP", t)
                        }

                        // Achievements: for each merged achievement, if merged.progress > current local progress -> increment by diff
                        val localByIdMutable = localAchievements.associateBy { it.id }.toMutableMap()
                        mergedAchievements.forEach { mergedAch ->
                            val local = localByIdMutable[mergedAch.id]
                            if (local != null) {
                                val current = local.progress
                                val target = mergedAch.progress
                                val diff = target - current
                                if (diff > 0) {
                                    // incrementProgress will save progress
                                    local.incrementProgress(context, diff)
                                }
                            } else {
                                // create new and set progress
                                val newAch = Achievement(mergedAch.id, mergedAch.title, mergedAch.description, 0, mergedAch.maxProgress)
                                val diff = mergedAch.progress - 0
                                if (diff > 0) newAch.incrementProgress(context, diff)
                            }
                        }

                        // Statistics: similar - only increase via incrementProgress
                        val localStatsByIdMutable = localStatistics.associateBy { it.id }.toMutableMap()
                        mergedStatistics.forEach { mergedStat ->
                            val local = localStatsByIdMutable[mergedStat.id]
                            if (local != null) {
                                val current = local.progress
                                val target = mergedStat.progress
                                val diff = target - current
                                if (diff > 0) {
                                    local.incrementProgress(context, diff)
                                }
                            } else {
                                val newStat = Statistics(mergedStat.id, mergedStat.title, 0)
                                val diff = mergedStat.progress - 0
                                if (diff > 0) newStat.incrementProgress(context, diff)
                            }
                        }

                        // Streak: update local streak with merged value and date
                        // Use setCurrentStreakWithDate to ensure proper validation and date tracking
                        try {
                            StreakManager.setCurrentStreakWithDate(context, mergedStreak, mergedLastPlayDate)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to update local streak", t)
                        }

                        onComplete?.invoke(true)
                    } else {
                        onComplete?.invoke(false)
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                onComplete?.invoke(false)
            }
        }
    }
}