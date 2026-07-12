package com.liveisl.app.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
 * Continuous Android SpeechRecognizer with:
 * - 1.8s minimum interval between starts (debounce)
 * - Longer complete/possible silence timeouts
 * - cancel() before each restart to clear busy sessions
 */
class AndroidSpeechStreamingAsr(
    private val context: Context,
) : StreamingAsr {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
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
    private var isStarting = false
    private var lastStartAt = 0L
    private var consecutiveNoMatch = 0
    private var preferOffline = true

    private val restartRunnable = Runnable {
        if (shouldRestart && _listening.value) {
            startListeningInternal()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isStarting = false
        }

        override fun onBeginningOfSpeech() {
            listenStartedAt = SystemClock.elapsedRealtime()
            consecutiveNoMatch = 0
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            isStarting = false
            Log.w(TAG, "ASR error=${errorName(error)} ($error)")
            when (error) {
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                -> {
                    scheduleRestart(delayMs = RESTART_BUSY_MS)
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> {
                    consecutiveNoMatch++
                    val delay = if (consecutiveNoMatch >= 3) RESTART_SILENCE_LONG_MS else RESTART_SILENCE_MS
                    scheduleRestart(delayMs = delay)
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                -> {
                    preferOffline = false
                    scheduleRestart(delayMs = RESTART_NETWORK_MS)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                SpeechRecognizer.ERROR_SERVER,
                -> {
                    shouldRestart = false
                    _listening.value = false
                    scope.launch {
                        _partials.emit(
                            AsrPartial(
                                text = "",
                                isFinal = true,
                                latencyMs = 0,
                                engine = "Speech error: ${errorName(error)}",
                            ),
                        )
                    }
                }
                else -> scheduleRestart(delayMs = RESTART_DEFAULT_MS)
            }
        }

        override fun onResults(results: Bundle?) {
            isStarting = false
            consecutiveNoMatch = 0
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
            scheduleRestart(delayMs = RESTART_AFTER_RESULT_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = texts?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                consecutiveNoMatch = 0
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
        consecutiveNoMatch = 0
        // English usually has an offline pack; Indic packs are often missing.
        preferOffline = this.language == AsrLanguage.ENGLISH
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _listening.value = false
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
        ensureRecognizer()
        _listening.value = true
        startListeningInternal()
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartRunnable)
        if (!shouldRestart || !_listening.value) return
        mainHandler.postDelayed(restartRunnable, delayMs)
    }

    private fun startListeningInternal() {
        if (!shouldRestart || !_listening.value) return
        if (isStarting) return

        val now = SystemClock.elapsedRealtime()
        val sinceLast = now - lastStartAt
        if (sinceLast < MIN_START_INTERVAL_MS) {
            scheduleRestart(MIN_START_INTERVAL_MS - sinceLast)
            return
        }

        isStarting = true
        lastStartAt = now
        listenStartedAt = now

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Keep the session open longer so silence doesn't end every ~2s.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }

        try {
            // Cancel any in-flight session before starting to avoid ERROR_RECOGNIZER_BUSY loops.
            recognizer?.cancel()
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            isStarting = false
            scheduleRestart(RESTART_DEFAULT_MS)
        }
    }

    override suspend fun stop() {
        shouldRestart = false
        _listening.value = false
        isStarting = false
        mainHandler.removeCallbacks(restartRunnable)
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    override fun release() {
        shouldRestart = false
        _listening.value = false
        isStarting = false
        mainHandler.removeCallbacks(restartRunnable)
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        private const val TAG = "AndroidSpeechAsr"
        private const val MIN_START_INTERVAL_MS = 1800L
        private const val RESTART_AFTER_RESULT_MS = 600L
        private const val RESTART_SILENCE_MS = 1600L
        private const val RESTART_SILENCE_LONG_MS = 2800L
        private const val RESTART_BUSY_MS = 1200L
        private const val RESTART_NETWORK_MS = 2000L
        private const val RESTART_DEFAULT_MS = 1500L

        private fun errorName(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_SERVER -> "SERVER"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "SERVER_DISCONNECTED"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "LANGUAGE_NOT_SUPPORTED"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "LANGUAGE_UNAVAILABLE"
            else -> "UNKNOWN"
        }
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
