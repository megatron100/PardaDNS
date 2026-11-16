package com.example.pardadns.data.repository

import android.content.Context
import com.example.pardadns.ui.theme.ThemeMode

class ThemeRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode =
        ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "parda_theme_prefs"
        private const val KEY_THEME  = "theme_mode"
    }
}
