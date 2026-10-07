package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel

@Composable
fun VoiceCorrectionsSection(vm: HomeViewModel) {
    val corr by vm.corrections.collectAsState()
    var heard by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice corrections", style = MaterialTheme.typography.titleMedium)
        Text(
            "If the phone keeps mishearing a word, teach it here. Example: when it hears listen, treat it as kitchen. " +
                "Treat-as can be a room or device word (kitchen, living) or on, off, fan, light, socket.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        corr.entries.sortedBy { it.key }.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.key + "  \u2192  " + e.value, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removeCorrection(e.key) }) { Text("Remove") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = heard, onValueChange = { heard = it },
                label = { Text("Phone hears") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = target, onValueChange = { target = it },
                label = { Text("Treat as") }, singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        Button(
            onClick = { vm.addCorrection(heard, target); heard = ""; target = "" },
            enabled = heard.isNotBlank() && target.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Add correction") }
    }
}
