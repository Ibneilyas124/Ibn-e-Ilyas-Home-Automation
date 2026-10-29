package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.data.AppUpdate
import kotlinx.coroutines.launch

/** One-tap app update: checks the public dev release, downloads the APK and opens the installer. */
@Composable
fun AppUpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Installed build: " + AppUpdate.currentBuild(ctx))
            if (msg.isNotEmpty()) Text(msg)
            if (apk != null) Button(onClick = { apk?.let { AppUpdate.install(ctx, it) } }) { Text("Install") }
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        msg = "Checking..."
                        apk = null
                        try { com.ibneilyas.home.data.Alert.ask(ctx) } catch (e: Exception) { }
                        val latest = AppUpdate.latestBuild()
                        val cur = AppUpdate.currentBuild(ctx)
                        if (latest == null) {
                            msg = "Could not check. Is the internet on?"
                        } else if (latest <= cur) {
                            msg = "App is up to date (latest build " + latest + ")."
                        } else if (!AppUpdate.canInstall(ctx)) {
                            msg = "Allow installing from this app, then come back and tap again."
                            AppUpdate.openInstallPermission(ctx)
                        } else {
                            msg = "Downloading build " + latest + "..."
                            val f = AppUpdate.download(ctx)
                            if (f == null) {
                                msg = "Download failed. Try again."
                                try { com.ibneilyas.home.data.Alert.notify(ctx, "App update", "Download failed. Try again.") } catch (e: Exception) { }
                            } else {
                                apk = f
                                msg = "Build " + latest + " downloaded. Tap Install."
                                try { com.ibneilyas.home.data.Alert.notify(ctx, "App update", "Build " + latest + " downloaded. Open the app and tap Install.") } catch (e: Exception) { }
                            }
                        }
                        busy = false
                    }
                }
            ) { Text("Check and update") }
        }
    }
}
