package com.ibneilyas.home.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.Appliance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerDialog(
    devices: List<Appliance>,
    onStart: (String, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var sel by remember { mutableStateOf(devices.firstOrNull()?.id) }
    var on by remember { mutableStateOf(false) }
    var mins by remember { mutableStateOf("30") }
    val m = mins.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device", style = MaterialTheme.typography.labelLarge)
                devices.forEach { a ->
                    FilterChip(selected = sel == a.id, onClick = { sel = a.id }, label = { Text(a.name) })
                }
                Text("When the time is up", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !on, onClick = { on = false }, label = { Text("Turn OFF") })
                    FilterChip(selected = on, onClick = { on = true }, label = { Text("Turn ON") })
                }
                Text("Minutes", style = MaterialTheme.typography.labelLarge)
                listOf(5, 15, 30, 60, 120, 240).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { v ->
                            FilterChip(selected = mins == "$v", onClick = { mins = "$v" }, label = { Text("$v") })
                        }
                    }
                }
                OutlinedTextField(
                    value = mins,
                    onValueChange = { mins = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Custom minutes (1 to 1440)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TextButton(onClick = { sel?.let { id -> onStart(id, 0, on) } }) { Text("Cancel the timer on this device") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { sel?.let { id -> if (m != null) onStart(id, m, on) } },
                enabled = sel != null && m != null && m in 1..1440
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
