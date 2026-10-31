package com.ibneilyas.home.ui.screens

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
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
    var maxon by remember { mutableStateOf(listOf<String>()) }
    var vacOn by remember { mutableStateOf(false) }
    var vacMask by remember { mutableStateOf(0) }
    var vacS by remember { mutableStateOf("18:00") }
    var vacE by remember { mutableStateOf("23:00") }
    var sunLat by remember { mutableStateOf("") }
    var sunLon by remember { mutableStateOf("") }
    var sunInfo by remember { mutableStateOf("") }
    var sunList by remember { mutableStateOf(listOf<List<Int>>()) }
    var sEv by remember { mutableStateOf(0) }
    var sOff by remember { mutableStateOf("0") }
    var sCh by remember { mutableStateOf("1") }
    var sOn by remember { mutableStateOf(true) }
    var sDays by remember { mutableStateOf(127) }
    var supported by remember { mutableStateOf(true) }
    var timers by remember { mutableStateOf<List<Triple<Int, Boolean, Int>>>(emptyList()) }
    var status by remember { mutableStateOf("Loading...") }
    var otaPct by remember { mutableStateOf(-2) }
    var busy by remember { mutableStateOf(false) }
    var certTicket by remember { mutableStateOf("") }
    var certMsg by remember { mutableStateOf("") }
    var chCount by remember { mutableStateOf(4) }
    var pendingCh by remember { mutableStateOf(4) }

    suspend fun load() {
        val i = client.info()
        if (i == null) { status = "Cannot reach this ESP32"; return }
        installed = versionOf(i)
        chCount = Regex("\"channels\":(\\d+)").find(i)?.groupValues?.get(1)?.toIntOrNull() ?: 4
        pendingCh = chCount
        val c = client.config()
        supported = c != null
        if (c != null) {
            boot = Regex("\"boot\":\"(\\w+)\"").find(c)?.groupValues?.get(1) ?: "last"
            sw = Regex("\"sw\":(\\d)").find(c)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            maxon = (Regex("\"maxon\":\\[([0-9,]*)\\]").find(c)?.groupValues?.get(1) ?: "").split(",").filter { it.isNotBlank() }
        }
        timers = (client.timers() ?: "").split(";").mapNotNull { p ->
            val x = p.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (x.size == 3) Triple(x[0], x[1] == 1, x[2]) else null
        }
        status = ""
    }
    fun hm(t: String): Int {
        val p = t.split(":")
        if (p.size != 2) return -1
        val h = p[0].trim().toIntOrNull() ?: return -1
        val m = p[1].trim().toIntOrNull() ?: return -1
        if (h !in 0..23 || m !in 0..59) return -1
        return h * 60 + m
    }
    fun hmText(m: Int): String = if (m < 0) "not set" else "%02d:%02d".format(m / 60, m % 60)
    suspend fun loadVacSun() {
        val v = client.vac()
        if (v != null) {
            vacOn = Regex("\"on\":(\\d)").find(v)?.groupValues?.get(1) == "1"
            vacMask = Regex("\"mask\":(\\d+)").find(v)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val a = Regex("\"start\":(\\d+)").find(v)?.groupValues?.get(1)?.toIntOrNull() ?: 1080
            val e = Regex("\"end\":(\\d+)").find(v)?.groupValues?.get(1)?.toIntOrNull() ?: 1380
            vacS = hmText(a); vacE = hmText(e)
        }
        val u = client.sun()
        if (u != null) {
            sunLat = Regex("\"lat\":(-?[0-9.]+)").find(u)?.groupValues?.get(1) ?: ""
            sunLon = Regex("\"lon\":(-?[0-9.]+)").find(u)?.groupValues?.get(1) ?: ""
            val r = Regex("\"rise\":(-?\\d+)").find(u)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val st = Regex("\"set\":(-?\\d+)").find(u)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            sunInfo = "Today (estimate): sunrise " + hmText(r) + ", sunset " + hmText(st)
        }
    }
    LaunchedEffect(cfg.id) { load() }
    suspend fun loadSunList() {
        val ss = client.sunSched()
        if (ss != null) {
            sunList = ss.split(";").mapNotNull { p ->
                val x = p.split(",").mapNotNull { it.trim().toIntOrNull() }
                if (x.size == 5) x else null
            }
        }
    }
    LaunchedEffect(cfg.id) { loadVacSun(); loadSunList() }
    fun saveSunList(l: List<List<Int>>) = scope.launch {
        val d = l.joinToString("") { it.joinToString(",") + ";" }
        if (client.setSunSched(d) != null) { sunList = l; status = "Sun schedule saved" } else { status = "Could not save. Is the ESP32 online?" }
    }
    fun addSun() {
        val o = sOff.toIntOrNull(); val c = sCh.toIntOrNull()
        if (o == null || o < -720 || o > 720 || c == null || c < 1 || c > chCount) { status = "Offset -720 to 720, channel 1 to $chCount"; return }
        if (sDays == 0) { status = "Pick at least one day"; return }
        if (sunList.size >= 8) { status = "Maximum 8 entries"; return }
        saveSunList(sunList + listOf(listOf(sEv, o, sDays, c, if (sOn) 1 else 0)))
    }
    fun saveVac() = scope.launch {
        val a = hm(vacS); val e = hm(vacE)
        if (a < 0 || e < 0 || a + 90 > e) { status = "Use HH:MM. Start must be at least 90 minutes before end."; return@launch }
        status = if (client.setVac(vacOn, vacMask, a, e) != null) "Vacation mode saved" else "Could not save. Is the ESP32 online?"
    }
    fun saveSun() = scope.launch {
        val la = sunLat.toDoubleOrNull(); val lo = sunLon.toDoubleOrNull()
        if (la == null || lo == null || la <= -66.0 || la >= 66.0 || lo <= -180.0 || lo >= 180.0) { status = "Latitude must be between -66 and 66, longitude between -180 and 180."; return@launch }
        status = if (client.setSun(sunLat.trim(), sunLon.trim()) != null) "Location saved" else "Could not save. Is the ESP32 online?"
        loadVacSun()
    }

    fun saveBoot(v: String) = scope.launch {
        if (client.setConfig(boot = v) != null) { boot = v } else { status = "Could not save. Is the ESP32 online?" }
    }
    fun saveSwitch(v: Int) = scope.launch {
        if (client.setConfig(sw = v) != null) { sw = v } else { status = "Could not save. Is the ESP32 online?" }
    }
    fun saveMaxon() = scope.launch {
        val v = maxon.joinToString(",") { (it.toIntOrNull() ?: 0).coerceIn(0, 1440).toString() }
        status = if (client.setConfig(maxon = v) != null) "Auto-off saved" else "Could not save. Is the ESP32 online?"
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
    val buzzCtx = androidx.compose.ui.platform.LocalContext.current
    fun buzz() {
        try { com.ibneilyas.home.data.Alert.notify(buzzCtx, "Firmware update", status.toString()) } catch (e: Exception) { }
        try {
            val vb = androidx.core.content.ContextCompat.getSystemService(buzzCtx, android.os.Vibrator::class.java)
            val at = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            vb?.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE), at)
        } catch (e: Exception) { }
    }
    fun update() = scope.launch {
        val want = latest ?: return@launch
        try { com.ibneilyas.home.data.Alert.ask(buzzCtx) } catch (e: Exception) { }
        busy = true
        try { (buzzCtx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ibn:ota").acquire(180000L) } catch (e: Exception) { }
        status = "Downloading firmware..."
        otaPct = -1
        val bin = FirmwareHub.download { p -> otaPct = p; status = if (p >= 0) "Downloading firmware: " + p + "%" else "Downloading firmware..." }
        if (bin == null) { otaPct = -2; status = "Download failed"; busy = false; buzz(); return@launch }
        otaPct = 0
        status = "Uploading to the ESP32. Do not switch off the power..."
        if (!client.uploadFirmware(bin, { p -> otaPct = p; status = "Uploading to the ESP32: " + p + "%. Do not switch off the power." })) { otaPct = -2; status = "Upload failed (" + client.lastError + "). Nothing was changed."; busy = false; buzz(); return@launch }
        otaPct = -2
        status = "Restarting the ESP32..."
        var done = false
        for (k in 1..30) {
            delay(2000)
            val i = client.info() ?: continue
            installed = versionOf(i)
            if (installed == want) { done = true; break }
        }
        status = if (done) "Updated to $installed" else "Update sent. Reopen this screen after the ESP32 restarts."
        buzz()
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
        if (otaPct == -1) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        if (otaPct >= 0) LinearProgressIndicator(progress = { otaPct / 100f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
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
            Text("Channels", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (pendingCh > 1) pendingCh-- }, enabled = !busy) { Text("\u2212") }
                Text("$pendingCh", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { if (pendingCh < 8) pendingCh++ }, enabled = !busy) { Text("+") }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "Applying..."
                            val r = client.setChannels(pendingCh)
                            if (r == null) {
                                status = "Failed. Update the ESP32 firmware to 0.8.0 first."
                            } else {
                                status = "Restarting ESP32..."
                                delay(9000)
                                load()
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && pendingCh != chCount
                ) { Text("Apply") }
            }
            Text(
                "How many relays are fitted (1 to 8). The ESP32 restarts after Apply.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                "Relay outputs: GPIO 16, 17, 18, 19 = channels 1 to 4; GPIO 21, 22, 23, 27 = channels 5 to 8. " +
                "Inputs: GPIO 32, 33, 25, 26 = channels 1 to 4; GPIO 4, 5, 13, 14 = channels 5 to 8, wired as a low-voltage contact to GND. " +
                    "Never connect mains wiring to the ESP32. Use an isolated sensing module fitted by an electrician.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Auto-off timer", style = MaterialTheme.typography.titleMedium)
            maxon.forEachIndexed { i, v ->
                OutlinedTextField(
                    value = v,
                    onValueChange = { t -> maxon = maxon.toMutableList().also { it[i] = t.filter { c -> c.isDigit() }.take(4) } },
                    label = { Text("Channel ${i + 1}: max minutes ON (0 = off)") },
                    singleLine = true, enabled = supported, modifier = Modifier.fillMaxWidth()
                )
            }
            Button(onClick = { saveMaxon() }, enabled = supported && maxon.isNotEmpty()) { Text("Save auto-off") }
            Text(
                "A channel switches OFF by itself after this many minutes (0 to 1440). Counting restarts after a power cut.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Vacation mode", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = vacOn, onCheckedChange = { vacOn = it }, enabled = supported)
                Text(if (vacOn) "On" else "Off")
            }
            for (i in 0 until chCount) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = ((vacMask shr i) and 1) == 1,
                        onCheckedChange = { c -> vacMask = if (c) vacMask or (1 shl i) else vacMask and (1 shl i).inv() },
                        enabled = supported
                    )
                    Text("Channel ${i + 1}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = vacS, onValueChange = { vacS = it }, label = { Text("From (HH:MM)") }, singleLine = true, enabled = supported, modifier = Modifier.weight(1f))
                OutlinedTextField(value = vacE, onValueChange = { vacE = it }, label = { Text("Until (HH:MM)") }, singleLine = true, enabled = supported, modifier = Modifier.weight(1f))
            }
            Button(onClick = { saveVac() }, enabled = supported) { Text("Save vacation mode") }
            Text(
                "While on, the ticked channels switch ON and OFF at random times inside this window, so the house looks lived in.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Sunrise / sunset location", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sunLat, onValueChange = { sunLat = it }, label = { Text("Latitude") }, singleLine = true, enabled = supported, modifier = Modifier.weight(1f))
                OutlinedTextField(value = sunLon, onValueChange = { sunLon = it }, label = { Text("Longitude") }, singleLine = true, enabled = supported, modifier = Modifier.weight(1f))
            }
            Button(onClick = { saveSun() }, enabled = supported) { Text("Save location") }
            if (sunInfo.isNotEmpty()) Text(sunInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Sunrise / sunset schedules", style = MaterialTheme.typography.titleMedium)
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            sunList.forEachIndexed { i, x ->
                val dn = dayNames.filterIndexed { d, _ -> ((x[2] shr d) and 1) == 1 }.joinToString(" ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (x[0] == 0) "Sunrise" else "Sunset") + (if (x[1] >= 0) " +" else " ") + x[1] + " min: channel " + x[3] + " " + (if (x[4] == 1) "ON" else "OFF") + " (" + dn + ")",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { saveSunList(sunList.filterIndexed { j, _ -> j != i }) }, enabled = supported) { Text("Delete") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sEv == 0, onClick = { sEv = 0 }, label = { Text("Sunrise") }, enabled = supported)
                FilterChip(selected = sEv == 1, onClick = { sEv = 1 }, label = { Text("Sunset") }, enabled = supported)
                FilterChip(selected = sOn, onClick = { sOn = true }, label = { Text("ON") }, enabled = supported)
                FilterChip(selected = !sOn, onClick = { sOn = false }, label = { Text("OFF") }, enabled = supported)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sOff, onValueChange = { sOff = it.filter { c -> c.isDigit() || c == '-' }.take(4) }, label = { Text("Offset minutes") }, singleLine = true, enabled = supported, modifier = Modifier.weight(1f))
                OutlinedTextField(value = sCh, onValueChange = { sCh = it.filter { c -> c.isDigit() }.take(1) }, label = { Text("Channel") }, singleLine = true, enabled = supported, modifier = Modifier.weight(1f))
            }
            for (rg in listOf(0..3, 4..6)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in rg) {
                        FilterChip(selected = ((sDays shr d) and 1) == 1, onClick = { sDays = sDays xor (1 shl d) }, label = { Text(dayNames[d]) }, enabled = supported)
                    }
                }
            }
            Button(onClick = { addSun() }, enabled = supported) { Text("Add sun schedule") }
            Text(
                "Offset is in minutes from sunrise or sunset: minus = before, plus = after. Up to 8 entries. Uses the location above.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Certificate", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = certTicket, onValueChange = { certTicket = it },
                label = { Text("Ticket (IBN-TICKET:...)") }, maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        certMsg = applyCert(client, certTicket)
                        busy = false
                    }
                },
                enabled = !busy && certTicket.isNotBlank()
            ) { Text("Apply certificate") }
            if (certMsg.isNotEmpty()) Text(certMsg, style = MaterialTheme.typography.bodySmall)
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

private suspend fun applyCert(client: HttpNodeClient, ticket: String): String {
    val p = ticket.trim().split(":")
    if (p.size != 3 || p[0] != "IBN-TICKET") return "Ticket ka format ghalat hai"
    if (!com.ibneilyas.home.core.ControllerCert.ok(com.ibneilyas.home.core.LicenseKey.PUBLIC_B64, p[1], p[2])) return "Ticket sahi nahi"
    val info = client.info() ?: return "ESP32 tak nahi pahunch saka"
    val id = Regex("\"id\":\"([^\"]*)\"").find(info)?.groupValues?.get(1).orEmpty()
    if (id != p[1]) return "Yeh ticket " + p[1] + " ke liye hai, lekin yeh ESP32 " + id + " hai"
    val r = client.provision(p[2]) ?: return "Nahi hua. Token aur firmware (0.7.0 ya naya) check karein."
    return if (Regex("\"ok\": *true").containsMatchIn(r)) "Certificate lag gaya." else "Jawab: " + r
}
