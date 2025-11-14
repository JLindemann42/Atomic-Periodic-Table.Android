package com.jlindemann.science.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.preferences.NotesPreference
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion

/**
 * Manager for syncing notes to Firebase with optimized debouncing to minimize calls.
 */
object NotesSyncManager {
    private const val TAG = "NotesSyncManager"
    private const val SYNC_DELAY_MS = 2000L // 2 seconds debounce delay
    
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSyncRunnable: Runnable? = null
    private var isSyncing = false
    
    /**
     * Check if user is eligible for notes sync (logged in AND has Pro or Pro+ version).
     */
    fun canSyncNotes(context: Context): Boolean {
        val isSignedIn = AuthManager.isSignedIn()
        if (!isSignedIn) return false
        
        val proPref = ProVersion(context)
        val proPlusPreference = ProPlusVersion(context)
        val hasPro = proPref.getValue() == 100
        val hasProPlus = proPlusPreference.getValue() == 100
        
        return hasPro || hasProPlus
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
            onSyncStatusChange?.invoke(SyncStatus.NOT_ELIGIBLE)
            return
        }
        
        // If already syncing, just notify status
        if (isSyncing) {
            onSyncStatusChange?.invoke(SyncStatus.SYNCING)
            return
        }
        
        // Schedule a new sync after delay
        pendingSyncRunnable = Runnable {
            performSync(context, onSyncStatusChange)
        }
        handler.postDelayed(pendingSyncRunnable!!, SYNC_DELAY_MS)
    }
    
    /**
     * Perform the actual sync operation.
     */
    private fun performSync(context: Context, onSyncStatusChange: ((SyncStatus) -> Unit)?) {
        isSyncing = true
        onSyncStatusChange?.invoke(SyncStatus.SYNCING)
        
        val uid = AuthManager.getUid()
        if (uid == null) {
            Log.w(TAG, "User UID is null, cannot sync")
            isSyncing = false
            onSyncStatusChange?.invoke(SyncStatus.NOT_ELIGIBLE)
            return
        }
        
        // Get current notes
        val notesPref = NotesPreference(context)
        val notes = notesPref.getValue()
        
        // Load current cloud progress to get existing xp/level
        ProgressSyncManager.loadCloudProgress(uid) { cloudProgress ->
            if (cloudProgress != null) {
                // Use existing cloud xp/level so we don't overwrite them
                ProgressSyncManager.saveFullProgressToCloud(
                    uid = uid,
                    xp = cloudProgress.xp,
                    level = cloudProgress.level,
                    notes = notes
                ) { success, exception ->
                    isSyncing = false
                    if (success) {
                        Log.d(TAG, "Notes synced successfully")
                        onSyncStatusChange?.invoke(SyncStatus.SYNCED)
                    } else {
                        Log.e(TAG, "Failed to sync notes", exception)
                        onSyncStatusChange?.invoke(SyncStatus.ERROR)
                    }
                }
            } else {
                // No cloud progress exists, create initial entry with notes only
                ProgressSyncManager.saveFullProgressToCloud(
                    uid = uid,
                    xp = 0L,
                    level = 1,
                    notes = notes
                ) { success, exception ->
                    isSyncing = false
                    if (success) {
                        Log.d(TAG, "Notes synced successfully (initial)")
                        onSyncStatusChange?.invoke(SyncStatus.SYNCED)
                    } else {
                        Log.e(TAG, "Failed to sync notes", exception)
                        onSyncStatusChange?.invoke(SyncStatus.ERROR)
                    }
                }
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
        NOT_ELIGIBLE,  // User not logged in or doesn't have Pro/Pro+
        SYNCING,       // Currently syncing
        SYNCED,        // Successfully synced
        ERROR          // Error during sync
    }
}
