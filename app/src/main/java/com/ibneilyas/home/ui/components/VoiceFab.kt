package com.ibneilyas.home.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.VoiceIntents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VoiceFab(vm: HomeViewModel) {
    val msg by vm.voiceMessage.collectAsState()
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf("en") }
    var retry by remember { mutableStateOf(false) }
    val listenRef = remember { arrayOfNulls<(String) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        } else null
        if (said.isNullOrEmpty()) {
            vm.say(if (retry) "Could not understand. Please try again." else "I did not catch that")
            retry = false
        } else {
            scope.launch {
                val out = vm.voiceTry(said, lang, !retry)
                if (out == null) {
                    retry = true
                    lang = if (lang == "ur") "en" else "ur"
                    vm.say("Dobara boliye...")
                    listenRef[0]?.invoke(lang)
                } else {
                    vm.say(out)
                    retry = false
                }
            }
        }
    }
    listenRef[0] = { l ->
        try {
            launcher.launch(VoiceIntents.build(l, vm.preferOffline.value))
        } catch (e: ActivityNotFoundException) {
            vm.say("Voice input is not available on this phone")
        }
    }
    LaunchedEffect(msg) { if (msg != null) { delay(6000); vm.clearVoice() } }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        msg?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.widthIn(max = 280.dp)
            ) { Text(it, Modifier.padding(12.dp)) }
        }
        FloatingActionButton(onClick = {
            retry = false
            lang = vm.voiceLang.value
            listenRef[0]?.invoke(lang)
        }) {
            Icon(Icons.Filled.Mic, contentDescription = "Voice command")
        }
    }
}
