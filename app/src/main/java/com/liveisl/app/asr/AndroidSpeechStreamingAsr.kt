package com.liveisl.app.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Factory picks Sherpa when offline models are present; otherwise Android SpeechRecognizer
 * (works offline when the user has installed language packs). Mock is for UI tests.
 */
object StreamingAsrFactory {
    fun create(context: Context, preferMock: Boolean = false): StreamingAsr {
        if (preferMock) return MockStreamingAsr()
        val modelsDir = File(context.filesDir, "asr")
        val sherpaReady = File(modelsDir, "tokens.txt").exists() &&
            (File(modelsDir, "encoder.onnx").exists() || File(modelsDir, "model.onnx").exists())
        return if (sherpaReady) {
            SherpaStreamingAsr(context, modelsDir)
        } else {
            AndroidSpeechStreamingAsr(context.applicationContext)
        }
    }
}

class SherpaStreamingAsr(
    context: Context,
    private val modelsDir: File,
) : StreamingAsr by AndroidSpeechStreamingAsr(context) {
    override val engineKind: AsrEngineKind = AsrEngineKind.SHERPA
    override val engineLabel: String =
        "Sherpa-ONNX models @ ${modelsDir.name} (JNI pending — using speech bridge)"
}

/**
 * Continuous Android SpeechRecognizer — same restart behavior as before the
 * debounce / silence-timeout / cancel-before-restart / online-fallback experiments.
 */
class AndroidSpeechStreamingAsr(
    private val context: Context,
) : StreamingAsr {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _partials = MutableSharedFlow<AsrPartial>(extraBufferCapacity = 64)
    private val _listening = MutableStateFlow(false)

    override val partials: SharedFlow<AsrPartial> = _partials.asSharedFlow()
    override val isListening: StateFlow<Boolean> = _listening.asStateFlow()
    override val engineKind: AsrEngineKind = AsrEngineKind.ANDROID_SPEECH
    override val engineLabel: String = "Android SpeechRecognizer (on-device when packs installed)"

    private var recognizer: SpeechRecognizer? = null
    private var language: AsrLanguage = AsrLanguage.ENGLISH
    private var listenStartedAt = 0L
    private var shouldRestart = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() {
            listenStartedAt = SystemClock.elapsedRealtime()
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) {
            if (shouldRestart && _listening.value) {
                scope.launch { restartListening() }
            }
        }

        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = texts?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                val latency = SystemClock.elapsedRealtime() - listenStartedAt
                scope.launch {
                    _partials.emit(
                        AsrPartial(
                            text = text,
                            isFinal = true,
                            latencyMs = latency.coerceAtLeast(0),
                            engine = engineLabel,
                        ),
                    )
                }
            }
            if (shouldRestart && _listening.value) {
                scope.launch { restartListening() }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = texts?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                val latency = SystemClock.elapsedRealtime() - listenStartedAt
                scope.launch {
                    _partials.emit(
                        AsrPartial(
                            text = text,
                            isFinal = false,
                            latencyMs = latency.coerceAtLeast(0),
                            engine = engineLabel,
                        ),
                    )
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override suspend fun start(language: AsrLanguage) {
        this.language = if (language == AsrLanguage.AUTO) AsrLanguage.ENGLISH else language
        shouldRestart = true
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _partials.emit(
                AsrPartial(
                    text = "",
                    isFinal = true,
                    latencyMs = 0,
                    engine = "Speech recognition unavailable on this device",
                ),
            )
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
            }
        }
        _listening.value = true
        restartListening()
    }

    private fun restartListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listenStartedAt = SystemClock.elapsedRealtime()
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            // ignore transient restart races
        }
    }

    override suspend fun stop() {
        shouldRestart = false
        _listening.value = false
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    override fun release() {
        shouldRestart = false
        _listening.value = false
        recognizer?.destroy()
        recognizer = null
    }
}

/**
 * Emits a scripted phrase for UI / pipeline testing without a microphone.
 */
class MockStreamingAsr : StreamingAsr {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _partials = MutableSharedFlow<AsrPartial>(extraBufferCapacity = 16)
    private val _listening = MutableStateFlow(false)

    override val partials: SharedFlow<AsrPartial> = _partials.asSharedFlow()
    override val isListening: StateFlow<Boolean> = _listening.asStateFlow()
    override val engineKind: AsrEngineKind = AsrEngineKind.MOCK
    override val engineLabel: String = "Mock ASR"

    private var job: kotlinx.coroutines.Job? = null

    override suspend fun start(language: AsrLanguage) {
        stop()
        _listening.value = true
        job = scope.launch {
            val phrase = when (language) {
                AsrLanguage.HINDI ->
                    listOf("नमस्ते", "नमस्ते आप", "नमस्ते आप कैसे", "नमस्ते आप कैसे हैं")
                AsrLanguage.TAMIL ->
                    listOf("வணக்கம்", "வணக்கம் நீங்கள்", "வணக்கம் நீங்கள் எப்படி")
                AsrLanguage.KANNADA ->
                    listOf("ನಮಸ್ಕಾರ", "ನಮಸ್ಕಾರ ನೀವು", "ನಮಸ್ಕಾರ ನೀವು ಹೇಗಿದ್ದೀರಿ")
                AsrLanguage.BENGALI ->
                    listOf("নমস্কার", "নমস্কার আপনি", "নমস্কার আপনি কেমন আছেন")
                AsrLanguage.ENGLISH, AsrLanguage.AUTO ->
                    listOf("hello", "hello how", "hello how are", "hello how are you")
            }
            val t0 = SystemClock.elapsedRealtime()
            for ((i, p) in phrase.withIndex()) {
                kotlinx.coroutines.delay(280)
                _partials.emit(
                    AsrPartial(
                        text = p,
                        isFinal = i == phrase.lastIndex,
                        latencyMs = SystemClock.elapsedRealtime() - t0,
                        engine = engineLabel,
                    ),
                )
            }
            _listening.value = false
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
        _listening.value = false
    }

    override fun release() {
        scope.launch { stop() }
    }
}
