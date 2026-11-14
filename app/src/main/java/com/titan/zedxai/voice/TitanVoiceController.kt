package com.titan.zedxai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

enum class OrbVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class VoiceSessionState(
    val orbState: OrbVisualState = OrbVisualState.IDLE,
    val liveTranscript: String = "",
    val lastFinalTranscript: String = "",
    val languageTag: String = "en-IN",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val isMuted: Boolean = false,
    val wakeWordArmed: Boolean = false,
    val rmsNormalized: Float = 0f,
    val statusMessage: String = "Tap mic to speak in English, Hindi, or Hinglish",
    val isRecognizerAvailable: Boolean = true
)

class TitanVoiceController(
    private val context: Context,
    private val onFinalTranscript: (String) -> Unit
) {
    private val _state = MutableStateFlow(
        VoiceSessionState(
            isRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        )
    )
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                applyTtsConfig(_state.value.languageTag, _state.value.speechRate, _state.value.pitch)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.update { it.copy(orbState = OrbVisualState.SPEAKING, statusMessage = "Speaking...") }
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.update { it.copy(orbState = OrbVisualState.IDLE, statusMessage = "Ready") }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.update { it.copy(orbState = OrbVisualState.IDLE, statusMessage = "TTS error") }
                    }
                })
            }
        }
    }

    fun updateSettings(
        languageTag: String,
        speechRate: Float,
        pitch: Float,
        wakeWordArmed: Boolean
    ) {
        _state.update {
            it.copy(
                languageTag = languageTag,
                speechRate = speechRate.coerceIn(0.5f, 2.0f),
                pitch = pitch.coerceIn(0.5f, 2.0f),
                wakeWordArmed = wakeWordArmed
            )
        }
        applyTtsConfig(languageTag, speechRate, pitch)
    }

    private fun applyTtsConfig(languageTag: String, rate: Float, pitch: Float) {
        val locale = when (languageTag) {
            "hi-IN" -> Locale("hi", "IN")
            "en-US" -> Locale.US
            else -> Locale("en", "IN")
        }
        tts?.language = locale
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun startListening() {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.update {
                it.copy(
                    isRecognizerAvailable = false,
                    statusMessage = "Speech recognition service unavailable on this device. Use text input."
                )
            }
            return
        }
        val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also {
            speechRecognizer = it
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.update {
                    it.copy(
                        orbState = OrbVisualState.LISTENING,
                        liveTranscript = "",
                        statusMessage = "Listening (${it.languageTag})..."
                    )
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _state.update { it.copy(rmsNormalized = normalized) }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _state.update { it.copy(orbState = OrbVisualState.THINKING, rmsNormalized = 0f, statusMessage = "Processing speech...") }
            }

            override fun onError(error: Int) {
                _state.update {
                    it.copy(
                        orbState = OrbVisualState.IDLE,
                        rmsNormalized = 0f,
                        statusMessage = mapSpeechError(error)
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (text.isNotEmpty()) {
                    val cleaned = stripWakeWordIfNeeded(text, _state.value.wakeWordArmed)
                    _state.update {
                        it.copy(
                            orbState = OrbVisualState.THINKING,
                            liveTranscript = cleaned,
                            lastFinalTranscript = cleaned,
                            statusMessage = "Thinking..."
                        )
                    }
                    onFinalTranscript(cleaned)
                } else {
                    _state.update { it.copy(orbState = OrbVisualState.IDLE, statusMessage = "Did not catch that") }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotEmpty()) {
                    _state.update { it.copy(liveTranscript = partial) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (_state.value.languageTag == "hinglish") "en-IN" else _state.value.languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    fun setThinking() {
        _state.update { it.copy(orbState = OrbVisualState.THINKING, statusMessage = "ZEDX AI thinking...") }
    }

    fun speakResponse(text: String) {
        if (_state.value.isMuted || !ttsReady || text.isBlank()) {
            _state.update { it.copy(orbState = OrbVisualState.IDLE, statusMessage = "Ready") }
            return
        }
        val cleanText = text.replace(Regex("```[\\s\\S]*?```"), "Code block generated.")
            .replace(Regex("[#*_`>]"), "")
            .take(1500)
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun toggleMute() {
        val nowMuted = !_state.value.isMuted
        if (nowMuted) stopSpeaking()
        _state.update { it.copy(isMuted = nowMuted) }
    }

    fun stopSpeaking() {
        tts?.stop()
        if (_state.value.orbState == OrbVisualState.SPEAKING) {
            _state.update { it.copy(orbState = OrbVisualState.IDLE, statusMessage = "Interrupted") }
        }
    }

    fun stopAll() {
        runCatching { speechRecognizer?.cancel() }
        runCatching { tts?.stop() }
        ZedxVoiceForegroundService.stop(context)
        _state.update {
            it.copy(orbState = OrbVisualState.IDLE, rmsNormalized = 0f, statusMessage = "Voice stopped")
        }
    }

    fun release() {
        stopAll()
        runCatching { speechRecognizer?.destroy() }
        runCatching { tts?.shutdown() }
        speechRecognizer = null
        tts = null
    }

    companion object {
        fun stripWakeWordIfNeeded(transcript: String, wakeWordEnabled: Boolean): String {
            if (!wakeWordEnabled) return transcript
            return transcript.replace(Regex("^(hey\\s+titan|ok\\s+titan|zedx)[,\\s]*", RegexOption.IGNORE_CASE), "")
                .ifBlank { transcript }
        }

        fun mapSpeechError(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
            else -> "Speech recognizer idle (code $code)."
        }
    }
}
