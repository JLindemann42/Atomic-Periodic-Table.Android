package com.jlindemann.science.preferences

import android.content.Context

class ProVersion(context : Context) {

    val PREFERENCE_NAME = "Pro_Preference"
    val PREFERENCE_VALUE = "Pro_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : Int{
        return preference.getInt (PREFERENCE_VALUE, 1) //0 == No PRO User, 1 == PRO USER
    }

    fun setValue(count:Int) {
        val editor = preference.edit()
        editor.putInt(PREFERENCE_VALUE,count)
        editor.apply()
    }
}