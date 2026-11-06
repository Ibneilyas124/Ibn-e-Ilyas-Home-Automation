package com.ibneilyas.home.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Memory
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.NodeStatus
import com.ibneilyas.home.ui.components.FlashSection
import com.ibneilyas.home.ui.components.TextDialog

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(vm: HomeViewModel, onSchedules: (String) -> Unit) {
    val nodes by vm.nodes.collectAsState()
    val d by vm.data.collectAsState()
    val isDark by vm.dark.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var room by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenFor by remember { mutableStateOf<String?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteMsg by remember { mutableStateOf("") }
    var devTaps by remember { mutableStateOf(0) }
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
            if (isDark) {
                Text("Devices", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { devTaps++ })
            } else {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF7C4DDB), Color(0xFF4F2AA8)))).padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Column {
                        Text("Devices", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.clickable { devTaps++ })
                        Text(nodes.size.toString() + (if (nodes.size == 1) " controller" else " controllers"), style = MaterialTheme.typography.bodySmall, color = Color(0xFFDDD0F7))
                    }
                }
            }
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
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (isDark) null else BorderStroke(1.dp, Color(0xFFE1D8F5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.then(if (isDark) Modifier else Modifier.drawBehind { drawRect(Color(0xFF1E7FE0), size = Size(6.dp.toPx(), size.height)) }).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cs = MaterialTheme.colorScheme
                    val boxBg = Color(0xFF1E7FE0)
                    val boxFg = Color.White
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(boxBg),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Memory, contentDescription = null, tint = boxFg) }
                    Spacer(Modifier.width(12.dp))
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
                                "genuine" -> Text("Genuine controller", style = MaterialTheme.typography.bodySmall, color = Color(0xFF12A150))
                                "unprovisioned" -> Text("Not provisioned (no certificate yet)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                "oldfw" -> Text("Firmware too old for the certificate check (needs 0.7.0)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> {}
                            }
                        }
                    }
                    IconButton(onClick = { vm.removeNode(n.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
                FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!d.mockMode) FilledTonalButton(onClick = { onSchedules(n.id) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF7C4DDB), contentColor = Color.White), modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Schedules") }
                        if (!d.mockMode) FilledTonalButton(onClick = { onSchedules("opt:" + n.id) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E7FE0), contentColor = Color.White), modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Options") }
                        FilledTonalButton(onClick = { tokenFor = n.id }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF12A150), contentColor = Color.White), modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Set token") }
                        if (n.token.isNotBlank()) FilledTonalButton(colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFFB020), contentColor = Color(0xFF2B1A00)), modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 14.dp), onClick = {
                            val i = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, "Ibn e Ilyas Home setup code:\n" + encodeSetup(n.room, n.ip, n.token))
                            ctx.startActivity(android.content.Intent.createChooser(i, "Share setup code").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Share") }
                }
                }
            }
        }
        item {
            Column {
                Button(onClick = { pasteOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7FE0), contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Paste setup code") }
                if (pasteMsg.isNotEmpty()) Text(pasteMsg, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { DiscoverySection(vm) }
        item { FlashSection() }
        item { CloudSetupSection(vm) }
        item { AppUpdateSection() }
        if (devTaps >= 5) item { RemoteSection(vm) }
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12A150), contentColor = Color.White),
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
