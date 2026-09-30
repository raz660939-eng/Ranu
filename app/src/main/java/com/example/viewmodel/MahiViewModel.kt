package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioStreamer
import com.example.live.LiveSession
import com.example.live.MahiVoiceFallback
import com.example.live.SessionState
import com.example.live.ToolCallInfo
import com.example.tools.ExecutedToolEvent
import com.example.tools.MahiToolExecutor
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MahiUiState(
    val sessionState: SessionState = SessionState.DISCONNECTED,
    val mood: String = "Sassy & Flirty",
    val inputAmplitude: Float = 0f,
    val outputAmplitude: Float = 0f,
    val transcriptSnippet: String = "",
    val activeToolEvent: ExecutedToolEvent? = null,
    val isMicPermissionGranted: Boolean = false,
    val errorMessage: String? = null,
    val voiceName: String = "Aoede",
    val modelName: String = "gemini-3.1-flash-live-preview",
    val isMuted: Boolean = false,
    val quickPrompts: List<QuickPrompt> = listOf(
        QuickPrompt("Hey Mahi, tease me a little 😉", "Tease me"),
        QuickPrompt("What do you think of me? 💕", "Compliment"),
        QuickPrompt("Open YouTube for some music 🎵", "Open YouTube", "https://youtube.com"),
        QuickPrompt("Open Google for me 🌐", "Open Google", "https://google.com"),
        QuickPrompt("Tell me your best witty comeback 🔥", "Witty Comeback"),
        QuickPrompt("What are you up to right now? ✨", "Casual Chit-chat")
    )
)

data class QuickPrompt(
    val text: String,
    val label: String,
    val actionUrl: String? = null
)

class MahiViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MahiViewModel"
    }

    private val _uiState = MutableStateFlow(MahiUiState())
    val uiState: StateFlow<MahiUiState> = _uiState.asStateFlow()

    private val audioStreamer = AudioStreamer(viewModelScope)
    private var liveSession: LiveSession? = null
    private var toolExecutor: MahiToolExecutor? = null
    private var voiceFallback: MahiVoiceFallback? = null
    private var toolBannerDismissJob: Job? = null

    init {
        toolExecutor = MahiToolExecutor(application.applicationContext) { event ->
            _uiState.update { it.copy(activeToolEvent = event) }
            toolBannerDismissJob?.cancel()
            toolBannerDismissJob = viewModelScope.launch {
                delay(6000)
                _uiState.update { it.copy(activeToolEvent = null) }
            }
        }

        voiceFallback = MahiVoiceFallback(
            context = application.applicationContext,
            onSpeechStarted = {
                _uiState.update { it.copy(sessionState = SessionState.SPEAKING) }
            },
            onSpeechEnded = {
                _uiState.update { it.copy(sessionState = SessionState.LISTENING) }
            }
        )

        setupLiveSession()

        // Monitor audio levels
        viewModelScope.launch {
            audioStreamer.inputAmplitude.collect { amp ->
                _uiState.update { it.copy(inputAmplitude = amp) }
            }
        }

        viewModelScope.launch {
            audioStreamer.outputAmplitude.collect { amp ->
                _uiState.update { it.copy(outputAmplitude = amp) }
            }
        }
    }

    private fun setupLiveSession() {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        liveSession = LiveSession(
            scope = viewModelScope,
            apiKey = apiKey,
            onAudioReceived = { pcmBytes ->
                audioStreamer.playAudio(pcmBytes)
            },
            onInterrupted = {
                audioStreamer.interruptAndFlush()
                voiceFallback?.stop()
                _uiState.update { it.copy(sessionState = SessionState.LISTENING) }
            },
            onTurnComplete = {
                audioStreamer.notifyTurnFinished()
                _uiState.update { it.copy(sessionState = SessionState.LISTENING) }
            },
            onToolCall = { toolCall ->
                handleToolCall(toolCall)
            },
            onTranscript = { snippet ->
                _uiState.update { it.copy(transcriptSnippet = snippet) }
            }
        )

        // Observe session state
        viewModelScope.launch {
            liveSession?.state?.collect { state ->
                _uiState.update { it.copy(sessionState = state) }
            }
        }

        viewModelScope.launch {
            liveSession?.errorMessage?.collect { err ->
                if (err != null) {
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }
    }

    private fun handleToolCall(toolCall: ToolCallInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = toolExecutor?.execute(toolCall.functionName, toolCall.arguments)
                ?: mapOf("status" to "error")
            liveSession?.sendToolResponse(toolCall.callId, result)
        }
    }

    fun onMicPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isMicPermissionGranted = granted) }
    }

    /**
     * Toggles main session connection (Power / Central button).
     */
    fun toggleConnection() {
        val currentState = _uiState.value.sessionState
        if (currentState == SessionState.DISCONNECTED || currentState == SessionState.ERROR) {
            startSession()
        } else {
            disconnectSession()
        }
    }

    fun startSession() {
        _uiState.update { it.copy(errorMessage = null) }
        liveSession?.connect()

        if (_uiState.value.isMicPermissionGranted && !_uiState.value.isMuted) {
            audioStreamer.startRecording { chunk ->
                liveSession?.sendAudioPcm(chunk)
            }
        }
    }

    fun disconnectSession() {
        audioStreamer.stopRecording()
        audioStreamer.interruptAndFlush()
        voiceFallback?.stop()
        liveSession?.disconnect()
        _uiState.update {
            it.copy(
                sessionState = SessionState.DISCONNECTED,
                transcriptSnippet = "",
                inputAmplitude = 0f,
                outputAmplitude = 0f
            )
        }
    }

    /**
     * Immediate interruption when user taps interrupt or talks over Mahi.
     */
    fun interrupt() {
        audioStreamer.interruptAndFlush()
        voiceFallback?.stop()
        liveSession?.interrupt()
        _uiState.update { it.copy(sessionState = SessionState.LISTENING) }
    }

    /**
     * Triggers a quick voice prompt.
     * If session is connected, sends via Gemini Live; if offline or fallback, speaks via smart synthesizer!
     */
    fun triggerQuickPrompt(prompt: QuickPrompt) {
        // If action url is present, execute tool directly
        if (prompt.actionUrl != null) {
            toolExecutor?.execute("openWebsite", mapOf("url" to prompt.actionUrl))
        }

        val currentState = _uiState.value.sessionState
        if (currentState == SessionState.LISTENING || currentState == SessionState.SPEAKING) {
            // Live session is active: send client turn to Gemini Live
            liveSession?.sendClientTextTurn(prompt.text)
            _uiState.update { it.copy(transcriptSnippet = "You: ${prompt.text}") }
        } else {
            // If disconnected, connect first and speak prompt or provide witty preview
            startSession()
            viewModelScope.launch {
                delay(600)
                if (liveSession?.state?.value == SessionState.LISTENING) {
                    liveSession?.sendClientTextTurn(prompt.text)
                } else {
                    // Sassy immediate fallback response
                    val wittyFallbackReplies = mapOf(
                        "Tease me" to "Oh, look who decided to show up! Did you miss me that much, or do you just need my brilliant company?",
                        "Compliment" to "You? You're cute when you're trying to figure out if I'm being serious or just totally messing with you.",
                        "Open YouTube" to "Opening YouTube for you, darling. Try not to get lost in cat videos for four hours this time!",
                        "Open Google" to "Google is opening right now. What are we secretly researching today, detective?",
                        "Witty Comeback" to "I'd agree with you, but then we'd both be wrong! Anything else you need to be schooled on?",
                        "Casual Chit-chat" to "Just chilling in the cyber ether looking fabulous, waiting for you to say something interesting. What's on your mind?"
                    )
                    val reply = wittyFallbackReplies[prompt.label]
                        ?: "I'm right here! Talk to me, don't be shy."
                    _uiState.update {
                        it.copy(
                            sessionState = SessionState.SPEAKING,
                            transcriptSnippet = "Mahi: $reply"
                        )
                    }
                    voiceFallback?.speak(reply)
                }
            }
        }
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = newMuted) }
        if (newMuted) {
            audioStreamer.stopRecording()
        } else if (_uiState.value.isMicPermissionGranted && _uiState.value.sessionState == SessionState.LISTENING) {
            audioStreamer.startRecording { chunk ->
                liveSession?.sendAudioPcm(chunk)
            }
        }
    }

    fun setMood(newMood: String) {
        _uiState.update { it.copy(mood = newMood) }
    }

    fun setVoice(voice: String) {
        _uiState.update { it.copy(voiceName = voice) }
        liveSession?.selectedVoice = voice
    }

    override fun onCleared() {
        super.onCleared()
        audioStreamer.release()
        liveSession?.disconnect()
        voiceFallback?.shutdown()
    }
}
