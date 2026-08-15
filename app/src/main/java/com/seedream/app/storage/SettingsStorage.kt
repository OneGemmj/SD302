package com.seedream.app.storage

import android.content.Context
import android.content.SharedPreferences

class SettingsStorage(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveSetting(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getSetting(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    /** Returns all saved settings as a key-value map (used by backup/restore). */
    fun all(): Map<String, String> {
        return prefs.all.mapNotNull { (key, value) ->
            (value as? String)?.let { key to it }
        }.toMap()
    }

    companion object {
        private const val PREFS = "seedream_settings"
    }
}
