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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.Appliance
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.*

@Composable
fun RoomDetailScreen(roomId: String, vm: HomeViewModel, onSchedules: () -> Unit = {}, onBack: () -> Unit) {
    val d by vm.data.collectAsState()
    val haptic = LocalHapticFeedback.current
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
            Column {
                Row(Modifier.backHeader(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = backHeaderTint())
                    }
                    Column(Modifier.weight(1f)) {
                        Text(room.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = backHeaderText())
                        if (!isLightHeader()) {
                            NodeStatus(node?.online)
                            if (node != null && !node.online) {
                                Text(
                                    "Last seen: ${node.lastSeenMinutes} min ago",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(onClick = { edit = !edit }) {
                        Icon(
                            if (edit) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = if (edit) "Done editing" else "Edit",
                            tint = backHeaderTint()
                        )
                    }
                }
                if (isLightHeader()) {
                    Column(Modifier.padding(start = 12.dp, top = 8.dp)) {
                        NodeStatus(node?.online)
                        if (node != null && !node.online) {
                            Text(
                                "Last seen: ${node.lastSeenMinutes} min ago",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (edit) {
            item {
                TextButton(onClick = { renaming = true }) { Text("Edit room") }
            }
        }
        item {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val total = d.devicesIn(roomId).size
                val onCount = d.activeIn(roomId)
                Text(
                    if (total == 0) "No devices in this room yet." else "$onCount of $total ON",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { onCount.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                        color = Color(0xFF12A150),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { adding = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12A150), contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("+ Add", maxLines = 1) }
                    OutlinedButton(
                        onClick = { timing = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7FE0), contentColor = Color.White),
                        border = null,
                        enabled = total > 0,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Timer", maxLines = 1) }
                    OutlinedButton(
                        onClick = onSchedules,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DDB), contentColor = Color.White),
                        border = null,
                        enabled = total > 0,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Schedules", maxLines = 1) }
                }
            }
        }
        items(d.devicesIn(roomId), key = { it.id }) { a ->
            Box(Modifier.padding(start = 12.dp)) {
                ApplianceTile(a, d.stateOf(a), d.isOnline(a)) {
                    if (edit) { editing = a } else { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggle(a.id) }
                }
            }
        }
    }
}
