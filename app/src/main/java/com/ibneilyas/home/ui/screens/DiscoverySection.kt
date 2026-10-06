package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.data.Found
import com.ibneilyas.home.data.NodeDiscovery
import com.ibneilyas.home.ui.HomeViewModel
import kotlinx.coroutines.delay

@Composable
fun DiscoverySection(vm: HomeViewModel) {
    val ctx = LocalContext.current
    val disc = remember { NodeDiscovery(ctx) }
    val found = remember { mutableStateListOf<Found>() }
    val known by vm.nodes.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<Found?>(null) }
    DisposableEffect(Unit) { onDispose { disc.stop() } }
    LaunchedEffect(scanning) { if (scanning) { delay(10000); disc.stop() } }
    adding?.let { f ->
        var room by remember(f) { mutableStateOf("") }
        var token by remember(f) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(f.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f.ip, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room name") }, singleLine = true)
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.addNode(room, f.ip, token); adding = null },
                    enabled = room.isNotBlank() && token.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                found.clear()
                scanned = false
                disc.start(
                    { f -> if (found.none { it.name == f.name }) found.add(f) },
                    { s -> scanning = s; if (!s) scanned = true }
                )
            },
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (scanning) "Scanning..." else "Scan for ESP32 on Wi-Fi") }
        found.forEach { f ->
            val added = known.any { it.ip == f.ip }
            OutlinedButton(
                onClick = { adding = f },
                enabled = !added,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(if (added) "${f.name} (already added)" else "${f.name}  \u2022  ${f.ip}") }
        }
        if (scanned && found.isEmpty()) {
            Text(
                "No ESP32 found. Check it is powered, on the same Wi-Fi, and set up with firmware 0.2.0 or newer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
