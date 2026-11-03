package com.ibneilyas.home.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.core.BrandConfig
import com.ibneilyas.home.ui.HomeViewModel

@Composable
fun SettingsScreen(vm: HomeViewModel, onActivity: () -> Unit = {}) {
    val d by vm.data.collectAsState()
    val subtitle by vm.subtitle.collectAsState()
    var sub by remember(subtitle) { mutableStateOf(subtitle) }
    var msg by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            msg = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
                "Backup saved"
            } catch (e: Exception) { "Export failed" }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            msg = try {
                val t = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (t == null) "Could not read file" else vm.importJson(t)
            } catch (e: Exception) { "Import failed" }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SettingsCard {
            Text("Mode", style = MaterialTheme.typography.titleMedium)
            Text(
                if (d.mockMode) "Mode: Mock (no hardware)" else "Mode: Real ESP32",
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(onClick = { vm.useMock() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Use Mock mode") }
            OutlinedButton(onClick = { vm.useReal() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Use Real ESP32 mode") }
            OutlinedButton(onClick = { vm.restoreHidden() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Restore hidden devices") }
        }
        SettingsCard {
            Text("Home subtitle", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = sub, onValueChange = { sub = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { vm.setSubtitle(sub) },
                enabled = sub.isNotBlank() && sub.trim() != subtitle,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Save subtitle") }
            Text(
                "The company name is fixed and cannot be changed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsCard { AppearanceSection(vm) }
        SettingsCard {
            VoiceOptionsSection(vm)
            VoiceRoomSection(vm)
            RecentVoiceSection(vm)
            VoiceCorrectionsSection(vm)
        }
        SettingsCard { WidgetSection() }
        SettingsCard { GeoSection(vm) }
        SettingsCard {
            Text("Data and backup", style = MaterialTheme.typography.titleMedium)
            Text(vm.storageSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onActivity, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Activity history") }
            OutlinedButton(onClick = { exporter.launch("home_config.json") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Export configuration") }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Import configuration") }
            Text(
                "Backup does not include tokens or Wi-Fi passwords.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
        }
        SettingsCard {
            AboutDeveloperSection()
            Text("RelayX Home Automation", fontWeight = FontWeight.SemiBold)
            Text(BrandConfig.OWNER_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
