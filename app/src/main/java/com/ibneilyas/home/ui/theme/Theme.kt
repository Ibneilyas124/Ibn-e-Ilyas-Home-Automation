package com.ibneilyas.home.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

val accentChoices = listOf(
    "blue" to Color(0xFF4FC3F7),
    "green" to Color(0xFF66BB6A),
    "orange" to Color(0xFFFFA726),
    "purple" to Color(0xFFB39DDB)
)

fun accentFor(key: String): Color =
    accentChoices.firstOrNull { it.first == key }?.second ?: accentChoices[0].second

private val HomeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun HomeTheme(dark: Boolean = true, accentKey: String = "blue", content: @Composable () -> Unit) {
    val a = accentFor(accentKey)
    val scheme = if (dark) darkColorScheme(
        primary = a,
        onPrimary = Color(0xFF00212E),
        primaryContainer = lerp(a, Color(0xFF14171C), 0.72f),
        onPrimaryContainer = lerp(a, Color.White, 0.6f),
        secondary = Color(0xFFFFB38A),
        onSecondary = Color(0xFF2B1400),
        secondaryContainer = Color(0xFF3A2A22),
        onSecondaryContainer = Color(0xFFFFDCC8),
        tertiary = Color(0xFFB9A8F5),
        onTertiary = Color(0xFF1D1240),
        tertiaryContainer = Color(0xFF2E2848),
        onTertiaryContainer = Color(0xFFE6DEFF),
        background = Color(0xFF14171C),
        onBackground = Color(0xFFECEFF1),
        surface = Color(0xFF1E232B),
        onSurface = Color(0xFFECEFF1),
        surfaceVariant = Color(0xFF272D36),
        onSurfaceVariant = Color(0xFFA7B0BA),
        outline = Color(0xFF3A424D),
        outlineVariant = Color(0xFF2C333C)
    ) else lightColorScheme(
        primary = lerp(a, Color.Black, 0.4f),
        onPrimary = Color.White,
        primaryContainer = lerp(a, Color.White, 0.75f),
        onPrimaryContainer = lerp(a, Color.Black, 0.7f),
        secondary = Color(0xFFB4541F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE0CF),
        onSecondaryContainer = Color(0xFF3B1500),
        tertiary = Color(0xFF5E4BB6),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE8E0FF),
        onTertiaryContainer = Color(0xFF1D1240),
        background = Color(0xFFF4F6F8),
        onBackground = Color(0xFF14171C),
        surface = Color.White,
        onSurface = Color(0xFF14171C),
        surfaceVariant = Color(0xFFE3E8ED),
        onSurfaceVariant = Color(0xFF4A5560),
        outline = Color(0xFF8A949E),
        outlineVariant = Color(0xFFD0D6DC)
    )
    MaterialTheme(colorScheme = scheme, shapes = HomeShapes, content = content)
}
