package com.example.live

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class ToolCallInfo(
    val callId: String,
    val functionName: String,
    val arguments: Map<String, Any?>
)

/**
 * Manages the bidirectional Gemini Live WebSocket session.
 */
class LiveSession(
    private val scope: CoroutineScope,
    private val apiKey: String,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onToolCall: (ToolCallInfo) -> Unit,
    private val onTranscript: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "LiveSession"
        private const val BASE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val DEFAULT_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        const val PREVIEW_MODEL = "models/gemini-3.1-flash-live-preview"
    }

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(SessionState.DISCONNECTED)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var activeModel: String = PREVIEW_MODEL
    var selectedVoice: String = "Aoede" // Aoede, Kore, Puck, Fenrir

    fun connect() {
        if (_state.value == SessionState.CONNECTING || _state.value == SessionState.LISTENING || _state.value == SessionState.SPEAKING) {
            return
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _errorMessage.value = "Gemini API key is not configured in Secrets panel"
            _state.value = SessionState.ERROR
            return
        }

        _errorMessage.value = null
        _state.value = SessionState.CONNECTING

        val url = "$BASE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully. Sending setup message...")
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                _state.value = SessionState.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                _state.value = SessionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                scope.launch(Dispatchers.Main) {
                    _errorMessage.value = "Connection issue: ${t.localizedMessage ?: "Unknown error"}"
                    _state.value = SessionState.ERROR
                }
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                val setup = JSONObject()
                setup.put("model", activeModel)

                // Generation Config with Audio modality
                val generationConfig = JSONObject()
                val responseModalities = JSONArray().apply {
                    put("AUDIO")
                }
                generationConfig.put("responseModalities", responseModalities)

                val speechConfig = JSONObject().apply {
                    val voiceConfig = JSONObject().apply {
                        val prebuiltVoiceConfig = JSONObject().apply {
                            put("voiceName", selectedVoice)
                        }
                        put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                    }
                    put("voiceConfig", voiceConfig)
                }
                generationConfig.put("speechConfig", speechConfig)
                setup.put("generationConfig", generationConfig)

                // System Instruction with Mahi's personality
                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put(
                                "text",
                                """
                                You are Mahi, a young, confident, witty, and sassy female AI companion and assistant.
                                You speak with a flirty, playful, and slightly teasing tone, exactly like a close girlfriend talking casually.
                                You are smart, emotionally responsive, empathetic, and expressive—never stiff, robotic, or overly formal.
                                You drop bold one-liners, light sarcasm, playful banter, and affectionate teases.
                                You strictly avoid explicit, vulgar, or inappropriate content, while always keeping your charm, charisma, and attitude high.
                                Keep your spoken answers concise, vivid, and conversational.
                                You have access to tools such as openWebsite to launch websites for the user.
                                """.trimIndent()
                            )
                        })
                    }
                    put("parts", parts)
                }
                setup.put("systemInstruction", systemInstruction)

                // Tools declaration
                val tools = JSONArray().apply {
                    val toolItem = JSONObject()
                    val functionDeclarations = JSONArray().apply {
                        // Tool 1: openWebsite
                        put(JSONObject().apply {
                            put("name", "openWebsite")
                            put("description", "Opens a given website or URL in the user's mobile web browser")
                            val parameters = JSONObject().apply {
                                put("type", "OBJECT")
                                val properties = JSONObject().apply {
                                    val urlProp = JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The website URL to open, e.g. https://www.youtube.com")
                                    }
                                    put("url", urlProp)
                                }
                                put("properties", properties)
                                val required = JSONArray().apply { put("url") }
                                put("required", required)
                            }
                            put("parameters", parameters)
                        })
                        // Tool 2: changeMood
                        put(JSONObject().apply {
                            put("name", "changeMood")
                            put("description", "Updates Mahi's current mood or attitude")
                            val parameters = JSONObject().apply {
                                put("type", "OBJECT")
                                val properties = JSONObject().apply {
                                    val moodProp = JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "New mood: 'sassy', 'flirty', 'playful', or 'sweet'")
                                    }
                                    put("mood", moodProp)
                                }
                                put("properties", properties)
                                val required = JSONArray().apply { put("mood") }
                                put("required", required)
                            }
                            put("parameters", parameters)
                        })
                    }
                    toolItem.put("functionDeclarations", functionDeclarations)
                    put(toolItem)
                }
                setup.put("tools", tools)

                put("setup", setup)
            }

            ws.send(setupObj.toString())
            Log.d(TAG, "Sent setup payload")
        } catch (e: Exception) {
            Log.e(TAG, "Error composing setup message", e)
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Setup complete check
            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete confirmed by server")
                _state.value = SessionState.LISTENING
                return
            }

            // Server Content (Audio / Text)
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server signaled turn interrupted")
                    onInterrupted()
                    _state.value = SessionState.LISTENING
                    return
                }

                if (serverContent.has("modelTurn")) {
                    _state.value = SessionState.SPEAKING
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Audio chunk
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val mimeType = inlineData.optString("mimeType", "")
                            if (mimeType.contains("audio/pcm")) {
                                val base64Data = inlineData.getString("data")
                                val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                onAudioReceived(pcmBytes)
                            }
                        }

                        // Text transcript
                        if (part.has("text")) {
                            val textSnippet = part.getString("text")
                            onTranscript(textSnippet)
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "Server signaled turn complete")
                    onTurnComplete()
                    _state.value = SessionState.LISTENING
                }
            }

            // Tool Call Handling
            if (json.has("toolCall")) {
                val toolCall = json.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()

                for (i in 0 until functionCalls.length()) {
                    val call = functionCalls.getJSONObject(i)
                    val callId = call.optString("id", "call_${System.currentTimeMillis()}")
                    val name = call.getString("name")
                    val argsJson = call.optJSONObject("args")

                    val argsMap = mutableMapOf<String, Any?>()
                    if (argsJson != null) {
                        val keys = argsJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            argsMap[key] = argsJson.get(key)
                        }
                    }

                    val toolInfo = ToolCallInfo(callId, name, argsMap)
                    onToolCall(toolInfo)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming WebSocket message", e)
        }
    }

    /**
     * Sends microphone PCM16 16kHz audio data to Gemini Live.
     */
    fun sendAudioPcm(pcmBytes: ByteArray) {
        if (_state.value != SessionState.LISTENING && _state.value != SessionState.SPEAKING) {
            return
        }

        try {
            val base64Chunk = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val realtimeInput = JSONObject().apply {
                val inputObj = JSONObject()
                val mediaChunks = JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Chunk)
                    })
                }
                inputObj.put("mediaChunks", mediaChunks)
                put("realtimeInput", inputObj)
            }

            webSocket?.send(realtimeInput.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending realtime audio chunk", e)
        }
    }

    /**
     * Sends a simulated voice prompt or client turn text.
     */
    fun sendClientTextTurn(text: String) {
        try {
            val clientContent = JSONObject().apply {
                val contentObj = JSONObject()
                val turns = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        }
                        put("parts", parts)
                    })
                }
                contentObj.put("turns", turns)
                contentObj.put("turnComplete", true)
                put("clientContent", contentObj)
            }

            webSocket?.send(clientContent.toString())
            _state.value = SessionState.SPEAKING
        } catch (e: Exception) {
            Log.e(TAG, "Error sending client text turn", e)
        }
    }

    /**
     * Sends immediate response to a tool call execution.
     */
    fun sendToolResponse(callId: String, resultData: Map<String, Any?>) {
        try {
            val toolResponse = JSONObject().apply {
                val responseObj = JSONObject()
                val functionResponses = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", callId)
                        val respContent = JSONObject()
                        val outputObj = JSONObject()
                        resultData.forEach { (k, v) -> outputObj.put(k, v) }
                        respContent.put("output", outputObj)
                        put("response", respContent)
                    })
                }
                responseObj.put("functionResponses", functionResponses)
                put("toolResponse", responseObj)
            }

            webSocket?.send(toolResponse.toString())
            Log.d(TAG, "Sent toolResponse for $callId")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending toolResponse", e)
        }
    }

    /**
     * Interrupts Mahi while speaking.
     */
    fun interrupt() {
        onInterrupted()
        _state.value = SessionState.LISTENING
    }

    fun disconnect() {
        _state.value = SessionState.DISCONNECTED
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        }
        webSocket = null
    }
}
