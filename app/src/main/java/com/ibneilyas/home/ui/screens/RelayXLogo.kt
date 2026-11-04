package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibneilyas.home.R

@Composable
fun RelayXLogo() {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    Column {
        Image(
            painter = painterResource(if (dark) R.drawable.relayx_logo_dark else R.drawable.relayx_logo_light),
            contentDescription = "RelayX",
            modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1053f / 386f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Home Automation",
            color = cs.primary,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
