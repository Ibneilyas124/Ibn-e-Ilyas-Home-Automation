package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel

@Composable
fun VoiceOptionsSection(vm: HomeViewModel) {
    val speak by vm.speakReplies.collectAsState()
    val offline by vm.preferOffline.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice options", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spoken replies (English)", modifier = Modifier.weight(1f))
            Switch(checked = speak, onCheckedChange = { vm.setSpeakReplies(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Prefer offline speech recognition", modifier = Modifier.weight(1f))
            Switch(checked = offline, onCheckedChange = { vm.setPreferOffline(it) })
        }
        Text(
            "Offline recognition needs an offline speech pack for that language on your phone " +
                "(Google app or phone Settings > Voice > Offline speech recognition; names vary). " +
                "Buttons and Wi-Fi control never need internet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
