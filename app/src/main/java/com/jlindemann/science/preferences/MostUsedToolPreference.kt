package com.jlindemann.science.preferences

import android.content.Context

class MostUsedToolPreference(context : Context) {
    val PREFERENCE_NAME = "calPref4"
    val PREFERENCE_VALUE = "calValue2"
    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    fun getValue() : String = withNewTools(preference.getString(PREFERENCE_VALUE, DEFAULT)!!)

    fun setValue(string: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, string)
        editor.apply()
    }

    companion object {
        /** Every tool the row can show, with the seed that decides its initial order. */
        private val DEFAULT_ENTRIES = listOf(
            "cal" to "0.1", "uni" to "0.2", "fla" to "0.3",
            "gas" to "0.4", "dic" to "0.5", "bal" to "0.6"
        )

        val DEFAULT: String = DEFAULT_ENTRIES.joinToString(", ") { (id, seed) -> "$id=$seed" }

        /**
         * A stored counter string with any newly shipped tool appended.
         *
         * The default only ever reaches a fresh install. Every existing user already has a string
         * written, and the counter is rewritten in place rather than extended — so a tool added
         * after that string was first saved is never counted, and therefore never appears in the
         * most-used row for anyone who had the app before it shipped.
         *
         * Pure, so the migration can be tested without an Android Context.
         */
        fun withNewTools(stored: String): String {
            val missing = DEFAULT_ENTRIES.filterNot { (id, _) -> stored.contains("$id=") }
            if (missing.isEmpty()) return stored
            return stored + missing.joinToString("") { (id, seed) -> ", $id=$seed" }
        }
    }
}



