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
import com.ibneilyas.home.ui.components.TextDialog

@Composable
fun DevicesScreen(vm: HomeViewModel, onSchedules: (String) -> Unit) {
    val nodes by vm.nodes.collectAsState()
    val d by vm.data.collectAsState()
    var room by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenFor by remember { mutableStateOf<String?>(null) }
    tokenFor?.let { id ->
        TextDialog("Enter token", "", { vm.setToken(id, it); tokenFor = null }, { tokenFor = null })
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
                        if (!d.mockMode) TextButton(onClick = { onSchedules(n.id) }) { Text("Schedules") }
                        TextButton(onClick = { tokenFor = n.id }) { Text("Set token") }
                    }
                    IconButton(onClick = { vm.removeNode(n.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
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
