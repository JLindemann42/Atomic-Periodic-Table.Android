package com.jlindemann.science.sync

import android.content.Context
import android.util.Log
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.preferences.LastSyncPreference
import com.jlindemann.science.utils.EntitlementManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the account document in step without the user having to ask.
 *
 * Signing in is the only thing the user should have to do. There is no sync button, because a
 * button that must be pressed for progress to be safe is a button that will eventually not be
 * pressed. [com.jlindemann.science.AtomicApplication] drives this from the process lifecycle:
 * once when the app comes to the foreground, once when it leaves.
 *
 * A port of the Windows app's CloudSync auto-sync, minus its five-minute timer - a phone that is
 * synced on foreground and background has nothing left to catch, and a ticking timer would spend
 * battery to prove it.
 */
object AutoSync {
    private const val TAG = "AutoSync"

    /**
     * How stale a sync must be before an unforced request will run another.
     *
     * Foregrounding twice in a minute is a normal thing for a user to do; syncing twice for it is
     * not.
     */
    private const val MINIMUM_INTERVAL_MS = 2 * 60 * 1000L

    /**
     * What a request did.
     *
     * [SKIPPED] is deliberately distinct from [FAILED]: a caller showing an error must not show one
     * for a sync that never ran.
     */
    enum class Outcome { SKIPPED, SUCCESS, FAILED }

    private val running = AtomicBoolean(false)

    @Volatile
    private var lastAttempt = 0L

    /**
     * Whether a full progress sync could do anything, without attempting one.
     *
     * Note this is *not* the condition for talking to the document at all - see
     * [ProgressSyncManager.refreshEntitlements], which runs for any signed-in user.
     */
    fun canSync(context: Context): Boolean =
        AuthManager.isSignedIn() && EntitlementManager.hasAnyPro(context)

    /**
     * Sync unless one just ran.
     *
     * Single-flight: a second caller returns [Outcome.SKIPPED] rather than starting a competing
     * read-merge-write against the same document.
     */
    fun syncNow(
        context: Context,
        force: Boolean = false,
        onOutcome: ((Outcome) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext

        // Signed in is the only hard requirement to look at the document. The tier decides whether
        // anything is written back.
        val uid = AuthManager.getUid()
        if (!AuthManager.isSignedIn() || uid == null) {
            onOutcome?.invoke(Outcome.SKIPPED)
            return
        }

        val now = System.currentTimeMillis()
        if (!force && now - lastAttempt < MINIMUM_INTERVAL_MS) {
            onOutcome?.invoke(Outcome.SKIPPED)
            return
        }

        if (!running.compareAndSet(false, true)) {
            onOutcome?.invoke(Outcome.SKIPPED)
            return
        }
        lastAttempt = now

        try {
            if (canSync(appContext)) {
                runFullSync(appContext, uid, onOutcome)
            } else {
                // Free account. Read the licence keys first - an Ultimate bought on the PC turns
                // this into a paid account, and then the full sync is allowed to run after all.
                ProgressSyncManager.refreshEntitlements(appContext, uid) { read ->
                    if (read && canSync(appContext)) {
                        Log.d(TAG, "Ultimate granted from the account, syncing")
                        runFullSync(appContext, uid, onOutcome)
                    } else {
                        running.set(false)
                        onOutcome?.invoke(Outcome.SKIPPED)
                    }
                }
            }
        } catch (t: Throwable) {
            // The callbacks are the only thing that clear the flag, so a throw before a request is
            // dispatched would wedge every later sync.
            running.set(false)
            Log.w(TAG, "Auto-sync threw", t)
            onOutcome?.invoke(Outcome.FAILED)
        }
    }

    /** Caller holds [running]; this releases it. */
    private fun runFullSync(
        appContext: Context,
        uid: String,
        onOutcome: ((Outcome) -> Unit)?,
    ) {
        // The tools' saved items own their own part of the document and their own single-flight,
        // so they ride along rather than holding up the progress result.
        FavoritesSyncManager.sync(appContext)

        ProgressSyncManager.mergeAndUploadLocalProgress(appContext, uid) { success ->
            running.set(false)
            if (success) {
                LastSyncPreference(appContext).setValue(System.currentTimeMillis())
            }
            Log.d(TAG, if (success) "Auto-sync complete" else "Auto-sync failed")
            onOutcome?.invoke(if (success) Outcome.SUCCESS else Outcome.FAILED)
        }
    }

    /**
     * Forget the throttle.
     *
     * Called on sign-out so that signing in as somebody else syncs immediately rather than waiting
     * out the previous account's interval.
     */
    fun reset() {
        lastAttempt = 0L
    }
}
