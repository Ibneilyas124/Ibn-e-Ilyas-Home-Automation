package com.ibneilyas.home.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.core.BrandConfig
import com.ibneilyas.home.core.License
import com.ibneilyas.home.data.HouseholdActivation
import com.ibneilyas.home.ui.components.DlgBlue
import com.ibneilyas.home.ui.components.DlgGreen
import com.ibneilyas.home.ui.components.DlgViolet
import com.ibneilyas.home.ui.components.dlgChipColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(onActivated: () -> Unit, onHouseholdNode: (String, String) -> Unit = { _, _ -> }) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val id = remember { License.deviceId(ctx) }
    var mode by remember { mutableStateOf(1) }
    var code by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dev by remember { mutableStateOf(false) }
    if (dev) DeveloperInfoDialog { dev = false }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        RelayXLogo()
        Text("Activation required", color = DlgGreen, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 1, onClick = { mode = 1; msg = "" }, colors = dlgChipColors(), label = { Text("Household key") })
            FilterChip(selected = mode == 0, onClick = { mode = 0; msg = "" }, colors = dlgChipColors(), label = { Text("Phone code") })
        }
        if (mode == 1) {
            Text("Enter the household key from Ibn e Ilyas Technologies, your controller's IP address and its token. Your phone must be on the home Wi-Fi.")
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Household key") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("Controller IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Controller token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { key = clip.getText()?.text.orEmpty() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = DlgBlue)) { Text("Paste key") }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            msg = "Checking with the controller..."
                            val r = HouseholdActivation.run(ctx, key, ip, token)
                            busy = false
                            if (r == null) {
                                onHouseholdNode(ip.trim().removePrefix("http://").trimEnd('/'), token.trim())
                                onActivated()
                            } else {
                                msg = r
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DlgGreen, contentColor = Color.White),
                    enabled = !busy && key.isNotBlank() && ip.isNotBlank() && token.isNotBlank()
                ) { Text("Activate") }
            }
        }
        if (mode == 0) {
            Text("Send this Device ID to Ibn e Ilyas Technologies. You will receive an activation code that works only on this phone.")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Text(id, Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clip.setText(AnnotatedString(id)); msg = "Device ID copied" }, colors = ButtonDefaults.outlinedButtonColors(contentColor = DlgBlue)) { Text("Copy ID") }
                OutlinedButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Device ID: " + id)
                    ctx.startActivity(Intent.createChooser(i, "Send Device ID"))
                }, colors = ButtonDefaults.outlinedButtonColors(contentColor = DlgBlue)) { Text("Send ID") }
            }
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Activation code") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { code = clip.getText()?.text.orEmpty() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = DlgBlue)) { Text("Paste") }
                Button(
                    onClick = { if (License.activate(ctx, code)) onActivated() else msg = "This code is not valid for this phone." },
                    colors = ButtonDefaults.buttonColors(containerColor = DlgGreen, contentColor = Color.White),
                    enabled = code.isNotBlank()
                ) { Text("Activate") }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = if (listOf("not valid", "annot", "ould not", "ailed", "nvalid", "rror", "wrong").any { msg.contains(it) }) Color(0xFFE5484D) else DlgGreen)
        Spacer(Modifier.height(16.dp))
        Text("If you do not have an activation code, please contact the developer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { dev = true }, colors = ButtonDefaults.buttonColors(containerColor = DlgViolet, contentColor = Color.White), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Contact developer") }
    }
}
