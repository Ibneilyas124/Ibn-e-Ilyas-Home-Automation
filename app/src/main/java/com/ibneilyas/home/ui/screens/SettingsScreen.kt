package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.core.BrandConfig
import com.ibneilyas.home.ui.HomeViewModel

@Composable
fun SettingsScreen(vm: HomeViewModel) {
    val d by vm.data.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (d.mockMode) "Mode: Mock (no hardware)" else "Mode: Real ESP32",
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedButton(onClick = { vm.useMock() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Use Mock mode")
        }
        OutlinedButton(onClick = { vm.useReal() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Use Real ESP32 mode")
        }
        OutlinedButton(onClick = { vm.restoreHidden() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Restore hidden devices") }
        Spacer(Modifier.height(16.dp))
        Text(BrandConfig.COMPANY_NAME, fontWeight = FontWeight.SemiBold)
        Text(BrandConfig.OWNER_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
