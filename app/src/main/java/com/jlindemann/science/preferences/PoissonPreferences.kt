package com.jlindemann.science.preferences

import android.content.Context

class PoissonPreferences(context : Context) {

    val PREFERENCE_NAME = "Poisson_Preference"
    val PREFERENCE_VALUE = "Poisson_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : String {
        return preference.getString (PREFERENCE_VALUE, "rock")!!

    }

    fun setValue(string: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, string)
        editor.apply()
    }
}

