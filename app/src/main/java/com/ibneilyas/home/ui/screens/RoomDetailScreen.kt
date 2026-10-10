package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.Appliance
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.*

@Composable
fun RoomDetailScreen(roomId: String, vm: HomeViewModel, onSchedules: () -> Unit = {}, onBack: () -> Unit) {
    val d by vm.data.collectAsState()
    var edit by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var timing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Appliance?>(null) }
    val room = d.rooms.firstOrNull { it.id == roomId }
    if (room == null) {
        Text("Room not found", Modifier.padding(24.dp))
        return
    }
    val node = d.nodeInRoom(roomId)
    if (renaming) {
        RoomEditDialog("Edit room", room.name, room.iconKey, { n, i -> vm.editRoom(roomId, n, i); renaming = false }, { vm.deleteRoom(roomId); renaming = false; onBack() }, { renaming = false })
    }
    editing?.let { a ->
        ApplianceEditDialog(
            a, d.rooms,
            { n, t, r -> vm.editAppliance(a.id, n, t, r); editing = null },
            { vm.hideAppliance(a.id); editing = null },
            { editing = null }
        )
    }
    if (adding) {
        AddApplianceDialog(
            roomName = room.name,
            slots = vm.freeSlots(),
            onSave = { s, n, t -> vm.addAppliance(s, n, t, roomId); adding = false },
            onDismiss = { adding = false }
        )
    }
    if (timing) {
        TimerDialog(
            devices = d.devicesIn(roomId),
            onStart = { id, m, o -> vm.setTimer(id, m, o); timing = false },
            onDismiss = { timing = false }
        )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(room.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    NodeStatus(node?.online)
                    if (node != null && !node.online) {
                        Text(
                            "Last seen: ${node.lastSeenMinutes} min ago",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { edit = !edit }) {
                    Icon(
                        if (edit) Icons.Filled.Check else Icons.Filled.Edit,
                        contentDescription = if (edit) "Done editing" else "Edit"
                    )
                }
            }
        }
        if (edit) {
            item {
                TextButton(onClick = { renaming = true }) { Text("Edit room") }
            }
        }
        item {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.devicesIn(roomId).isEmpty()) {
                    Text("No devices in this room yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("+ Add device") }
            }
        }
        item {
            OutlinedButton(
                onClick = { timing = true },
                enabled = d.devicesIn(roomId).isNotEmpty(),
                modifier = Modifier.padding(start = 12.dp).fillMaxWidth().height(48.dp)
            ) { Text("Set timer") }
        }
        item {
            OutlinedButton(
                onClick = onSchedules,
                enabled = d.devicesIn(roomId).isNotEmpty(),
                modifier = Modifier.padding(start = 12.dp).fillMaxWidth().height(48.dp)
            ) { Text("Schedules") }
        }
        items(d.devicesIn(roomId), key = { it.id }) { a ->
            Box(Modifier.padding(start = 12.dp)) {
                ApplianceTile(a, d.stateOf(a), d.isOnline(a)) {
                    if (edit) { editing = a } else { vm.toggle(a.id) }
                }
            }
        }
    }
}
