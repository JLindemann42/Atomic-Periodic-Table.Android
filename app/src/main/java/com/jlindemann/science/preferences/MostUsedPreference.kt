package com.jlindemann.science.preferences

import android.content.Context

class MostUsedPreference(context : Context) {
    val PREFERENCE_NAME = "phPref"
    val PREFERENCE_VALUE = "phValue"
    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    fun getValue() : String {
        return preference.getString (PREFERENCE_VALUE, "phi=0.1, ele=0.2, eqe=0.3, ion=0.4, sol=0.5 poi=0.6, nuc=0.7, con=0.8, geo=0.9, iso=0.05, emi=0.95")!!
    }
    fun setValue(string: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, string)
        editor.apply()
    }
}



