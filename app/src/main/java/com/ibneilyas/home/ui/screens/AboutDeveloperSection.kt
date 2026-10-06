package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.core.BrandConfig

@Composable
fun AboutDeveloperSection() {
    var open by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val go: (String) -> Unit = { u ->
        try { uri.openUri(u) } catch (e: Exception) { }
    }
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) { Text("About developer") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("About developer") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sarfraz Qureshi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(BrandConfig.COMPANY_NAME, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("\uD83D\uDCCD Naushehro Feroz, Sindh, Pakistan")
                    TextButton(onClick = { go("mailto:ahmedsarfraz650@gmail.com") }) { Text("\uD83D\uDCE7 ahmedsarfraz650@gmail.com") }
                    TextButton(onClick = { go("https://wa.me/923043478576") }) { Text("\uD83D\uDCAC WhatsApp: +92 304 3478576") }
                    TextButton(onClick = { go("https://github.com/Ibneilyas124") }) { Text("\uD83C\uDF10 GitHub: Ibneilyas124") }
                    Spacer(Modifier.height(8.dp))
                    Text("Skills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("\u2022 Arduino & Embedded Systems\n\u2022 Circuit Design, Soldering & Programming\n\u2022 Hardware & Software Troubleshooting\n\u2022 IoT & Wi-Fi Projects\n\u2022 Cybersecurity")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "\u201CBuild it. Break it. Fix it. Learn from it.\u201D",
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}
