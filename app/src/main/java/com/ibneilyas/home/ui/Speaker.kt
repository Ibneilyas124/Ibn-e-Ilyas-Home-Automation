package com.ibneilyas.home.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** English spoken replies. `speaking` stays true until the phone finishes talking. */
class Speaker(ctx: Context) {
    @Volatile
    var speaking = false
        private set
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.US)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { speaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaking = false }
                })
                ready = true
                val p = pending
                pending = null
                if (p != null) go(p)
            } else {
                speaking = false
                pending = null
            }
        }
    }

    private fun go(text: String) {
        val r = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ibn")
        if (r != TextToSpeech.SUCCESS) speaking = false
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        speaking = true
        if (ready) go(text) else pending = text
    }

    fun stop() {
        tts?.stop()
        speaking = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        speaking = false
    }
}
