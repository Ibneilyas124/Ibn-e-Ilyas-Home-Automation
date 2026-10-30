package com.ibneilyas.home.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ibneilyas.home.data.Alert
import com.ibneilyas.home.data.GeoCfg
import com.ibneilyas.home.data.HomeGeo
import com.ibneilyas.home.ui.HomeViewModel

// Settings: "Arrive home" - ghar ki jagah, Scene, radius, on/off
@SuppressLint("MissingPermission")
@Composable
fun GeoSection(vm: HomeViewModel) {
    val ctx = LocalContext.current
    val scenes by vm.scenes.collectAsState()
    var cfg by remember { mutableStateOf(HomeGeo.load(ctx)) }
    var msg by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(HomeGeo.status(ctx)) }
    var rad by remember { mutableStateOf(cfg?.radius ?: 150f) }
    val c = cfg

    fun applyCfg(n: GeoCfg) {
        HomeGeo.save(ctx, n)
        cfg = n
        if (n.enabled) {
            Alert.ask(ctx)
            HomeGeo.register(ctx)
        } else {
            HomeGeo.unregister(ctx)
        }
        status = HomeGeo.status(ctx)
    }

    fun grabHere() {
        LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    msg = "Location not available, try near a window or outdoors"
                } else {
                    val o = cfg
                    applyCfg(GeoCfg(o?.enabled ?: false, loc.latitude, loc.longitude, o?.radius ?: rad, o?.sceneId ?: "", o?.sceneName ?: ""))
                    msg = "Home saved"
                }
            }
            .addOnFailureListener { e -> msg = "Location error: " + (e.message ?: "unknown") }
    }

    fun pick(id: String, name: String) {
        val o = cfg
        if (o == null) {
            msg = "Set home here first"
        } else {
            applyCfg(o.copy(sceneId = id, sceneName = name))
        }
    }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            grabHere()
        } else {
            msg = "Location permission denied"
        }
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        msg = if (ok) "Background location allowed" else "Open app settings and choose Allow all the time"
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Arrive home", fontWeight = FontWeight.Bold)
            Text("When you reach home you get a notification. Tap it to run a scene.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", Modifier.weight(1f))
                Switch(checked = c?.enabled == true, onCheckedChange = { on ->
                    val o = cfg
                    if (o == null) {
                        msg = "Set home here first"
                    } else if (on && o.sceneId.isEmpty()) {
                        msg = "Choose a scene first"
                    } else {
                        applyCfg(o.copy(enabled = on))
                    }
                })
            }
            Text(if (c == null) "Home: not set" else "Home: " + String.format("%.5f, %.5f", c.lat, c.lon))
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    grabHere()
                } else {
                    locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }) { Text("Set home here") }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 29) bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }) { Text("Allow location all the time") }
            Text("Radius: ${rad.toInt()} m")
            Slider(
                value = rad,
                onValueChange = { rad = it },
                valueRange = 100f..500f,
                onValueChangeFinished = {
                    val o = cfg
                    if (o != null) applyCfg(o.copy(radius = rad))
                }
            )
            Text("Scene to run", fontWeight = FontWeight.SemiBold)
            if (scenes.isEmpty()) Text("No scenes yet. Create one in Scenes first.")
            scenes.forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { pick(s.id, s.name) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = c?.sceneId == s.id, onClick = { pick(s.id, s.name) })
                    Text(s.name)
                }
            }
            TextButton(onClick = { status = HomeGeo.status(ctx) }) { Text("Status: $status (tap to refresh)") }
            if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
        }
    }
}
