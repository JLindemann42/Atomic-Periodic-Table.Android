package com.jlindemann.science

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.jlindemann.science.sync.AutoSync

/**
 * Process-wide setup.
 *
 * Firebase initialisation used to run in BaseActivity.onCreate, once per activity. Firestore
 * settings in particular can only be applied before the first use of the instance, so on every
 * activity after the first the assignment threw and was swallowed - it worked only because the
 * first activity happened to win the race. Doing it here removes the race.
 */
class AtomicApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            FirebaseApp.initializeApp(this)
        } catch (t: Throwable) {
            // Safe fallback if initialization already happened or the config is missing.
            t.printStackTrace()
        }

        try {
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
        } catch (t: Throwable) {
            // Ignore if Firestore is not available.
            t.printStackTrace()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(SyncOnForegroundAndBackground())
    }

    /**
     * Sync when the app is opened and when it is put away.
     *
     * On foreground so a device picks up whatever another one did while this was closed; on
     * background so the work the user just did is safe before the process can be killed.
     * [AutoSync] throttles and de-duplicates, so this is safe to fire on every transition.
     */
    private inner class SyncOnForegroundAndBackground : DefaultLifecycleObserver {

        override fun onStart(owner: LifecycleOwner) {
            AutoSync.syncNow(this@AtomicApplication)
        }

        override fun onStop(owner: LifecycleOwner) {
            // Forced: the throttle exists to stop repeated foregrounding from syncing repeatedly,
            // not to let the app close on unsaved progress.
            AutoSync.syncNow(this@AtomicApplication, force = true)
        }
    }
}
