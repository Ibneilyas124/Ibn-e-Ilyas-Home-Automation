package com.ibneilyas.home.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.Appliance
import com.ibneilyas.home.domain.Room
import com.ibneilyas.home.domain.SchedEntry

private val dayOrder = listOf(1, 2, 3, 4, 5, 6, 0)
private val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val presets = listOf("Every day" to 127, "Mon-Fri" to 62, "Sat, Sun" to 65)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    devices: List<Appliance>,
    rooms: List<Room>,
    initial: SchedEntry?,
    onSave: (Appliance, Pair<Int, Int>?, Pair<Int, Int>?, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var dev by remember { mutableStateOf(devices.firstOrNull()) }
    var days by remember { mutableStateOf(initial?.days ?: 127) }
    var useOn by remember { mutableStateOf(initial == null || initial.on) }
    var useOff by remember { mutableStateOf(initial == null || !initial.on) }
    val onS = rememberTimePickerState(
        initialHour = if (initial != null && initial.on) initial.h else 18,
        initialMinute = if (initial != null && initial.on) initial.m else 0,
        is24Hour = false
    )
    val offS = rememberTimePickerState(
        initialHour = if (initial != null && !initial.on) initial.h else 23,
        initialMinute = if (initial != null && !initial.on) initial.m else 0,
        is24Hour = false
    )
    val same = useOn && useOff && onS.hour == offS.hour && onS.minute == offS.minute
    val valid = dev != null && (useOn || useOff) && (days and 127) != 0 && !same
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add schedule" else "Edit schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device", style = MaterialTheme.typography.labelLarge)
                rooms.forEach { r ->
                    val l = devices.filter { it.roomId == r.id }
                    if (l.isNotEmpty()) {
                        Text(r.name, style = MaterialTheme.typography.bodySmall)
                        l.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { a ->
                                    FilterChip(selected = dev?.id == a.id, onClick = { if (initial == null) dev = a }, label = { Text(a.name) })
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Turn ON at", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Switch(checked = useOn, onCheckedChange = { useOn = it })
                }
                if (useOn) TimeInput(state = onS)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Turn OFF at", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Switch(checked = useOff, onCheckedChange = { useOff = it })
                }
                if (useOff) TimeInput(state = offS)
                Text("Days", style = MaterialTheme.typography.labelLarge)
                presets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, mask) ->
                            FilterChip(selected = days == mask, onClick = { days = mask }, label = { Text(label) })
                        }
                    }
                }
                dayOrder.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { b ->
                            FilterChip(selected = (days and (1 shl b)) != 0, onClick = { days = days xor (1 shl b) }, label = { Text(dayNames[b]) })
                        }
                    }
                }
                if (same) Text("ON and OFF times must be different.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dev?.let { d ->
                        val a = if (useOn) Pair(onS.hour, onS.minute) else null
                        val b = if (useOff) Pair(offS.hour, offS.minute) else null
                        onSave(d, a, b, days and 127)
                    }
                },
                enabled = valid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
