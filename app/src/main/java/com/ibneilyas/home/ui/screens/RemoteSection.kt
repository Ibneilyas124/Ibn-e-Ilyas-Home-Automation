package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel

@Composable
fun RemoteSection(vm: HomeViewModel) {
    val s = remember { vm.remoteSettings() }
    var host by remember { mutableStateOf(s.first) }
    var user by remember { mutableStateOf(s.second) }
    var pass by remember { mutableStateOf(s.third) }
    var msg by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Remote access (outside home)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Cloud broker settings. Leave empty to use home Wi-Fi only.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text("Broker host") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Broker username") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Broker password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                vm.setRemote(host, user, pass)
                msg = if (host.isNotBlank() && user.isNotBlank() && pass.isNotEmpty()) "Saved. Remote is on." else "Saved. Remote is off."
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Save remote settings") }
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
    }
}
