package com.ibneilyas.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewModelScope
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.VoiceIntents
import kotlinx.coroutines.launch

class VoiceActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()
    private var lang = "en"
    private var retry = false

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val list = if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        } else null
        if (list.isNullOrEmpty()) {
            finish()
        } else {
            vm.viewModelScope.launch {
                val out = vm.voiceTry(list, lang, !retry)
                if (out == null) {
                    retry = true
                    lang = if (lang == "ur") "en" else "ur"
                    Toast.makeText(applicationContext, "Dobara boliye...", Toast.LENGTH_SHORT).show()
                    listen()
                } else {
                    Toast.makeText(applicationContext, out, Toast.LENGTH_LONG).show()
                    vm.waitSpeech()
                    finish()
                }
            }
        }
    }

    private fun listen() {
        try {
            launcher.launch(VoiceIntents.build(lang, vm.preferOffline.value))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Voice input is not available on this phone", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.ibneilyas.home.core.License.isActive(applicationContext)) {
            Toast.makeText(this, "Please open the app and activate it first", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        lang = vm.voiceLang.value
        listen()
    }
}
