package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.NodeStatus
import com.ibneilyas.home.ui.components.FlashSection
import com.ibneilyas.home.ui.components.TextDialog

@Composable
fun DevicesScreen(vm: HomeViewModel, onSchedules: (String) -> Unit) {
    val nodes by vm.nodes.collectAsState()
    val d by vm.data.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var room by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenFor by remember { mutableStateOf<String?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteMsg by remember { mutableStateOf("") }
    tokenFor?.let { id ->
        TextDialog("Enter token", "", { vm.setToken(id, it); tokenFor = null }, { tokenFor = null })
    }
    if (pasteOpen) {
        TextDialog("Paste setup code", "", { code ->
            val t = decodeSetup(code)
            pasteMsg = if (t == null) "Invalid code"
            else if (nodes.any { it.ip == t.second.trim() }) "Already added"
            else { vm.addNode(t.first, t.second, t.third); "Added" }
            pasteOpen = false
        }, { pasteOpen = false })
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Devices", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (d.mockMode) {
                Text(
                    "Mock mode is active. Add an ESP32 to use real mode.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(nodes, key = { it.id }) { n ->
            val online = d.nodes.firstOrNull { it.id == n.id }?.online
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.room, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(n.ip, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!d.mockMode) NodeStatus(online)
                        if (!d.mockMode) {
                            var gs by remember(n.id, n.ip, online) { mutableStateOf("") }
                            LaunchedEffect(n.id, n.ip, n.token, online) {
                                gs = com.ibneilyas.home.data.HttpNodeClient("http://" + n.ip, n.token).genuineStatus()
                            }
                            when (gs) {
                                "genuine" -> Text("Genuine controller", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                "unprovisioned" -> Text("Not provisioned (no certificate yet)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                "oldfw" -> Text("Firmware too old for the certificate check (needs 0.7.0)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> {}
                            }
                        }
                        if (!d.mockMode) TextButton(onClick = { onSchedules(n.id) }) { Text("Schedules") }
                        if (!d.mockMode) TextButton(onClick = { onSchedules("opt:" + n.id) }) { Text("Options") }
                        TextButton(onClick = { tokenFor = n.id }) { Text("Set token") }
                        if (n.token.isNotBlank()) TextButton(onClick = {
                            val i = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, "Ibn e Ilyas Home setup code:\n" + encodeSetup(n.room, n.ip, n.token))
                            ctx.startActivity(android.content.Intent.createChooser(i, "Share setup code").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Share") }
                    }
                    IconButton(onClick = { vm.removeNode(n.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
        item {
            Column {
                OutlinedButton(onClick = { pasteOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Paste setup code") }
                if (pasteMsg.isNotEmpty()) Text(pasteMsg, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { DiscoverySection(vm) }
        item { FlashSection() }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Add ESP32", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        item {
            OutlinedTextField(
                value = room, onValueChange = { room = it },
                label = { Text("Room name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = ip, onValueChange = { ip = it },
                label = { Text("ESP32 IP address") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text("Token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = { vm.addNode(room, ip, token); room = ""; ip = ""; token = "" },
                enabled = room.isNotBlank() && ip.isNotBlank() && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Add ESP32") }
        }
    }
}

private fun encodeSetup(room: String, ip: String, token: String): String {
    val raw = "$room\n$ip\n$token"
    return "IBN1:" + android.util.Base64.encodeToString(
        raw.toByteArray(Charsets.UTF_8),
        android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
    )
}

private fun decodeSetup(code: String): Triple<String, String, String>? {
    val s = code.trim()
    if (!s.startsWith("IBN1:")) return null
    return try {
        val raw = String(android.util.Base64.decode(s.removePrefix("IBN1:"), android.util.Base64.URL_SAFE), Charsets.UTF_8)
        val p = raw.split("\n")
        if (p.size == 3 && p[0].isNotBlank() && p[1].isNotBlank() && p[2].isNotBlank()) Triple(p[0], p[1], p[2]) else null
    } catch (e: IllegalArgumentException) {
        null
    }
}
