package com.jlindemann.science.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.utils.EntitlementManager

/**
 * Shares the tools' saved items with the user's other devices - this phone, another phone, the PC -
 * through `users/{uid}.favorites`.
 *
 * The format and the merge are [FavoritesDocument], pure and unit tested; the local half is
 * [FavoritesStore]. What lives here is the round trip: read the document, merge each tool's list
 * with this device's, and write back only when the merge changed something.
 *
 * Gated as saving them is: signed in, and PRO or PRO+ (Ultimate included). A free user cannot save
 * favourites, so has nothing to sync.
 */
object FavoritesSyncManager {
    private const val TAG = "FavoritesSyncManager"

    /** A burst of saves and removals becomes one sync. */
    private const val SYNC_DELAY_MS = 2000L

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var syncing = false
    private var again = false

    fun canSync(context: Context): Boolean =
        AuthManager.isSignedIn() && EntitlementManager.hasAnyPro(context)

    /** Sync shortly, after a save or removal. */
    fun requestSync(context: Context) {
        if (!canSync(context)) return
        val appContext = context.applicationContext
        pending?.let { handler.removeCallbacks(it) }
        pending = Runnable { sync(appContext) }.also { handler.postDelayed(it, SYNC_DELAY_MS) }
    }

    /**
     * Merge this device's saved items with the shared ones and write the result back.
     *
     * A failed read reports false and changes nothing. It must never be taken for an empty cloud: the
     * merge would then write this device's lists over the other device's. A missing document - a
     * first sign-in - really is empty, and the merge uploads what this device has.
     *
     * Single-flight. A request that arrives while a sync is running runs once more after it, so a
     * change made mid-sync is never left behind.
     */
    fun sync(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        val uid = AuthManager.getUid()
        if (!canSync(appContext) || uid == null) {
            onComplete?.invoke(false)
            return
        }
        if (syncing) {
            again = true
            onComplete?.invoke(false)
            return
        }
        syncing = true

        val doc = db.collection("users").document(uid)
        doc.get()
            .addOnSuccessListener { snap ->
                val cloud = try {
                    FavoritesDocument.fromField(if (snap.exists()) snap.get(FavoritesDocument.FAVORITES) else null)
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not read cloud favourites", t)
                    finish(appContext, false, onComplete)
                    return@addOnSuccessListener
                }

                val now = System.currentTimeMillis()
                var differs = false
                val merged = FavoriteKind.values().associateWith { kind ->
                    val result = FavoritesDocument.merge(FavoritesStore.local(appContext, kind), cloud.getValue(kind), now)
                    FavoritesStore.apply(appContext, kind, result)
                    differs = differs || FavoritesDocument.differs(result, cloud.getValue(kind))
                    result
                }

                if (!differs) {
                    finish(appContext, true, onComplete)
                    return@addOnSuccessListener
                }

                // merge() deep-merges the favorites map, so any other key under it survives; only
                // the three lists are replaced.
                doc.set(mapOf(FavoritesDocument.FAVORITES to FavoritesDocument.toField(merged)), SetOptions.merge())
                    .addOnSuccessListener { finish(appContext, true, onComplete) }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Could not write favourites", e)
                        finish(appContext, false, onComplete)
                    }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Could not read the account document", e)
                finish(appContext, false, onComplete)
            }
    }

    private fun finish(context: Context, success: Boolean, onComplete: ((Boolean) -> Unit)?) {
        syncing = false
        onComplete?.invoke(success)
        if (again) {
            again = false
            sync(context)
        }
    }
}
