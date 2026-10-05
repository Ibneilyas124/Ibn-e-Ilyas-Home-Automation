package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.ApplianceType
import com.ibneilyas.home.domain.SceneAction
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.SceneEditDialog

@Composable
fun ScenesScreen(vm: HomeViewModel) {
    val d by vm.data.collectAsState()
    val scenes by vm.scenes.collectAsState()
    val running by vm.runningScene.collectAsState()
    val msg by vm.sceneMessage.collectAsState()
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        SceneEditDialog(d.appliances, d.rooms, { n, a -> vm.addScene(n, a); adding = false }, { adding = false })
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Scenes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        if (scenes.isEmpty()) {
            item { Text("No scenes yet. Tap Add scene below.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(scenes, key = { it.id }) { s ->
            val busy = running == s.id
            Card(
                onClick = { vm.runScene(s) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (busy) "Running..." else "${s.actions.size} actions \u2022 tap to run",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.deleteScene(s.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete scene")
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add scene")
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val lights = d.appliances.filter { it.type == ApplianceType.LIGHT }.map { SceneAction(it.id, false) }
                    vm.addScene("All Lights OFF", lights)
                },
                enabled = d.appliances.any { it.type == ApplianceType.LIGHT },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Create All Lights OFF") }
        }
    }
}
