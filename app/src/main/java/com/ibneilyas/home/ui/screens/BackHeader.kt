package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Back-arrow page header: purple rounded header in light mode, unchanged in dark mode. */
@Composable
fun isLightHeader(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
fun Modifier.backHeader(): Modifier =
    if (isLightHeader()) this.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF7C4DDB), Color(0xFF4F2AA8)))).padding(horizontal = 8.dp, vertical = 12.dp) else this

@Composable
fun backHeaderTint(): Color = if (isLightHeader()) Color.White else LocalContentColor.current

@Composable
fun backHeaderText(): Color = if (isLightHeader()) Color.White else Color.Unspecified

@Composable
fun backHeaderSub(): Color = if (isLightHeader()) Color(0xFFDDD0F7) else MaterialTheme.colorScheme.onSurfaceVariant
