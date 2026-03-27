package com.clawsses.phone.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OpenRouter voice recognition client using OpenAI-compatible audio transcription API.
 *
 * Records audio and sends it to OpenRouter's /v1/audio/transcriptions endpoint.
 * Supports Whisper models available on OpenRouter.
 */
class OpenRouterVoiceClient {

    companion object {
        private const val TAG = "OpenRouterVoice"
        private const val API_BASE_URL = "https://openrouter.ai/api/v1"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2

        private const val MAX_RECORDING_DURATION_MS = 60_000L  // Max 60 seconds
    }

    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Recording : ConnectionState()
        object Processing : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var scope: CoroutineScope? = null
    private var recordingStartTime: Long = 0

    private val audioBuffer = mutableListOf<ByteArray>()

    @Volatile private var onPartialResult: ((String) -> Unit)? = null
    @Volatile private var onFinalResult: ((String) -> Unit)? = null
    @Volatile private var onError: ((String) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // Longer timeout for transcription
        .build()

    /**
     * Start voice recognition with OpenRouter.
     *
     * @param apiKey OpenRouter API key
     * @param model Model ID (e.g., "openai/whisper-large-v3")
     * @param languageTag BCP-47 language tag (optional)
     * @param onPartial Callback for partial results (not typically used for transcription)
     * @param onFinal Callback for final transcription
     * @param onError Callback for errors
     */
    fun startListening(
        apiKey: String,
        model: String,
        languageTag: String? = null,
        onPartial: (String) -> Unit = {},
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_connectionState.value == ConnectionState.Recording) {
            Log.w(TAG, "Already recording, stopping first")
            stopListening()
        }

        this.onPartialResult = onPartial
        this.onFinalResult = onFinal
        this.onError = onError

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        audioBuffer.clear()

        _connectionState.value = ConnectionState.Recording

        // Start audio capture
        startAudioCapture(apiKey, model, languageTag)
    }

    private fun startAudioCapture(apiKey: String, model: String, languageTag: String?) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ) * BUFFER_SIZE_MULTIPLIER

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            deliverError("Failed to calculate audio buffer size")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                deliverError("Failed to initialize AudioRecord")
                return
            }

            audioRecord?.startRecording()
            recordingStartTime = System.currentTimeMillis()
            Log.i(TAG, "Audio recording started (16kHz, 16-bit PCM)")

            recordingJob = scope?.launch {
                val readBuffer = ByteArray(bufferSize)
                while (isActive) {
                    val bytesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (bytesRead > 0) {
                        audioBuffer.add(readBuffer.copyOf(bytesRead))
                    }

                    // Check max duration
                    if (System.currentTimeMillis() - recordingStartTime > MAX_RECORDING_DURATION_MS) {
                        Log.i(TAG, "Max recording duration reached")
                        break
                    }
                }

                // Recording stopped, process audio
                processAudio(apiKey, model, languageTag)
            }
        } catch (e: SecurityException) {
            deliverError("Microphone permission denied")
        } catch (e: Exception) {
            deliverError("Audio capture error: ${e.message}")
        }
    }

    private suspend fun processAudio(apiKey: String, model: String, languageTag: String?) {
        _connectionState.value = ConnectionState.Processing

        try {
            // Combine PCM buffers into one byte array
            val totalSize = audioBuffer.sumOf { it.size }
            val pcmData = ByteArray(totalSize)
            var pcmOffset = 0
            for (chunk in audioBuffer) {
                chunk.copyInto(pcmData, pcmOffset)
                pcmOffset += chunk.size
            }
            val wavData = convertPcmToWav(pcmData, SAMPLE_RATE)

            // Create temp file for the audio
            val tempFile = File.createTempFile("voice_", ".wav")
            tempFile.writeBytes(wavData)

            Log.d(TAG, "Audio file prepared: ${tempFile.absolutePath}, ${wavData.size} bytes")

            // Send to OpenRouter API
            val result = transcribeAudio(apiKey, model, tempFile, languageTag)

            tempFile.delete()

            result.onSuccess { text ->
                Log.i(TAG, "Transcription: $text")
                val callback = onFinalResult
                mainHandler.post { callback?.invoke(text) }
                _connectionState.value = ConnectionState.Idle
            }.onFailure { error ->
                Log.e(TAG, "Transcription failed", error)
                deliverError(error.message ?: "Transcription failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            deliverError(e.message ?: "Processing error")
        }
    }

    private suspend fun transcribeAudio(
        apiKey: String,
        model: String,
        audioFile: File,
        languageTag: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mediaType = "audio/wav".toMediaType()
            val audioBody = audioFile.asRequestBody(mediaType)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("file", audioFile.name, audioBody)
                .apply {
                    val lang = languageTag?.split("-")?.firstOrNull()
                    if (!lang.isNullOrEmpty()) addFormDataPart("language", lang)
                }
                .build()

            val request = Request.Builder()
                .url("$API_BASE_URL/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://github.com/ynck000/clawsses")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API error: ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("API error: ${response.code}"))
            }

            // OpenRouter returns JSON with the transcription
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }

            // Parse JSON response - OpenRouter follows OpenAI format
            val text = try {
                val json = org.json.JSONObject(responseBody)
                json.getString("text")
            } catch (e: Exception) {
                // If JSON parsing fails, return as-is (some endpoints return plain text)
                responseBody.trim()
            }

            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun convertPcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 1 * 16 / 8  // sampleRate * channels * bitsPerSample / 8
        val blockAlign = 1 * 16 / 8  // channels * bitsPerSample / 8
        val dataSize = pcmData.size

        val header = ByteArray(44)
        val buffer = java.io.ByteArrayOutputStream()

        // RIFF header
        buffer.write("RIFF".toByteArray())
        buffer.write(intToByteArray(36 + dataSize))
        buffer.write("WAVE".toByteArray())

        // fmt chunk
        buffer.write("fmt ".toByteArray())
        buffer.write(intToByteArray(16))  // Subchunk1Size
        buffer.write(shortToByteArray(1))  // AudioFormat (1 = PCM)
        buffer.write(shortToByteArray(1))  // NumChannels (1 = mono)
        buffer.write(intToByteArray(sampleRate))  // SampleRate
        buffer.write(intToByteArray(byteRate))  // ByteRate
        buffer.write(shortToByteArray(blockAlign))  // BlockAlign
        buffer.write(shortToByteArray(16))  // BitsPerSample

        // data chunk
        buffer.write("data".toByteArray())
        buffer.write(intToByteArray(dataSize))

        // Write PCM data
        buffer.write(pcmData)

        return buffer.toByteArray()
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }

    private fun deliverError(message: String) {
        val callback = onError
        mainHandler.post { callback?.invoke(message) }
        _connectionState.value = ConnectionState.Error(message)
    }

    /**
     * Stop recording and processing.
     */
    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let { record ->
            try {
                record.stop()
                record.release()
            } catch (_: Exception) {}
        }
        audioRecord = null

        scope?.cancel()
        scope = null

        _connectionState.value = ConnectionState.Idle
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        stopListening()
        client.dispatcher.executorService.shutdown()
    }
}