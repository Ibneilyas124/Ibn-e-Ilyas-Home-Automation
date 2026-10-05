package com.ibneilyas.home.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.Appliance
import com.ibneilyas.home.domain.Room
import com.ibneilyas.home.domain.SceneAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditDialog(
    appliances: List<Appliance>,
    rooms: List<Room>,
    onSave: (String, List<SceneAction>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val choice = remember { mutableStateMapOf<String, Boolean>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New scene") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Scene name") }, singleLine = true)
                rooms.forEach { r ->
                    val list = appliances.filter { it.roomId == r.id }
                    if (list.isNotEmpty()) {
                        Text(r.name, style = MaterialTheme.typography.labelLarge)
                        list.forEach { a ->
                            Text(a.name)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = choice[a.id] == null, onClick = { choice.remove(a.id) }, label = { Text("Skip") })
                                FilterChip(selected = choice[a.id] == true, onClick = { choice[a.id] = true }, label = { Text("ON") })
                                FilterChip(selected = choice[a.id] == false, onClick = { choice[a.id] = false }, label = { Text("OFF") })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), choice.map { SceneAction(it.key, it.value) }) },
                enabled = name.isNotBlank() && choice.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
