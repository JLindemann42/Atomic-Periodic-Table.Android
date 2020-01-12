package com.jlindemann.science.preferences

import android.content.Context

class reducedInternetPreference(context : Context) {
    val PREFERENCE_NAME = "ReducedInternet_Preference"
    val PREFERENCE_VALUE = "RedicedInternet_Value"

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

class enableZoomPreference(context : Context) {
    val PREFERENCE_NAME = "enableZoom_Preference"
    val PREFERENCE_VALUE = "enableZoom_Value"

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