package com.scanx.qrscanner

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "app_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_THEME = "pref_theme"
        private const val KEY_BEEP = "pref_beep"
        private const val KEY_BEEP_STYLE = "pref_beep_style"
        
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val BEEP_STANDARD = 0
        const val BEEP_ELECTRONIC = 1
        const val BEEP_NOTIFICATION = 2
        const val BEEP_BLIP = 3

        private const val KEY_SEARCH_ENGINE = "pref_search_engine"
        
        const val SEARCH_ENGINE_GOOGLE = "Google"
        const val SEARCH_ENGINE_BING = "Bing"
        const val SEARCH_ENGINE_DUCKDUCKGO = "DuckDuckGo"
        const val SEARCH_ENGINE_YAHOO = "Yahoo"
    }

    var theme: Int
        get() = prefs.getInt(KEY_THEME, THEME_SYSTEM)
        set(value) {
            prefs.edit().putInt(KEY_THEME, value).apply()
            applyTheme(value)
        }

    var isBeepEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEEP, true)
        set(value) = prefs.edit().putBoolean(KEY_BEEP, value).apply()

    var beepStyle: Int
        get() = prefs.getInt(KEY_BEEP_STYLE, BEEP_STANDARD)
        set(value) = prefs.edit().putInt(KEY_BEEP_STYLE, value).apply()

    var searchEngine: String
        get() = prefs.getString(KEY_SEARCH_ENGINE, SEARCH_ENGINE_GOOGLE) ?: SEARCH_ENGINE_GOOGLE
        set(value) = prefs.edit().putString(KEY_SEARCH_ENGINE, value).apply()

    fun applyTheme(themeValue: Int = theme) {
        val mode = when (themeValue) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
