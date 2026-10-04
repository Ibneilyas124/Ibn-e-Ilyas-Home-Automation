package com.ibneilyas.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF4FC3F7)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00212E),
    background = Color(0xFF14171C),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF1E232B),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF272D36),
    onSurfaceVariant = Color(0xFFA7B0BA)
)

@Composable
fun HomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
