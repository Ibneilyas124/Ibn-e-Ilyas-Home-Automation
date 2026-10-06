package com.ibneilyas.home.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.MicWidget
import java.text.DateFormat
import java.util.Date

@Composable
fun WidgetSection() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val mgr = remember { AppWidgetManager.getInstance(ctx) }
    val registered = remember { mgr.installedProviders.any { it.provider.packageName == ctx.packageName } }
    val updated = remember {
        try {
            DateFormat.getDateTimeInstance().format(Date(ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime))
        } catch (e: Exception) {
            "unknown"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice widget", style = MaterialTheme.typography.titleMedium)
        Text(
            (if (registered) "Widget registered with Android: yes" else "Widget registered with Android: NO") +
                "\nApp installed: $updated",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = {
                msg = if (!mgr.isRequestPinAppWidgetSupported()) {
                    "This launcher cannot add widgets from the app. Long-press the app icon and choose Voice command."
                } else if (mgr.requestPinAppWidget(ComponentName(ctx, MicWidget::class.java), null, null)) {
                    "Confirm the popup to add the widget"
                } else {
                    "Android refused. Restart the phone and try again."
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Add voice widget to Home screen") }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
    }
}
