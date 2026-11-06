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
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ibneilyas.home.data.Alert
import com.ibneilyas.home.data.GeoCfg
import com.ibneilyas.home.data.HomeGeo
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.GeoRunService

// Settings: "Arrive home" - ghar ki jagah, Scene, radius, on/off
@SuppressLint("MissingPermission")
@Composable
fun GeoSection(vm: HomeViewModel) {
    val ctx = LocalContext.current
    val scenes by vm.scenes.collectAsState()
    var cfg by remember { mutableStateOf(HomeGeo.load(ctx)) }
    var msg by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(HomeGeo.isAuto(ctx)) }
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

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader("Arrive home", SGreen, Icons.Default.LocationOn)
            Text("When you reach home your scene runs by itself. Turn Automatic off to get a notification instead.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", Modifier.weight(1f))
                Switch(colors = gsw(), checked = c?.enabled == true, onCheckedChange = { on ->
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Automatic (no tap needed)", Modifier.weight(1f))
                Switch(colors = gsw(), checked = auto, onCheckedChange = { v ->
                    auto = v
                    HomeGeo.setAuto(ctx, v)
                })
            }
            Text(if (c == null) "Home: not set" else "Home: " + String.format("%.5f, %.5f", c.lat, c.lon))
            Button(colors = ButtonDefaults.buttonColors(containerColor = SGreen, contentColor = Color.White), onClick = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    grabHere()
                } else {
                    locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }) { Text("Set home here") }
            Button(colors = ButtonDefaults.buttonColors(containerColor = SBlue, contentColor = Color.White), onClick = {
                if (Build.VERSION.SDK_INT >= 29) bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }) { Text("Allow location all the time") }
            Text("Radius: ${rad.toInt()} m")
            Slider(
                value = rad,
                onValueChange = { rad = it },
                valueRange = 100f..500f,
                colors = SliderDefaults.colors(thumbColor = SGreen, activeTrackColor = SGreen),
                onValueChangeFinished = {
                    val o = cfg
                    if (o != null) applyCfg(o.copy(radius = rad))
                }
            )
            Text("Scene to run", fontWeight = FontWeight.SemiBold)
            if (scenes.isEmpty()) Text("No scenes yet. Create one in Scenes first.")
            scenes.forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { pick(s.id, s.name) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = c?.sceneId == s.id, onClick = { pick(s.id, s.name) }, colors = RadioButtonDefaults.colors(selectedColor = SGreen))
                    Text(s.name)
                }
            }
            Button(colors = ButtonDefaults.buttonColors(containerColor = SBlue, contentColor = Color.White), onClick = {
                val o = cfg
                if (o == null || o.sceneId.isEmpty()) {
                    msg = "Set home and choose a scene first"
                } else {
                    msg = if (GeoRunService.start(ctx)) "Test started, the scene runs in a few seconds" else "Could not start the service"
                }
            }) { Text("Test now (run arrival scene)") }
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = SBlue), onClick = { status = HomeGeo.status(ctx) }) { Text("Status: $status (tap to refresh)") }
            val lg = HomeGeo.logText(ctx)
            Text(if (lg.isEmpty()) "Geofence log: empty" else "Geofence log:\n" + lg, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (msg.isNotEmpty()) {
                val bad = listOf("not available", "error", "first", "denied", "Open app settings", "Could not").any { msg.contains(it) }
                Text(msg, color = if (bad) Color(0xFFE24B4A) else SGreen)
            }
    }
}


@Composable
private fun gsw() = SwitchDefaults.colors(checkedTrackColor = SGreen, checkedThumbColor = Color.White, checkedBorderColor = SGreen)
