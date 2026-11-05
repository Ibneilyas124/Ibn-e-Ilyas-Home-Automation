package com.ibneilyas.home.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.Appliance
import com.ibneilyas.home.domain.ApplianceType
import com.ibneilyas.home.domain.Room

val roomIconChoices = listOf(
    "bed" to "Bedroom", "sofa" to "Living", "kitchen" to "Kitchen",
    "bath" to "Bathroom", "garage" to "Garage", "workshop" to "Workshop", "room" to "Other"
)

private fun typeLabel(t: ApplianceType) = when (t) {
    ApplianceType.LIGHT -> "Light"
    ApplianceType.FAN -> "Fan"
    ApplianceType.SOCKET -> "Socket"
    ApplianceType.OTHER -> "Other"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomEditDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Room name") }, singleLine = true)
                roomIconChoices.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (k, lbl) ->
                            FilterChip(selected = icon == k, onClick = { icon = k }, colors = dlgChipColors(), label = { Text(lbl) })
                        }
                    }
                }
                if (onDelete != null) TextButton(onClick = onDelete, colors = dlgDangerColors()) { Text("Delete room") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), icon) }, enabled = name.isNotBlank(), colors = dlgSaveColors()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplianceEditDialog(
    a: Appliance,
    rooms: List<Room>,
    onSave: (String, ApplianceType, String) -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(a.name) }
    var type by remember { mutableStateOf(a.type) }
    var roomId by remember { mutableStateOf(a.roomId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit device") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                ApplianceType.values().toList().chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, colors = dlgChipColors(), label = { Text(typeLabel(t)) })
                        }
                    }
                }
                Text("Room", style = MaterialTheme.typography.labelLarge)
                rooms.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { r ->
                            FilterChip(selected = roomId == r.id, onClick = { roomId = r.id }, colors = dlgChipColors(), label = { Text(r.name) })
                        }
                    }
                }
                TextButton(onClick = onHide, colors = dlgDangerColors()) { Text("Delete this device") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), type, roomId) }, enabled = name.isNotBlank(), colors = dlgSaveColors()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TextDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = v, onValueChange = { v = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }, enabled = v.isNotBlank(), colors = dlgSaveColors()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

internal val DlgGreen = Color(0xFF12A150)
internal val DlgBlue = Color(0xFF1E7FE0)
internal val DlgViolet = Color(0xFF7C4DDB)
internal val DlgAmber = Color(0xFFFFB020)
internal val DlgRed = Color(0xFFE5484D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun dlgChipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = DlgBlue, selectedLabelColor = Color.White)

@Composable
internal fun dlgSaveColors() = ButtonDefaults.textButtonColors(contentColor = DlgGreen)

@Composable
internal fun dlgDangerColors() = ButtonDefaults.textButtonColors(contentColor = DlgRed)
