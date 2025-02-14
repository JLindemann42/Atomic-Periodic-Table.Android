package com.jlindemann.science.preferences

import android.content.Context

class ConstantsPreference(context : Context) {

    val PREFERENCE_NAME = "Constants_Preference"
    val PREFERENCE_VALUE = "Constants_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : String {
        return preference.getString (PREFERENCE_VALUE, "mathematics")!!
    }

    fun setValue(string: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, string)
        editor.apply()
    }
}

