package com.jlindemann.science.preferences

import android.content.Context

class ElementSendAndLoad(context: Context) {

    val PREFERENCE_NAME = "ElementSendAndLoad"
    val PREFERENCE_VALUE = "ElementSendAndLoadValue"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : Int?{
        return preference.getInt(PREFERENCE_VALUE, 1)
    }

    fun setValue(count: Int) {
        val editor = preference.edit()
        editor.putInt(PREFERENCE_VALUE, count)
        editor.apply()
    }
}