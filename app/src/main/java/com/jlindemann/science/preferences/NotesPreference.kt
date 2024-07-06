package com.jlindemann.science.preferences

import android.content.Context

class NotesPreference(context : Context) {

    val PREFERENCE_NAME = "Notes_Preference"
    val PREFERENCE_VALUE = "Notes_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : String {
        return preference.getString (PREFERENCE_VALUE, "value")!!
    }

    fun setValue(string: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, string)
        editor.apply()
    }
}

