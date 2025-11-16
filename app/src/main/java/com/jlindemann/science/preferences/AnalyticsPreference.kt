package com.jlindemann.science.preferences

import android.content.Context

class AnalyticsPreference(context: Context) {
    private val PREFERENCE_NAME = "Analytics_Preference"
    private val PREFERENCE_VALUE = "Analytics_Enabled"

    private val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue(): Boolean {
        return preference.getBoolean(PREFERENCE_VALUE, true) // Default to enabled
    }

    fun setValue(enabled: Boolean) {
        val editor = preference.edit()
        editor.putBoolean(PREFERENCE_VALUE, enabled)
        editor.apply()
    }
}
