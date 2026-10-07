package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.data.HttpNodeClient
import com.ibneilyas.home.ui.HomeViewModel
import java.text.DateFormat
import java.util.Date

private data class ActEv(val room: String, val device: String, val on: Boolean, val src: Int, val t: Long)

private fun srcName(s: Int) = when (s) {
    0 -> "Power restored"
    2 -> "Schedule"
    3 -> "Timer"
    4 -> "Wall switch"
    else -> "App or voice"
}

@Composable
fun ActivityScreen(vm: HomeViewModel, onBack: () -> Unit) {
    val nodes by vm.nodes.collectAsState()
    val d by vm.data.collectAsState()
    var events by remember { mutableStateOf<List<ActEv>>(emptyList()) }
    var status by remember { mutableStateOf("Loading...") }
    LaunchedEffect(nodes, d.mockMode) {
        if (d.mockMode || nodes.isEmpty()) {
            status = "History is recorded by the ESP32 itself. Switch to Real ESP32 mode and add an ESP32 to see it here."
            events = emptyList()
            return@LaunchedEffect
        }
        var failed = 0
        val all = ArrayList<ActEv>()
        for (cfg in nodes) {
            val txt = HttpNodeClient("http://${cfg.ip}", cfg.token).events()
            if (txt == null) { failed++; continue }
            for (p in txt.split(";")) {
                val x = p.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (x.size != 4) continue
                val ch = x[1].toInt()
                val name = d.appliances.firstOrNull { it.nodeId == cfg.id && it.channel == ch }?.name ?: "Channel $ch"
                all.add(ActEv(cfg.room, name, x[2] == 1L, x[3].toInt(), x[0]))
            }
        }
        events = all.sortedWith(compareByDescending<ActEv> { it.t })
        status = if (failed > 0) "$failed ESP32 not reachable" else ""
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Activity history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        if (status.isNotEmpty()) item { Text(status, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp)) }
        if (status.isEmpty() && events.isEmpty()) item { Text("No activity recorded yet.", modifier = Modifier.padding(start = 12.dp)) }
        items(events.size) { i ->
            val e = events[i]
            val whenText = if (e.t > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.t * 1000)) else "time unknown"
            Card(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${e.device} (${e.room}) \u2192 ${if (e.on) "ON" else "OFF"}", fontWeight = FontWeight.SemiBold)
                    Text(srcName(e.src) + "  \u2022  " + whenText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
