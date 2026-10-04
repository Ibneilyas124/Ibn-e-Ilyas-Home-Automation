package com.ibneilyas.home.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.domain.*

fun roomIcon(key: String): ImageVector = when (key) {
    "bed" -> Icons.Filled.Bed
    "sofa" -> Icons.Filled.Weekend
    "kitchen" -> Icons.Filled.Kitchen
    else -> Icons.Filled.MeetingRoom
}

fun applianceIcon(t: ApplianceType): ImageVector = when (t) {
    ApplianceType.LIGHT -> Icons.Filled.Lightbulb
    ApplianceType.FAN -> Icons.Filled.Air
    ApplianceType.SOCKET -> Icons.Filled.Power
    ApplianceType.OTHER -> Icons.Filled.Bolt
}

@Composable
fun NodeStatus(online: Boolean?) {
    val cs = MaterialTheme.colorScheme
    val icon = if (online == true) Icons.Filled.Wifi else Icons.Filled.WifiOff
    val text = when (online) {
        true -> "ESP32 Online"
        false -> "ESP32 Offline"
        null -> "No controller"
    }
    val tint = if (online == true) cs.primary else cs.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(cs.surface).padding(16.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
fun RoomCard(
    room: Room,
    deviceCount: Int,
    activeCount: Int,
    online: Boolean?,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(roomIcon(room.iconKey), contentDescription = null, tint = cs.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val word = if (deviceCount == 1) "Device" else "Devices"
                Text(
                    "$deviceCount $word \u2022 $activeCount ON",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                NodeStatus(online)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun ApplianceTile(
    appliance: Appliance,
    state: ApplianceState,
    online: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isOn = online && state.isOn
    val container = when {
        !online -> cs.surface
        isOn -> cs.primary
        else -> cs.surfaceVariant
    }
    val content = if (isOn) cs.onPrimary else cs.onSurface
    val subtitle = when {
        state.cmd == CmdState.SENDING -> "Sending\u2026"
        state.cmd == CmdState.FAILED -> "Failed \u2022 Tap to retry"
        !online -> "Device offline \u2022 Tap to retry"
        isOn -> "ON"
        else -> "OFF"
    }
    val pill = when {
        !online -> "OFFLINE"
        isOn -> "ON"
        else -> "OFF"
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = if (!online) BorderStroke(1.dp, cs.error) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (!online) Icons.Filled.WifiOff else applianceIcon(appliance.type),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(appliance.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.cmd == CmdState.SENDING) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = content)
            } else {
                Text(
                    pill,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(content.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
