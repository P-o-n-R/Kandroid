package com.kandroid.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B87), onPrimary = Color.White,
    secondary = Color(0xFF527681), tertiary = Color(0xFF6B5778),
    surface = Color(0xFFF9FAFB), surfaceVariant = Color(0xFFE2E9EC)
)
private val DarkColors = darkColorScheme(primary = Color(0xFF76D1F2), secondary = Color(0xFFB3CBD3))

@Composable fun KandroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

