package com.ibneilyas.home.ui

import android.content.Intent
import android.speech.RecognizerIntent

object VoiceIntents {
    fun build(lang: String, offline: Boolean = false): Intent {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "ur") "ur-PK" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye, jaise: fan band karo")
        if (offline) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        return i
    }
}
