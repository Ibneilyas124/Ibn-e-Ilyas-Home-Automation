package com.ibneilyas.home.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.ApplianceType
import com.ibneilyas.home.domain.FreeSlot

private fun typeName(t: ApplianceType) = when (t) {
    ApplianceType.LIGHT -> "Light"
    ApplianceType.FAN -> "Fan"
    ApplianceType.SOCKET -> "Socket"
    ApplianceType.OTHER -> "Other"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddApplianceDialog(
    roomName: String,
    slots: List<FreeSlot>,
    onSave: (String, String, ApplianceType) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ApplianceType.LIGHT) }
    var nodeId by remember { mutableStateOf(slots.firstOrNull()?.nodeId) }
    var slotId by remember { mutableStateOf<String?>(null) }
    val nodes = slots.distinctBy { it.nodeId }
    val channels = slots.filter { it.nodeId == nodeId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add device to $roomName") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (slots.isEmpty()) {
                    Text("No free relay channel. Each channel can have one device. Delete a device first, or add another ESP32 in Devices.")
                } else {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Device name, e.g. Zero Bulb") }, singleLine = true
                    )
                    Text("Type", style = MaterialTheme.typography.labelLarge)
                    ApplianceType.values().toList().chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { t ->
                                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(typeName(t)) })
                            }
                        }
                    }
                    Text("ESP32", style = MaterialTheme.typography.labelLarge)
                    nodes.forEach { n ->
                        FilterChip(
                            selected = nodeId == n.nodeId,
                            onClick = { nodeId = n.nodeId; slotId = null },
                            label = { Text(n.nodeName + if (n.online) "" else " (offline)") }
                        )
                    }
                    Text("Channel", style = MaterialTheme.typography.labelLarge)
                    channels.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { s ->
                                FilterChip(selected = slotId == s.id, onClick = { slotId = s.id }, label = { Text("Ch ${s.channel}") })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { slotId?.let { onSave(it, name.trim(), type) } },
                enabled = slots.isNotEmpty() && name.isNotBlank() && slotId != null
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
