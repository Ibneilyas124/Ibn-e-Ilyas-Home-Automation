package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RelayXLogo() {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    Column {
        Box(
            Modifier
                .drawBehind {
                    val xw = 30.dp.toPx()
                    val tail = 10.dp.toPx()
                    val th = size.height - tail
                    val xl = size.width - xw
                    val top = th * 0.24f
                    val bot = th * 0.82f
                    val lineY = th + 2.dp.toPx()
                    val sw = 3.5.dp.toPx()
                    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val leg = Path().apply {
                        moveTo(xl + xw, top)
                        lineTo(xl + xw * 0.18f, bot)
                        quadraticBezierTo(xl + xw * 0.02f, lineY, xl - xw * 0.45f, lineY)
                        lineTo(sw, lineY)
                    }
                    drawPath(leg, accent, style = st)
                    val other = Path().apply {
                        moveTo(xl, top)
                        lineTo(xl + xw, bot)
                    }
                    drawPath(other, accent, style = st)
                }
                .padding(bottom = 10.dp)
        ) {
            Row {
                Text(
                    "Relay",
                    color = cs.onBackground,
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic
                )
                Spacer(Modifier.width(30.dp))
            }
        }
        Text(
            "Home Automation",
            color = accent,
            fontSize = 13.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
