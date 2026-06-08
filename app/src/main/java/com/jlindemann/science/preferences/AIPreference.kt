package com.jlindemann.science.preferences

import android.content.Context

class AIPreference(context: Context) {

    val PREFERENCE_NAME = "AI_Preference"
    val PREFERENCE_VALUE = "AI_Enabled"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue(): Boolean {
        return preference.getBoolean(PREFERENCE_VALUE, true)
    }

    fun setValue(enabled: Boolean) {
        val editor = preference.edit()
        editor.putBoolean(PREFERENCE_VALUE, enabled)
        editor.apply()
    }
}
