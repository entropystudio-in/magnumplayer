package com.cadence.player.ui.theme

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK_YELLOW }

object ThemeManager {
    private const val PREFS = "cadence_prefs"
    private const val KEY_THEME = "theme_mode"

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
    }

    fun next(current: ThemeMode): ThemeMode = when (current) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.BLACK_YELLOW
        ThemeMode.BLACK_YELLOW -> ThemeMode.SYSTEM
    }
}
