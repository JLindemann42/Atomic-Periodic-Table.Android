package com.jlindemann.science.preferences

import android.content.Context

class hideNavPreference(context : Context) {
    val PREFERENCE_NAME = "Hide_Preference"
    val PREFERENCE_VALUE = "Hide_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : Int{
        return preference.getInt (PREFERENCE_VALUE, 0)
    }

    fun setValue(count:Int) {
        val editor = preference.edit()
        editor.putInt(PREFERENCE_VALUE,count)
        editor.apply()
    }
}