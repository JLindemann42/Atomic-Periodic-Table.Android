package com.jlindemann.science.preferences

import android.content.Context

class SearchPreferences(context : Context) {

    val PREFERENCE_NAME = "Search_Preference"
    val PREFERENCE_VALUE = "Search_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : Int{
        return preference.getInt (PREFERENCE_VALUE, 0)
        //0 == Element Number
        //1 == Electronegativity
        //2 == STP Phase
    }

    fun setValue(count:Int) {
        val editor = preference.edit()
        editor.putInt(PREFERENCE_VALUE,count)
        editor.apply()
    }
}