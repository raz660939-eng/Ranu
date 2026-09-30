package com.example.live

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class MahiVoiceFallback(
    context: Context,
    private val onSpeechStarted: () -> Unit,
    private val onSpeechEnded: () -> Unit
) {
    companion object {
        private const val TAG = "MahiVoiceFallback"
    }

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(1.25f) // Slightly higher, playful, sassy pitch
                tts?.setSpeechRate(1.08f) // Quick, witty pace
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeechStarted()
                    }

                    override fun onDone(utteranceId: String?) {
                        onSpeechEnded()
                    }

                    override fun onError(utteranceId: String?) {
                        onSpeechEnded()
                    }
                })
                _isReady.value = true
                Log.d(TAG, "TTS Initialized successfully with sassy girlfriend cadence")
            } else {
                Log.e(TAG, "Failed to initialize TTS")
            }
        }
    }

    fun speak(text: String) {
        if (!_isReady.value) return
        val utteranceId = "mahi_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        onSpeechEnded()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
