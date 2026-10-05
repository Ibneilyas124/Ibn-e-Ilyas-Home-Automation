package com.ibneilyas.home.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
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
import kotlinx.coroutines.delay

@Composable
fun VoiceFab(vm: HomeViewModel) {
    val msg by vm.voiceMessage.collectAsState()
    val lang by vm.voiceLang.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (said != null) vm.voiceCommand(said) else vm.say("I did not catch that")
        }
    }
    LaunchedEffect(msg) { if (msg != null) { delay(5000); vm.clearVoice() } }
    val listen: () -> Unit = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, when (lang) { "ur" -> "ur-PK"; "hi" -> "hi-IN"; else -> "en-US" })
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command, e.g. turn on zero bulb")
        try { launcher.launch(i) } catch (e: ActivityNotFoundException) { vm.say("Voice input is not available on this phone") }
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        msg?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.widthIn(max = 280.dp)
            ) { Text(it, Modifier.padding(12.dp)) }
        }
        OutlinedButton(onClick = { vm.setVoiceLang(when (lang) { "en" -> "ur"; "ur" -> "hi"; else -> "en" }) }) { Text(when (lang) { "ur" -> "اردو"; "hi" -> "हिं"; else -> "EN" }) }
        FloatingActionButton(onClick = listen) {
            Icon(Icons.Filled.Mic, contentDescription = "Voice command")
        }
    }
}
