package com.karthi.voicefinder.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A1B9A),
    primaryContainer = Color(0xFFF3E5F5),
    secondary = Color(0xFF00897B),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFCE93D8),
    primaryContainer = Color(0xFF4A148C),
    secondary = Color(0xFF80CBC4),
)

/** Material You wallpaper colours on Android 12+ (Pixel and others), brand purple before that. */
@Composable
fun VoiceFinderTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
