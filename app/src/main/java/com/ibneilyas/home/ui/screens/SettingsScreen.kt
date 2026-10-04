package com.ibneilyas.home.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel

@Composable
fun SettingsScreen(vm: HomeViewModel) {
    val d by vm.data.collectAsState()
    var ip by remember { mutableStateOf(vm.savedIp()) }
    var token by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (d.mockMode) "Mode: Mock (no hardware)" else "Mode: Real ESP32",
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("ESP32 IP address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { vm.connect(ip, token) },
            enabled = ip.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Connect to ESP32") }
        OutlinedButton(
            onClick = { vm.useMock() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Use Mock mode") }
    }
}
