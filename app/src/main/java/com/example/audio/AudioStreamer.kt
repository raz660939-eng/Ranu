package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * AudioStreamer manages:
 * 1. Microphone input recording at 16kHz PCM16 (Mono) for streaming to Gemini Live.
 * 2. Speaker output playback at 24kHz PCM16 (Mono) received from Gemini Live.
 * 3. Real-time amplitude calculation for waveform visualizers.
 * 4. Low-latency interruption handling.
 */
class AudioStreamer(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val INPUT_SAMPLE_RATE = 16000
        const val OUTPUT_SAMPLE_RATE = 24000
        private const val CHUNK_SIZE_SAMPLES = 1024
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBufSize, OUTPUT_SAMPLE_RATE * 2)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(onChunk: (ByteArray) -> Unit) {
        if (_isRecording.value) return

        val minBufSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufSize <= 0) {
            Log.e(TAG, "Invalid buffer size for AudioRecord: $minBufSize")
            return
        }

        val bufferSize = max(minBufSize, CHUNK_SIZE_SAMPLES * 2 * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_SIZE_SAMPLES * 2) // 1024 16-bit samples
                while (isActive && _isRecording.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        // Calculate input amplitude
                        val amp = calculateRms(buffer, bytesRead)
                        _inputAmplitude.value = amp

                        // Send copy of chunk to callback
                        val chunkCopy = buffer.copyOf(bytesRead)
                        onChunk(chunkCopy)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _isRecording.value = false
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        _inputAmplitude.value = 0f
    }

    /**
     * Plays 24kHz PCM16 audio bytes received from Gemini Live.
     */
    fun playAudio(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return

        try {
            if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initAudioTrack()
            }

            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.play()
            }

            _isPlaying.value = true
            val amp = calculateRms(pcmData, pcmData.size)
            _outputAmplitude.value = amp

            audioTrack?.write(pcmData, 0, pcmData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    /**
     * Instantly stops playback and flushes buffers (Interruption handler).
     */
    fun interruptAndFlush() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
            _isPlaying.value = false
            _outputAmplitude.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error interrupting audio", e)
        }
    }

    fun notifyTurnFinished() {
        _isPlaying.value = false
        _outputAmplitude.value = 0f
    }

    fun release() {
        stopRecording()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    private fun calculateRms(pcmBytes: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSquares = 0.0
        val shortCount = length / 2
        val byteBuffer = ByteBuffer.wrap(pcmBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until shortCount) {
            val sample = byteBuffer.short.toDouble()
            sumSquares += sample * sample
        }

        val rms = sqrt(sumSquares / shortCount)
        val normalized = (rms / 32768.0).toFloat()
        return min(1.0f, normalized * 3.5f) // Boost slightly for visual effect
    }
}
