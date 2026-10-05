package com.ibneilyas.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val accentChoices = listOf(
    "blue" to Color(0xFF4FC3F7),
    "green" to Color(0xFF66BB6A),
    "orange" to Color(0xFFFFA726),
    "purple" to Color(0xFFB39DDB)
)

fun accentFor(key: String): Color =
    accentChoices.firstOrNull { it.first == key }?.second ?: accentChoices[0].second

@Composable
fun HomeTheme(dark: Boolean = true, accentKey: String = "blue", content: @Composable () -> Unit) {
    val a = accentFor(accentKey)
    val scheme = if (dark) darkColorScheme(
        primary = a,
        onPrimary = Color(0xFF00212E),
        background = Color(0xFF14171C),
        onBackground = Color(0xFFECEFF1),
        surface = Color(0xFF1E232B),
        onSurface = Color(0xFFECEFF1),
        surfaceVariant = Color(0xFF272D36),
        onSurfaceVariant = Color(0xFFA7B0BA)
    ) else lightColorScheme(
        primary = lerp(a, Color.Black, 0.4f),
        onPrimary = Color.White,
        background = Color(0xFFF4F6F8),
        onBackground = Color(0xFF14171C),
        surface = Color.White,
        onSurface = Color(0xFF14171C),
        surfaceVariant = Color(0xFFE3E8ED),
        onSurfaceVariant = Color(0xFF4A5560)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
