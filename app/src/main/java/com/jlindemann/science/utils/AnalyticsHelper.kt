package com.jlindemann.science.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.jlindemann.science.preferences.AnalyticsPreference

object AnalyticsHelper {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    /** GA4 truncates a string parameter past this, so do it here where it is visible. */
    private const val MAX_PARAM_LENGTH = 100

    fun initialize(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = Firebase.analytics
        }
        updateAnalyticsCollection(context)
    }

    fun updateAnalyticsCollection(context: Context) {
        val analyticsPreference = AnalyticsPreference(context)
        val isEnabled = analyticsPreference.getValue()
        firebaseAnalytics?.setAnalyticsCollectionEnabled(isEnabled)
    }

    fun logScreenView(context: Context, screenName: String, screenClass: String) {
        if (!isEnabled(context)) return

        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    fun logEvent(context: Context, eventName: String, params: Bundle? = null) {
        if (!isEnabled(context)) return

        firebaseAnalytics?.logEvent(eventName, params)
    }

    /**
     * Log an event without building a [Bundle] at the call site.
     *
     * Null values are dropped rather than written as the string "null", so an optional parameter
     * simply does not appear on the event.
     */
    fun logEvent(context: Context, eventName: String, vararg params: Pair<String, Any?>) {
        if (!isEnabled(context)) return

        firebaseAnalytics?.logEvent(eventName, bundleOf(params))
    }

    /**
     * Record entering a fragment.
     *
     * Sends both a standard `screen_view` — so the console's Screens report names the tab instead
     * of reporting all five as `Main` — and a countable [AnalyticsEvent.FRAGMENT_VIEW], which can
     * be segmented by fragment and entry source without filtering `screen_view` away from the
     * thirty activities that also emit it.
     */
    fun logFragmentView(
        context: Context,
        fragmentName: String,
        fragmentClass: String,
        entrySource: String
    ) {
        if (!isEnabled(context)) return

        logScreenView(context, fragmentName, fragmentClass)
        logEvent(
            context,
            AnalyticsEvent.FRAGMENT_VIEW,
            AnalyticsParam.FRAGMENT_NAME to fragmentName,
            AnalyticsParam.ENTRY_SOURCE to entrySource
        )
    }

    fun logFeatureUsage(context: Context, featureName: String) {
        if (!isEnabled(context)) return

        val bundle = Bundle().apply {
            putString("feature_name", featureName)
        }
        firebaseAnalytics?.logEvent("feature_usage", bundle)
    }

    private fun isEnabled(context: Context): Boolean = AnalyticsPreference(context).getValue()

    /**
     * GA4 has no boolean parameter type. Booleans go as 1/0 so they aggregate as a metric rather
     * than forcing a string comparison against "true" in every query.
     */
    private fun bundleOf(params: Array<out Pair<String, Any?>>): Bundle = Bundle().apply {
        for ((key, value) in params) {
            when (value) {
                null -> Unit
                is String -> putString(key, value.take(MAX_PARAM_LENGTH))
                is Boolean -> putLong(key, if (value) 1L else 0L)
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Double -> putDouble(key, value)
                is Float -> putDouble(key, value.toDouble())
                else -> putString(key, value.toString().take(MAX_PARAM_LENGTH))
            }
        }
    }
}
