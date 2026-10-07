package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentVoiceSection(vm: HomeViewModel) {
    var tick by remember { mutableStateOf(0) }
    val list = remember(tick) { vm.recentVoice() }
    var teaching by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    teaching?.let { w ->
        AlertDialog(
            onDismissRequest = { teaching = null },
            title = { Text("\"$w\" should mean...") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.teachChoices().forEach { (title, words) ->
                        Text(title, style = MaterialTheme.typography.labelLarge)
                        words.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { t ->
                                    AssistChip(
                                        onClick = { vm.addCorrection(w, t); msg = "Saved: $w \u2192 $t. Try the command again."; teaching = null },
                                        label = { Text(t) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { teaching = null }) { Text("Cancel") } }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recent voice commands", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap a word the phone heard wrongly, then pick what you really meant. The app remembers it for next time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (list.isEmpty()) {
            Text("No voice commands yet. Use the mic and they will show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        list.forEach { e ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.time)), style = MaterialTheme.typography.bodySmall)
                    Text(e.result.substringBefore(" Heard:"), fontWeight = FontWeight.SemiBold)
                    Text("Heard:", style = MaterialTheme.typography.labelSmall)
                    e.heard.split(" ").filter { it.isNotBlank() }.distinct().chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { w -> AssistChip(onClick = { teaching = w }, label = { Text(w) }) }
                        }
                    }
                }
            }
        }
        if (list.isNotEmpty()) TextButton(onClick = { vm.clearVoiceLog(); tick++ }) { Text("Clear list") }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
    }
}
