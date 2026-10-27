package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel
import kotlinx.coroutines.launch

/** Customer remote setup. Hidden on the owner's phone, which has broker settings but no cloud home. */
@Composable
fun CloudSetupSection(vm: HomeViewModel) {
    val scope = rememberCoroutineScope()
    val hidden = remember { vm.remoteSettings().first.isNotBlank() && vm.cloudEsp().isBlank() }
    var esp by remember { mutableStateOf(vm.cloudEsp()) }
    var key by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    if (!hidden) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Remote access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (esp.isBlank()) {
                Text("Add your controller below first, then enter the activation key from Ibn e Ilyas Technologies. Your phone must be on the home Wi-Fi.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Activation key") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            msg = "Setting up. Please wait..."
                            val r = vm.cloudActivate(key)
                            esp = vm.cloudEsp()
                            msg = r ?: "Remote access is on."
                            if (r == null) key = ""
                            busy = false
                        }
                    },
                    enabled = !busy && key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Activate remote access") }
            } else {
                Text("Remote access is set up for " + esp + ".", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            msg = "Sending the settings..."
                            val r = vm.cloudResend()
                            msg = r ?: "The controller is connected to the cloud."
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Send settings to the controller again") }
            }
            if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
        }
    }
}
