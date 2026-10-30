package com.jlindemann.science.preferences

import android.content.Context

/**
 * When the last successful sync finished, as epoch milliseconds, or 0 for never.
 *
 * The Windows app keeps the equivalent in memory. It is persisted here because an Android activity
 * is recreated far more readily than a desktop window, and "Last synced: Never" on every rotation
 * would read as a failure rather than as a forgotten value.
 */
class LastSyncPreference(context: Context) {

    val PREFERENCE_NAME = "Last_Sync_Preference"
    val PREFERENCE_VALUE = "Last_Sync_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue(): Long {
        return preference.getLong(PREFERENCE_VALUE, 0L)
    }

    fun setValue(epochMillis: Long) {
        val editor = preference.edit()
        editor.putLong(PREFERENCE_VALUE, epochMillis)
        editor.apply()
    }
}
