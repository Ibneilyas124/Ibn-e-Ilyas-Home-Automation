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
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.RoomCard

@Composable
fun RoomsScreen(vm: HomeViewModel, onOpenRoom: (String) -> Unit) {
    val d by vm.data.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Rooms", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
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
