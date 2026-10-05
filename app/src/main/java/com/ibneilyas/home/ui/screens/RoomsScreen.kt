package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.RoomCard
import com.ibneilyas.home.ui.components.RoomEditDialog

@Composable
fun RoomsScreen(vm: HomeViewModel, onOpenRoom: (String) -> Unit) {
    val d by vm.data.collectAsState()
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        RoomEditDialog("Add room", "", "room", { n, i -> vm.addRoom(n, i); adding = false }, null, { adding = false })
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
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
        item {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add room")
            }
        }
    }
}
