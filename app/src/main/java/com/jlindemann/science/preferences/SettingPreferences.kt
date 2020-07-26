package com.jlindemann.science.preferences

import android.content.Context


class offlinePreference(context : Context) {
    val PREFERENCE_NAME = "Offline_Preference"
    val PREFERENCE_VALUE = "Offline_Value"

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


class isoPref(context : Context) {
    val PREFERENCE_NAME = "iso_pref"
    val PREFERENCE_VALUE = "iso_value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : String {
        return preference.getString (PREFERENCE_VALUE, "hydrogen")!!
    }

    fun setValue(count: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE,count)
        editor.apply()
    }
}