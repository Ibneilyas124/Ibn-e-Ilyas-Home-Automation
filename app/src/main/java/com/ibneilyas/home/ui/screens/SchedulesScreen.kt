package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.data.HttpNodeClient
import com.ibneilyas.home.ui.HomeViewModel
import kotlinx.coroutines.launch

data class SchedItem(val h: Int, val m: Int, val days: Int, val ch: Int, val on: Boolean)

private val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

private fun parse(s: String): List<SchedItem> = s.split(";").mapNotNull { p ->
    val x = p.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (x.size == 5) SchedItem(x[0], x[1], x[2], x[3], x[4] == 1) else null
}

private fun encode(l: List<SchedItem>) =
    l.joinToString(";") { "${it.h},${it.m},${it.days},${it.ch},${if (it.on) 1 else 0}" }

private fun daysText(d: Int) =
    if (d == 127) "Every day"
    else dayNames.filterIndexed { i, _ -> (d and (1 shl i)) != 0 }.joinToString(" ")

private fun timeText(h: Int, m: Int): String {
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(nodeId: String, vm: HomeViewModel, onBack: () -> Unit) {
    val nodes by vm.nodes.collectAsState()
    val cfg = nodes.firstOrNull { it.id == nodeId }
    if (cfg == null) {
        Text("Node not found", Modifier.padding(24.dp))
        return
    }
    val client = remember(cfg.id, cfg.ip, cfg.token) { HttpNodeClient("http://${cfg.ip}", cfg.token) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<SchedItem>>(emptyList()) }
    var status by remember { mutableStateOf("Loading...") }
    var clock by remember { mutableStateOf("") }

    suspend fun reload() {
        val t = client.time()
        val s = client.schedules()
        val tm = t?.let { Regex("\"time\":\"(\\d\\d:\\d\\d)\"").find(it)?.groupValues?.get(1) }
        clock = when {
            t == null -> ""
            t.contains("\"synced\":true") && tm != null -> "ESP32 time: $tm"
            else -> "ESP32 clock not synced yet (needs internet)"
        }
        if (s == null) status = "Cannot reach ESP32" else { list = parse(s); status = "" }
    }

    fun save(next: List<SchedItem>) = scope.launch {
        val r = client.setSchedules(encode(next))
        if (r == null) {
            status = "Failed. Not saved."
        } else {
            list = parse(r)
            status = if (list.size == next.size) "Saved to ESP32" else "ESP32 rejected an entry (channel too high?)"
        }
    }
    LaunchedEffect(cfg.id) { reload() }
    var hour by remember { mutableStateOf("22") }
    var minute by remember { mutableStateOf("00") }
    var ch by remember { mutableStateOf("1") }
    var on by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(127) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("Schedules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(cfg.room, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(Modifier.padding(start = 12.dp)) {
                if (clock.isNotEmpty()) Text(clock, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
            }
        }
        items(list.size) { i ->
            val s = list[i]
            Card(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${timeText(s.h, s.m)}  \u2022  Channel ${s.ch} ${if (s.on) "ON" else "OFF"}", fontWeight = FontWeight.SemiBold)
                        Text(daysText(s.days), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { save(list.filterIndexed { j, _ -> j != i }) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hour, { hour = it.take(2) }, label = { Text("Hour 0-23") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(minute, { minute = it.take(2) }, label = { Text("Min 0-59") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(ch, { ch = it.take(2) }, label = { Text("Channel") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                dayNames.indices.toList().chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { i ->
                            FilterChip(
                                selected = (days and (1 shl i)) != 0,
                                onClick = { days = days xor (1 shl i) },
                                label = { Text(dayNames[i]) }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !on, onClick = { on = false }, label = { Text("Turn OFF") })
                    FilterChip(selected = on, onClick = { on = true }, label = { Text("Turn ON") })
                }
                val h = hour.toIntOrNull()
                val m = minute.toIntOrNull()
                val c = ch.toIntOrNull()
                val ok = h != null && h in 0..23 && m != null && m in 0..59 &&
                    c != null && c >= 1 && days != 0 && list.size < 20
                Button(
                    onClick = { save(list + SchedItem(h!!, m!!, days, c!!, on)) },
                    enabled = ok,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Add schedule") }
            }
        }
    }
}
