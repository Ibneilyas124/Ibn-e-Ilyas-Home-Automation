package com.ibneilyas.home.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.Appliance
import com.ibneilyas.home.domain.ApplianceType

@Composable
fun TextDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = v, onValueChange = { v = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }, enabled = v.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun typeLabel(t: ApplianceType) = when (t) {
    ApplianceType.LIGHT -> "Light"
    ApplianceType.FAN -> "Fan"
    ApplianceType.SOCKET -> "Socket"
    ApplianceType.OTHER -> "Other"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplianceEditDialog(
    a: Appliance,
    onSave: (String, ApplianceType) -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(a.name) }
    var type by remember { mutableStateOf(a.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                ApplianceType.values().toList().chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(typeLabel(t)) })
                        }
                    }
                }
                TextButton(onClick = onHide) { Text("Hide this device") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), type) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
