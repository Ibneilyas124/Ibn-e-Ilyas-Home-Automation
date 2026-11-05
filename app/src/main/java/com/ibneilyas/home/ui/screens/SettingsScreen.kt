package com.ibneilyas.home.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
            CardHeader("Mode", SBlue, Icons.Default.Settings)
            Text(
                if (d.mockMode) "Mode: Mock (no hardware)" else "Mode: Real ESP32",
                color = SGreen
            )
            Button(onClick = { vm.useMock() }, colors = ButtonDefaults.buttonColors(containerColor = SBlue, contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Use Mock mode") }
            Button(onClick = { vm.useReal() }, colors = ButtonDefaults.buttonColors(containerColor = SBlue, contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Use Real ESP32 mode") }
            OutlinedButton(onClick = { vm.restoreHidden() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Restore hidden devices") }
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
            CardHeader("Data and backup", SAmber, Icons.Default.Info, Color(0xFF2B1A00))
            Text(vm.storageSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onActivity, colors = ButtonDefaults.buttonColors(containerColor = SViolet, contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Activity history") }
            Button(onClick = { exporter.launch("home_config.json") }, colors = ButtonDefaults.buttonColors(containerColor = SBlue, contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Export configuration") }
            Button(onClick = { importer.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = SBlue, contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Import configuration") }
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


val SBlue = Color(0xFF1E7FE0)
val SGreen = Color(0xFF12A150)
val SViolet = Color(0xFF7C4DDB)
val SAmber = Color(0xFFFFB020)

@Composable
fun CardHeader(title: String, color: Color, icon: ImageVector, iconTint: Color = Color.White) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
