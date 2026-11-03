package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.Scene
import com.ibneilyas.home.domain.SceneAction
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.RoomCard
import com.ibneilyas.home.ui.components.StatTile

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenRoom: (String) -> Unit) {
    val d by vm.data.collectAsState()
    val scenes by vm.scenes.collectAsState()
    val running by vm.runningScene.collectAsState()
    val msg by vm.sceneMessage.collectAsState()
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "RelayX",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Home Automation",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.primary
            )
            Spacer(Modifier.height(8.dp))
            if (!d.mockMode && d.nodes.isNotEmpty() && d.onlineNodes < d.nodes.size) {
                val off = d.nodes.size - d.onlineNodes
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WifiOff, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (off == d.nodes.size) "Controllers offline" else "$off controller(s) offline",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Check ESP32 power and Wi-Fi. Offline devices are skipped.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Controllers", "${d.onlineNodes}/${d.nodes.size} online", Modifier.weight(1f),
                    container = cs.primaryContainer, content = cs.onPrimaryContainer, icon = Icons.Filled.Memory
                )
                StatTile(
                    "Rooms", "${d.rooms.size}", Modifier.weight(1f),
                    container = cs.secondaryContainer, content = cs.onSecondaryContainer, icon = Icons.Filled.MeetingRoom
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Active now", "${d.activeCount}", Modifier.weight(1f),
                    container = cs.tertiaryContainer, content = cs.onTertiaryContainer, icon = Icons.Filled.Bolt
                )
                StatTile(
                    "Connection", if (d.mockMode) "Mock mode" else "Wi-Fi", Modifier.weight(1f),
                    container = cs.surfaceVariant, content = cs.onSurfaceVariant, icon = Icons.Filled.Wifi
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick scenes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (scenes.isEmpty()) {
                        Text(
                            "No scenes yet. Create one in the Scenes tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(scenes, key = { it.id }) { s ->
                                FilledTonalButton(
                                    onClick = { vm.runScene(s) },
                                    enabled = running == null
                                ) { Text(if (running == s.id) "Running..." else s.name) }
                            }
                        }
                    }
                    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.primary) }
                    Button(
                        onClick = {
                            vm.runScene(Scene("all-off", "All OFF", d.appliances.map { SceneAction(it.id, false) }))
                        },
                        enabled = d.appliances.isNotEmpty() && running == null,
                        colors = ButtonDefaults.buttonColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("All OFF")
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("Rooms", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        items(d.rooms, key = { it.id }) { room ->
            RoomCard(
                room = room,
                deviceCount = d.devicesIn(room.id).size,
                activeCount = d.activeIn(room.id),
                online = d.nodeInRoom(room.id)?.online,
                onClick = { onOpenRoom(room.id) }
            )
        }
    }
}
