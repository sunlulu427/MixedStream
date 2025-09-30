package com.devyk.av.rtmppush.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00BFA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5DF2D6),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF455A64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFD8DC),
    onSecondaryContainer = Color(0xFF121C1F),
    background = Color(0xFFF7F9FA),
    onBackground = Color(0xFF111415),
    surface = Color.White,
    onSurface = Color(0xFF111415)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD9C1),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFFB1FCE7),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1C252A),
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFDDE4E8),
    background = Color(0xFF0B1212),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF111B1B),
    onSurface = Color(0xFFE0E3E3)
)

@Composable
fun AVLiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
