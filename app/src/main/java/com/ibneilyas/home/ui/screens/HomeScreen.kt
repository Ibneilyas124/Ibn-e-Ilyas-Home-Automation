package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.core.BrandConfig
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.RoomCard
import com.ibneilyas.home.ui.components.StatTile

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenRoom: (String) -> Unit) {
    val d by vm.data.collectAsState()
    val subtitle by vm.subtitle.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                BrandConfig.COMPANY_NAME,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Controllers", "${d.onlineNodes}/${d.nodes.size} online", Modifier.weight(1f))
                StatTile("Rooms", "${d.rooms.size}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Active now", "${d.activeCount}", Modifier.weight(1f))
                StatTile("Connection", if (d.mockMode) "Mock mode" else "Wi-Fi", Modifier.weight(1f))
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Rooms", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        items(d.rooms, key = { it.id }) { room ->
            RoomCard(
                room = room,
                deviceCount = d.devicesIn(room.id).size,
                activeCount = d.activeIn(room.id),
                online = d.nodeInRoom(room.id)?.online,
                onClick = { onOpenRoom(room.id) }
            )
        }
    }
}
