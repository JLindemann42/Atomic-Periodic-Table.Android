package com.jlindemann.science.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.preferences.NotesPreference
import com.jlindemann.science.utils.EntitlementManager

/**
 * Manager for syncing notes to Firebase with optimized debouncing to minimize calls.
 */
object NotesSyncManager {
    private const val TAG = "NotesSyncManager"
    private const val SYNC_DELAY_MS = 2000L // 2 seconds debounce delay

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSyncRunnable: Runnable? = null
    private var isSyncing = false

    private fun notifyStatusOnMain(onSyncStatusChange: ((SyncStatus) -> Unit)?, status: SyncStatus) {
        if (onSyncStatusChange == null) return
        handler.post {
            try {
                onSyncStatusChange.invoke(status)
            } catch (e: Exception) {
                Log.w(TAG, "Exception while invoking status callback", e)
            }
        }
    }

    /**
     * Check if user is eligible for notes sync (logged in AND has PRO, PRO+ or Ultimate).
     *
     * Ultimate is included because it grants PRO+, which [EntitlementManager.hasAnyPro] already
     * knows - the point of routing this through the manager rather than reading the two
     * preferences directly is that a user whose Ultimate came from the PC has no Play purchase to
     * show for it.
     */
    fun canSyncNotes(context: Context): Boolean {
        if (!AuthManager.isSignedIn()) return false
        return EntitlementManager.hasAnyPro(context)
    }

    /**
     * Request a sync with debouncing to minimize Firebase calls.
     * If another sync is requested within SYNC_DELAY_MS, it cancels the previous one.
     */
    fun requestSync(context: Context, onSyncStatusChange: ((SyncStatus) -> Unit)? = null) {
        // Cancel any pending sync
        pendingSyncRunnable?.let { handler.removeCallbacks(it) }

        // Check if user can sync
        if (!canSyncNotes(context)) {
            notifyStatusOnMain(onSyncStatusChange, SyncStatus.NOT_ELIGIBLE)
            return
        }

        // If already syncing, just notify status
        if (isSyncing) {
            notifyStatusOnMain(onSyncStatusChange, SyncStatus.SYNCING)
            return
        }

        // Schedule a new sync after delay
        val appContext = context.applicationContext
        pendingSyncRunnable = Runnable {
            performSync(appContext, onSyncStatusChange)
        }
        handler.postDelayed(pendingSyncRunnable!!, SYNC_DELAY_MS)
    }

    /**
     * Perform the actual sync operation.
     *
     * A single write of the notes field. It used to read the whole document first so it could pass
     * the cloud's XP and level back into a full write; with SetOptions.merge() touching only the
     * keys present, the fields it was protecting were never in danger, and the read was a round
     * trip on every pause in typing.
     */
    private fun performSync(context: Context, onSyncStatusChange: ((SyncStatus) -> Unit)?) {
        isSyncing = true
        notifyStatusOnMain(onSyncStatusChange, SyncStatus.SYNCING)
        val uid = AuthManager.getUid()
        if (uid == null) {
            Log.w(TAG, "User UID is null, cannot sync")
            isSyncing = false
            notifyStatusOnMain(onSyncStatusChange, SyncStatus.NOT_ELIGIBLE)
            return
        }

        val notes = NotesPreference(context).getValue()

        ProgressSyncManager.saveNotesToCloud(context, uid, notes) { success, exception ->
            isSyncing = false
            if (success) {
                Log.d(TAG, "Notes synced successfully")
                notifyStatusOnMain(onSyncStatusChange, SyncStatus.SYNCED)
            } else {
                Log.e(TAG, "Failed to sync notes", exception)
                notifyStatusOnMain(onSyncStatusChange, SyncStatus.ERROR)
            }
        }
    }

    /**
     * Cancel any pending sync operations.
     */
    fun cancelPendingSync() {
        pendingSyncRunnable?.let { handler.removeCallbacks(it) }
        pendingSyncRunnable = null
    }

    enum class SyncStatus {
        NOT_ELIGIBLE,  // User not logged in or doesn't have PRO/PRO+/Ultimate
        SYNCING,       // Currently syncing
        SYNCED,        // Successfully synced
        ERROR          // Error during sync
    }
}
