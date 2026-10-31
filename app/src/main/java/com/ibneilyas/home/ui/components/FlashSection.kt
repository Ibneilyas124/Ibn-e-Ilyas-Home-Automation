package com.ibneilyas.home.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.data.EspFlasher
import kotlinx.coroutines.launch

@Composable
fun FlashSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ticket by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1) }
    val fw = remember {
        try { ctx.assets.open("firmware-version.txt").bufferedReader().use { it.readText().trim() } } catch (e: Exception) { "?" }
    }
    fun add(s: String) { logText = logText + s + "\n" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Flash new ESP32 (USB)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Firmware $fw is app mein bundled hai. ESP32 ko OTG cable se phone se jorein. Flash ke dauran cable na nikalein aur screen na chhorein.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = {
                scope.launch {
                    busy = true; logText = ""; progress = -1
                    try { EspFlasher.detect(ctx) { add(it) } } catch (e: Exception) { add("Masla: " + (e.message ?: e.toString())) }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Detect ESP32 (USB)") }
        OutlinedTextField(
            value = ticket, onValueChange = { ticket = it },
            label = { Text("Ticket (IBN-TICKET:...)") }, maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true; logText = ""; progress = 0
                    try { EspFlasher.flash(ctx, ticket, { add(it) }, { progress = it }) }
                    catch (e: Exception) { add("Masla: " + (e.message ?: e.toString())); progress = -1 }
                    busy = false
                }
            },
            enabled = !busy && (ticket.isNotBlank() || com.ibneilyas.home.core.BrandConfig.DEV_SKIP_TICKET),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Flash firmware") }
        if (progress in 0..100) LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
        if (progress in 0..100) Text("Flashing: " + progress + "%")
        if (logText.isNotEmpty()) Text(logText, style = MaterialTheme.typography.bodySmall)
    }
}
