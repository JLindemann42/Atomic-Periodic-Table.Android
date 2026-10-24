package com.jlindemann.science.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.preferences.NotesPreference
import com.jlindemann.science.preferences.WindowsTierPreference
import com.jlindemann.science.util.XpManager
import com.jlindemann.science.utils.EntitlementManager
import com.jlindemann.science.utils.StreakManager

/**
 * Reads and writes users/{uid}, and reconciles it with local progress.
 *
 * The merge itself is [ProgressMerge] and [NotesCodec] - pure, and unit tested. What lives here is
 * only transport and the local read/write, which keeps the part that can destroy data separable
 * from the part that can fail on a network.
 */
object ProgressSyncManager {
    private const val TAG = "ProgressSyncManager"
    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun docRefForUid(uid: String) = db.collection("users").document(uid)

    // ---- Transport ---------------------------------------------------------------------------

    /**
     * Load the account document.
     *
     * A missing document is [ProgressSnapshot.EMPTY] - a first sign-in has nothing yet, which is
     * the empty side of the merge rather than an error. Null means the read itself failed, and a
     * caller must not treat that as "the cloud is empty" and upload over it.
     */
    fun loadCloudProgress(uid: String, onLoaded: (ProgressSnapshot?) -> Unit) {
        docRefForUid(uid).get()
            .addOnSuccessListener { snap ->
                onLoaded(
                    try {
                        ProgressDocument.fromSnapshot(snap)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to parse cloud progress", t)
                        null
                    }
                )
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "loadCloudProgress failed", e)
                onLoaded(null)
            }
    }

    /**
     * Write a snapshot.
     *
     * Always with [SetOptions.merge]. A bare set() replaces the whole document, and this document
     * is co-owned with the Windows app: a field this build has never heard of would be deleted from
     * the user's PC. See [ProgressDocument].
     */
    fun saveProgressToCloud(
        uid: String,
        snapshot: ProgressSnapshot,
        onComplete: ((Boolean, Exception?) -> Unit)? = null,
    ) {
        docRefForUid(uid).set(ProgressDocument.toFirestoreMap(snapshot), SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke(true, null) }
            .addOnFailureListener { e -> onComplete?.invoke(false, e) }
    }

    /**
     * Write just the notes blob, plus this platform's entitlements.
     *
     * Notes are synced on a debounce while the user types, so this deliberately does not read the
     * document first and does not merge: SetOptions.merge() touches only the keys present, so the
     * XP and achievements the read used to exist to preserve were never at risk. It also does not
     * apply anything locally - the merge rule is "longer note wins", and running it against a note
     * the user is in the middle of shortening would undo their deletion as they typed.
     */
    fun saveNotesToCloud(
        context: Context,
        uid: String,
        notes: String,
        onComplete: ((Boolean, Exception?) -> Unit)? = null,
    ) {
        val data = mapOf(
            ProgressDocument.NOTES to notes,
            ProgressDocument.LAST_UPDATED to FieldValue.serverTimestamp(),
            ProgressDocument.ENTITLEMENTS to buildMap {
                put(ProgressDocument.ANDROID_TIER, EntitlementManager.currentTier(context).wire)
                if (EntitlementManager.hasUltimate(context)) put(ProgressDocument.ULTIMATE, true)
            },
        )

        docRefForUid(uid).set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke(true, null) }
            .addOnFailureListener { e -> onComplete?.invoke(false, e) }
    }

    // ---- The sync ----------------------------------------------------------------------------

    /**
     * Merge local and cloud progress, write the result back, then move local state forward.
     *
     * The merge is monotonic, so running this twice is harmless and running it on every launch is
     * safe.
     */
    fun mergeAndUploadLocalProgress(
        context: Context,
        uid: String,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val local = readLocal(context)

        loadCloudProgress(uid) { cloud ->
            if (cloud == null) {
                // The read failed. Uploading local state now would merge against an empty document
                // and could push a fresh install's zeroes over real progress.
                onComplete?.invoke(false)
                return@loadCloudProgress
            }

            try {
                val merged = ProgressMerge.merge(local, cloud)

                saveProgressToCloud(uid, merged) { success, _ ->
                    if (success) {
                        applyLocally(context, local, merged)
                        onComplete?.invoke(true)
                    } else {
                        onComplete?.invoke(false)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Merge failed", t)
                onComplete?.invoke(false)
            }
        }
    }

    // ---- Local state -------------------------------------------------------------------------

    /** Everything this device would contribute to the merge. */
    fun readLocal(context: Context): ProgressSnapshot {
        val xp = XpManager.getXp(context)

        val achievements = ArrayList<Achievement>()
        AchievementModel.getList(context, achievements)
        achievements.forEach { it.loadProgress(context) }

        val statistics = ArrayList<Statistics>()
        StatisticsModel.getList(context, statistics)
        statistics.forEach { it.loadProgress(context) }

        val streak = try {
            StreakManager.getCurrentStreak(context)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read local streak", t)
            0
        }
        val lastPlay = try {
            StreakManager.getLastPlayDate(context)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read local last play date", t)
            null
        }

        return ProgressSnapshot(
            xp = xp.toLong(),
            level = XpManager.getLevel(xp),
            streak = streak,
            streakLastPlay = lastPlay,
            achievements = achievements.map {
                AchievementProgress(it.id, it.progress, it.maxProgress, it.title)
            },
            statistics = statistics.associate { it.id to it.progress.toLong() },
            notes = NotesPreference(context).getValue(),

            // Reported per platform so the PC can tell "PRO+ on Windows" from "PRO+ on Android".
            // Without the platform the shared document cannot distinguish two separate purchases
            // from two separate stores.
            androidTier = EntitlementManager.currentTier(context).wire,
            windowsTier = WindowsTierPreference(context).getValue().ifEmpty { null },
            ultimate = EntitlementManager.hasUltimate(context),
        )
    }

    /**
     * Move local state forward to the merged values.
     *
     * Only ever forward. The merge already guarantees it, but writing it as an explicit comparison
     * means a future change to either side cannot quietly start moving a counter backwards.
     */
    private fun applyLocally(context: Context, local: ProgressSnapshot, merged: ProgressSnapshot) {
        if (merged.xp > local.xp) {
            try {
                XpManager.setXp(context, merged.xp.toInt())
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to update local XP", t)
            }
        }

        val achievements = ArrayList<Achievement>()
        AchievementModel.getList(context, achievements)
        achievements.forEach { it.loadProgress(context) }
        val achievementsById = achievements.associateBy { it.id }
        merged.achievements.forEach { mergedAchievement ->
            val current = achievementsById[mergedAchievement.id] ?: return@forEach
            val diff = mergedAchievement.progress - current.progress
            if (diff > 0) current.incrementProgress(context, diff)
        }

        val statistics = ArrayList<Statistics>()
        StatisticsModel.getList(context, statistics)
        statistics.forEach { it.loadProgress(context) }
        val statisticsById = statistics.associateBy { it.id }
        merged.statistics.forEach { (id, progress) ->
            val current = statisticsById[id] ?: return@forEach
            val diff = progress.toInt() - current.progress
            if (diff > 0) current.incrementProgress(context, diff)
        }

        try {
            StreakManager.setCurrentStreakWithDate(context, merged.streak, merged.streakLastPlay)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to update local streak", t)
        }

        try {
            val notesPref = NotesPreference(context)
            val mergedNotes = merged.notes ?: ""
            // Longer wins, matching the merge rule. Writing unconditionally would let a shorter
            // merged value overwrite a local edit made since the merge was computed.
            if (mergedNotes.length > notesPref.getValue().length) notesPref.setValue(mergedNotes)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to update local notes", t)
        }

        applyEntitlements(context, merged)
    }

    /**
     * Take what the document says about the user's licences.
     *
     * Split out of [applyLocally] because [refreshEntitlements] needs exactly this and nothing
     * else: a free user must be able to learn that they own Ultimate without a full merge running.
     */
    private fun applyEntitlements(context: Context, snapshot: ProgressSnapshot) {
        snapshot.windowsTier?.takeIf { it.isNotEmpty() }?.let {
            WindowsTierPreference(context).setValue(it)
        }

        // Ultimate crosses stores: bought on the PC, it unlocks PRO+ here, and Google Play has no
        // purchase to show for it. Grant-only, like every other rule in the merge.
        if (snapshot.ultimate) EntitlementManager.grantUltimate(context)
    }

    /**
     * Read the licence keys and nothing else.
     *
     * Progress and notes sync are a paid feature, so [AutoSync] gates them on owning a tier. That
     * gate cannot also cover this: Ultimate bought on the PC is precisely how a user who has paid
     * nothing on Google Play becomes PRO+ here, so gating the read on already being PRO would make
     * the grant unreachable - the phone would wait for an entitlement it could only learn about by
     * reading.
     *
     * Writes nothing, so a free account contributes no progress to the document.
     *
     * @param onComplete true when the document was read, whatever it contained.
     */
    fun refreshEntitlements(context: Context, uid: String, onComplete: ((Boolean) -> Unit)? = null) {
        loadCloudProgress(uid) { cloud ->
            if (cloud == null) {
                onComplete?.invoke(false)
                return@loadCloudProgress
            }

            try {
                applyEntitlements(context, cloud)
                onComplete?.invoke(true)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to apply cloud entitlements", t)
                onComplete?.invoke(false)
            }
        }
    }
}
