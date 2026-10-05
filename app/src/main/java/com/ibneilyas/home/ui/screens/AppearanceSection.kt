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
import com.ibneilyas.home.ui.theme.accentChoices

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(vm: HomeViewModel) {
    val dark by vm.dark.collectAsState()
    val accent by vm.accent.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (dark) "Dark mode" else "Light mode", modifier = Modifier.weight(1f))
            Switch(checked = dark, onCheckedChange = { vm.setDark(it) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accentChoices.forEach { (k, _) ->
                FilterChip(
                    selected = accent == k,
                    onClick = { vm.setAccent(k) },
                    label = { Text(k.replaceFirstChar { it.uppercase() }) }
                )
            }
        }
    }
}
