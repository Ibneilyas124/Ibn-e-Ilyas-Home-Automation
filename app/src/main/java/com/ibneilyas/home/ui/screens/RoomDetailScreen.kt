package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.ApplianceTile
import com.ibneilyas.home.ui.components.NodeStatus

@Composable
fun RoomDetailScreen(roomId: String, vm: HomeViewModel, onBack: () -> Unit) {
    val d by vm.data.collectAsState()
    val room = d.rooms.firstOrNull { it.id == roomId }
    if (room == null) {
        Text("Room not found", Modifier.padding(24.dp))
        return
    }
    val node = d.nodeInRoom(roomId)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(room.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    NodeStatus(node?.online)
                    if (node != null && !node.online) {
                        Text(
                            "Last seen: ${node.lastSeenMinutes} min ago",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(d.devicesIn(roomId), key = { it.id }) { a ->
            Box(Modifier.padding(start = 12.dp)) {
                ApplianceTile(a, d.stateOf(a), d.isOnline(a)) { vm.toggle(a.id) }
            }
        }
    }
}
