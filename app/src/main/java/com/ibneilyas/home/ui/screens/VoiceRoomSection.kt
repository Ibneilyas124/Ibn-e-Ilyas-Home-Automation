package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomSection(vm: HomeViewModel) {
    val d by vm.data.collectAsState()
    val vroom by vm.voiceRoom.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice room", style = MaterialTheme.typography.titleMedium)
        Text(
            "Voice commands without a room name go to this room.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        d.rooms.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { r ->
                    FilterChip(selected = vroom == r.id, onClick = { vm.setVoiceRoom(r.id) }, label = { Text(r.name) })
                }
            }
        }
        FilterChip(selected = vroom == null, onClick = { vm.setVoiceRoom(null) }, label = { Text("None") })
    }
}
