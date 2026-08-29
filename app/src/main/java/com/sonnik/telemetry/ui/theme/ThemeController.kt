package com.sonnik.telemetry.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

/**
 * Global theme preference: 0 = follow system, 1 = light, 2 = dark.
 * Backed by shared prefs and exposed as Compose state so changing it
 * recomposes the whole app immediately.
 */
object ThemeController {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    val mode = mutableIntStateOf(SYSTEM)

    fun load(context: Context) {
        mode.intValue = prefs(context).getInt(KEY, SYSTEM)
    }

    fun set(context: Context, value: Int) {
        mode.intValue = value
        prefs(context).edit().putInt(KEY, value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)

    private const val KEY = "theme_mode"
}
