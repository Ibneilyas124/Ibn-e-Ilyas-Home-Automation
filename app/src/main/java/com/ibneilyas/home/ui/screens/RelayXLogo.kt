package com.ibneilyas.home.ui.screens

import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibneilyas.home.R

@Composable
fun RelayXLogo() {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val c1 = if (dark) Color(0xFF7CFFC0) else Color(0xFF0E9F5A)
    val c2 = if (dark) Color(0xFF1FD27A) else Color(0xFF087A44)
    val grad = Brush.horizontalGradient(listOf(c1, c2))
    val sporty = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC))
    Column {
        Image(
            painter = painterResource(if (dark) R.drawable.relayx_logo_dark else R.drawable.relayx_logo_light),
            contentDescription = "RelayX",
            modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1053f / 386f)
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
            Box(
                Modifier.width(30.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(grad)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "HOME AUTOMATION",
                style = TextStyle(
                    brush = grad,
                    fontFamily = sporty,
                    fontSize = 15.sp,
                    letterSpacing = 4.sp
                )
            )
        }
    }
}
