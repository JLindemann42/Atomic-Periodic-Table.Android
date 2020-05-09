package com.jlindemann.science.preferences

import android.content.Context

class ElementSendAndLoad(context: Context) {

    val PREFERENCE_NAME = "ElementSendAndLoad"
    val PREFERENCE_VALUE = "ElementSendAndLoadValue"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : String?{
        return preference.getString(PREFERENCE_VALUE, "hydrogen")
    }

    fun setValue(string: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, string)
        editor.apply()
    }
}