package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel

@Composable
fun VoiceCorrectionsSection(vm: HomeViewModel) {
    val corr by vm.corrections.collectAsState()
    var heard by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = !open }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Voice corrections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(SViolet).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(corr.size.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        if (open) {
            Text(
                "If the phone keeps mishearing a word, teach it here. Example: when it hears listen, treat it as kitchen. " +
                    "Treat-as can be a room or device word (kitchen, living) or on, off, fan, light, socket.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            corr.entries.sortedBy { it.key }.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.key + "  \u2192  " + e.value, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.removeCorrection(e.key) }) { Text("Remove", color = Color(0xFFE24B4A)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = heard, onValueChange = { heard = it },
                    label = { Text("Phone hears") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = target, onValueChange = { target = it },
                    label = { Text("Treat as") }, singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { vm.addCorrection(heard, target); heard = ""; target = "" },
                enabled = heard.isNotBlank() && target.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SGreen, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Add correction") }
        }
    }
}
