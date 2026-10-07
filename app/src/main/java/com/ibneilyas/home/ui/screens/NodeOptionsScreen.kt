package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.data.FirmwareHub
import com.ibneilyas.home.data.HttpNodeClient
import com.ibneilyas.home.ui.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun fmtLeft(s: Int): String =
    if (s >= 3600) "${s / 3600} h ${(s % 3600) / 60} min" else if (s >= 60) "${s / 60} min" else "$s sec"

private fun versionOf(j: String): String =
    Regex("\"firmware\":\"([^\"]*)\"").find(j)?.groupValues?.get(1).orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeOptionsScreen(nodeId: String, vm: HomeViewModel, onBack: () -> Unit) {
    val nodes by vm.nodes.collectAsState()
    val d by vm.data.collectAsState()
    val cfg = nodes.firstOrNull { it.id == nodeId }
    if (cfg == null) {
        Text("Node not found", Modifier.padding(24.dp))
        return
    }
    val client = remember(cfg.id, cfg.ip, cfg.token) { HttpNodeClient("http://${cfg.ip}", cfg.token) }
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf("") }
    var latest by remember { mutableStateOf<String?>(null) }
    var boot by remember { mutableStateOf("last") }
    var sw by remember { mutableStateOf(0) }
    var supported by remember { mutableStateOf(true) }
    var timers by remember { mutableStateOf<List<Triple<Int, Boolean, Int>>>(emptyList()) }
    var status by remember { mutableStateOf("Loading...") }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        val i = client.info()
        if (i == null) { status = "Cannot reach this ESP32"; return }
        installed = versionOf(i)
        val c = client.config()
        supported = c != null
        if (c != null) {
            boot = Regex("\"boot\":\"(\\w+)\"").find(c)?.groupValues?.get(1) ?: "last"
            sw = Regex("\"sw\":(\\d)").find(c)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        timers = (client.timers() ?: "").split(";").mapNotNull { p ->
            val x = p.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (x.size == 3) Triple(x[0], x[1] == 1, x[2]) else null
        }
        status = ""
    }
    LaunchedEffect(cfg.id) { load() }

    fun saveBoot(v: String) = scope.launch {
        if (client.setConfig(boot = v) != null) { boot = v } else { status = "Could not save. Is the ESP32 online?" }
    }
    fun saveSwitch(v: Int) = scope.launch {
        if (client.setConfig(sw = v) != null) { sw = v } else { status = "Could not save. Is the ESP32 online?" }
    }
    fun cancelTimer(ch: Int) = scope.launch {
        client.setTimer(ch, 0, false)
        load()
    }
    fun check() = scope.launch {
        status = "Checking for update..."
        val v = FirmwareHub.latestVersion()
        latest = v
        status = when {
            v == null -> "Could not check. The phone needs internet for this step."
            FirmwareHub.newer(v, installed) -> "Update available: $v"
            else -> "Firmware is up to date"
        }
    }
    fun update() = scope.launch {
        val want = latest ?: return@launch
        busy = true
        status = "Downloading firmware..."
        val bin = FirmwareHub.download()
        if (bin == null) { status = "Download failed"; busy = false; return@launch }
        status = "Uploading to the ESP32. Do not switch off the power..."
        if (!client.uploadFirmware(bin)) { status = "Upload failed. Nothing was changed."; busy = false; return@launch }
        status = "Restarting the ESP32..."
        var done = false
        for (k in 1..30) {
            delay(2000)
            val i = client.info() ?: continue
            installed = versionOf(i)
            if (installed == want) { done = true; break }
        }
        status = if (done) "Updated to $installed" else "Update sent. Reopen this screen after the ESP32 restarts."
        busy = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column {
                Text("ESP32 options", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(cfg.room + "  \u2022  " + cfg.ip, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!supported) {
                Text("This ESP32 has older firmware without these options. Flash firmware 0.5.0 or newer once with USB, then they appear here.")
            }
            Text("Firmware", style = MaterialTheme.typography.titleMedium)
            Text("Installed: " + installed.ifEmpty { "unknown" } + (latest?.let { "     Latest: $it" } ?: ""))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { check() }, enabled = !busy) { Text("Check for update") }
                val l = latest
                if (l != null && installed.isNotEmpty() && FirmwareHub.newer(l, installed)) {
                    Button(onClick = { update() }, enabled = !busy) { Text("Update now") }
                }
            }
            Text("After a power cut", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = boot == "last", onClick = { saveBoot("last") }, label = { Text("Restore last state") }, enabled = supported)
                FilterChip(selected = boot == "off", onClick = { saveBoot("off") }, label = { Text("Always OFF") }, enabled = supported)
            }
            Text(
                "Restore last state can switch a heater or iron back on by itself. Choose Always OFF for those.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Wall switches", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sw == 0, onClick = { saveSwitch(0) }, label = { Text("Off") }, enabled = supported)
                FilterChip(selected = sw == 1, onClick = { saveSwitch(1) }, label = { Text("Push button") }, enabled = supported)
                FilterChip(selected = sw == 2, onClick = { saveSwitch(2) }, label = { Text("Flip switch") }, enabled = supported)
            }
            Text(
                "Inputs: GPIO 32, 33, 25, 26 = channels 1 to 4, wired as a low-voltage contact to GND. " +
                    "Never connect mains wiring to the ESP32. Use an isolated sensing module fitted by an electrician.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Active timers", style = MaterialTheme.typography.titleMedium)
            if (timers.isEmpty()) Text("No timer running.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            timers.forEach { (ch, on, left) ->
                val name = d.appliances.firstOrNull { it.nodeId == cfg.id && it.channel == ch }?.name ?: "Channel $ch"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$name \u2192 ${if (on) "ON" else "OFF"} in ${fmtLeft(left)}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { cancelTimer(ch) }) { Text("Cancel") }
                }
            }
            OutlinedButton(onClick = { scope.launch { load() } }) { Text("Refresh") }
        }
    }
}
