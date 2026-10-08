package com.ibneilyas.home.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.core.BrandConfig
import com.ibneilyas.home.core.License

@Composable
fun ActivationScreen(onActivated: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val id = remember { License.deviceId(ctx) }
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(BrandConfig.COMPANY_NAME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Activation required", color = MaterialTheme.colorScheme.primary)
        Text("Send this Device ID to Ibn e Ilyas Technologies. You will receive an activation code that works only on this phone.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Text(id, Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clip.setText(AnnotatedString(id)); msg = "Device ID copied" }) { Text("Copy ID") }
            OutlinedButton(onClick = {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Device ID: " + id)
                ctx.startActivity(Intent.createChooser(i, "Send Device ID"))
            }) { Text("Send ID") }
        }
        OutlinedTextField(
            value = code, onValueChange = { code = it },
            label = { Text("Activation code") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { code = clip.getText()?.text.orEmpty() }) { Text("Paste") }
            Button(
                onClick = { if (License.activate(ctx, code)) onActivated() else msg = "This code is not valid for this phone." },
                enabled = code.isNotBlank()
            ) { Text("Activate") }
        }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
    }
}
