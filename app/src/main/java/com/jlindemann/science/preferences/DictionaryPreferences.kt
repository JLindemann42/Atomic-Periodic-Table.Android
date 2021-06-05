package com.jlindemann.science.preferences

import android.content.Context

class DictionaryPreferences(context : Context) {

    val PREFERENCE_NAME = "Dictionary_Preference"
    val PREFERENCE_VALUE = "Dictionary_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : String {
        return preference.getString (PREFERENCE_VALUE, "chemistry")!!

    }

    fun setValue(string: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, string)
        editor.apply()
    }
}

