package com.jlindemann.science.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.jlindemann.science.preferences.AnalyticsPreference

object AnalyticsHelper {
    private var firebaseAnalytics: FirebaseAnalytics? = null

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
        val analyticsPreference = AnalyticsPreference(context)
        if (!analyticsPreference.getValue()) return

        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    fun logEvent(context: Context, eventName: String, params: Bundle? = null) {
        val analyticsPreference = AnalyticsPreference(context)
        if (!analyticsPreference.getValue()) return

        firebaseAnalytics?.logEvent(eventName, params)
    }

    fun logFeatureUsage(context: Context, featureName: String) {
        val analyticsPreference = AnalyticsPreference(context)
        if (!analyticsPreference.getValue()) return

        val bundle = Bundle().apply {
            putString("feature_name", featureName)
        }
        firebaseAnalytics?.logEvent("feature_usage", bundle)
    }
}
