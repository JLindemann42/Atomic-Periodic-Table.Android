package com.jlindemann.science.preferences

import android.content.Context

class MostUsedToolPreference(context : Context) {
    val PREFERENCE_NAME = "calPref4"
    val PREFERENCE_VALUE = "calValue2"
    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    fun getValue() : String {
        return preference.getString (PREFERENCE_VALUE, "cal=0.1, uni=0.2, fla=0.3")!!
    }
    fun setValue(string: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, string)
        editor.apply()
    }
}



