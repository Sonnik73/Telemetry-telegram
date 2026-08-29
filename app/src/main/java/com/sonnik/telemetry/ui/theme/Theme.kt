package com.sonnik.telemetry.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TelegramBlue = Color(0xFF1C93E3)
private val TelegramBlueDark = Color(0xFF64B5EF)

private val LightColors = lightColorScheme(primary = TelegramBlue)
private val DarkColors = darkColorScheme(primary = TelegramBlueDark)

@Composable
fun TelemetryTheme(content: @Composable () -> Unit) {
    val darkTheme = when (ThemeController.mode.intValue) {
        ThemeController.LIGHT -> false
        ThemeController.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
