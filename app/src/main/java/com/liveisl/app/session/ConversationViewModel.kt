package com.liveisl.app.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liveisl.app.LiveIslApplication
import com.liveisl.app.asr.AsrLanguage
import com.liveisl.app.asr.AsrPartial
import com.liveisl.app.asr.StreamingAsr
import com.liveisl.app.asr.StreamingAsrFactory
import com.liveisl.app.data.Gloss
import com.liveisl.app.nlp.IslGlossPipeline
import com.liveisl.app.sign.AvatarCharacter
import com.liveisl.app.sign.CislrPackManager
import com.liveisl.app.sign.CislrPackStatus
import com.liveisl.app.sign.SignOutputMode
import com.liveisl.app.sign.SignPreferences
import com.liveisl.app.sign.SignRendererHub
import com.liveisl.app.sign.VideoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConversationRole {
    HEARING_SPEAKS,
    SIGNER_CAMERA,
}

data class LatencyStats(
    val lastAsrMs: Long = 0,
    val lastClipStartLagMs: Long = 0,
    val queueDepth: Int = 0,
    val glossMapMs: Long = 0,
)

data class ConversationUiState(
    val role: ConversationRole = ConversationRole.HEARING_SPEAKS,
    val language: AsrLanguage = AsrLanguage.ENGLISH,
    val isListening: Boolean = false,
    val partialText: String = "",
    val committedText: String = "",
    val glossStrip: List<String> = emptyList(),
    val currentGlossLabel: String? = null,
    val lastGlossLabel: String? = null,
    val isSigning: Boolean = false,
    val isShowingVideo: Boolean = false,
    val fallbackSignLabel: String? = null,
    val engineLabel: String = "",
    val bootstrapMessage: String = "",
    val showLatencyOverlay: Boolean = true,
    val showSettings: Boolean = false,
    val signOutputMode: SignOutputMode = SignOutputMode.VIDEO,
    val avatarCharacter: AvatarCharacter = AvatarCharacter.ARMATURE,
    val videoSource: VideoSource = VideoSource.VIKASOPS,
    val cislrStatus: CislrPackStatus = CislrPackStatus(),
    val cislrPackUrl: String = "",
    val latency: LatencyStats = LatencyStats(),
    val skippedWords: List<String> = emptyList(),
    val suggestedWords: List<String> = emptyList(),
    val error: String? = null,
)

class ConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LiveIslApplication
    private val pipeline = IslGlossPipeline(app.dictionary)
    private val preferences = SignPreferences(application)
    private val cislrPack = CislrPackManager(application)
    val signHub = SignRendererHub(application, viewModelScope, preferences)

    private var asr: StreamingAsr = StreamingAsrFactory.create(application)
    private var committedTokenCount = 0
    private var lastPartialWordCount = 0

    private val _ui = MutableStateFlow(
        ConversationUiState(
            engineLabel = asr.engineLabel,
            bootstrapMessage = app.modelBootstrap.status.message,
            suggestedWords = app.dictionary.suggestedSpokenWords(),
            signOutputMode = preferences.signOutputMode,
            avatarCharacter = preferences.avatarCharacter,
            videoSource = preferences.videoSource,
            cislrStatus = cislrPack.refreshStatus(),
            cislrPackUrl = preferences.cislrPackUrl,
        ),
    )
    val uiState: StateFlow<ConversationUiState> = _ui.asStateFlow()

    init {
        // Ensure dictionary matches persisted source
        app.dictionary.reload(preferences.videoSource)
        _ui.update {
            it.copy(
                suggestedWords = app.dictionary.suggestedSpokenWords(),
                videoSource = preferences.videoSource,
                cislrStatus = cislrPack.refreshStatus(),
            )
        }

        viewModelScope.launch {
            asr.partials.collect { onAsrPartial(it) }
        }
        viewModelScope.launch {
            asr.isListening.collect { listening ->
                _ui.update { it.copy(isListening = listening) }
            }
        }
        viewModelScope.launch {
            cislrPack.status.collect { st ->
                _ui.update { it.copy(cislrStatus = st) }
            }
        }
        viewModelScope.launch {
            signHub.currentGloss.collect { gloss ->
                _ui.update {
                    it.copy(
                        currentGlossLabel = gloss?.displayLabel,
                        lastGlossLabel = gloss?.displayLabel ?: it.lastGlossLabel,
                        isSigning = gloss != null,
                    )
                }
            }
        }
        viewModelScope.launch {
            signHub.isPlaying.collect { playing ->
                _ui.update { it.copy(isSigning = playing || it.currentGlossLabel != null) }
            }
        }
        viewModelScope.launch {
            signHub.fallbackLabel.collect { label ->
                _ui.update {
                    it.copy(
                        fallbackSignLabel = label,
                        lastGlossLabel = label ?: it.lastGlossLabel,
                    )
                }
            }
        }
        viewModelScope.launch {
            signHub.isShowingVideo.collect { showing ->
                _ui.update { it.copy(isShowingVideo = showing) }
            }
        }
        viewModelScope.launch {
            signHub.queueDepth.collect { depth ->
                _ui.update { s -> s.copy(latency = s.latency.copy(queueDepth = depth)) }
            }
        }
        viewModelScope.launch {
            signHub.lastClipStartLagMs.collect { lag ->
                _ui.update { s -> s.copy(latency = s.latency.copy(lastClipStartLagMs = lag)) }
            }
        }
        viewModelScope.launch {
            signHub.mode.collect { mode ->
                _ui.update { it.copy(signOutputMode = mode) }
            }
        }
    }

    fun toggleLatencyOverlay() {
        _ui.update { it.copy(showLatencyOverlay = !it.showLatencyOverlay) }
    }

    fun openSettings() {
        _ui.update { it.copy(showSettings = true) }
    }

    fun dismissSettings() {
        _ui.update { it.copy(showSettings = false) }
    }

    fun setSignOutputMode(mode: SignOutputMode) {
        signHub.setMode(mode)
        _ui.update {
            it.copy(
                signOutputMode = mode,
                bootstrapMessage = when (mode) {
                    SignOutputMode.VIDEO -> "Using video ISL clips"
                    SignOutputMode.AVATAR_3D -> "Using 3D avatar signing"
                },
            )
        }
    }

    fun setAvatarCharacter(character: AvatarCharacter) {
        preferences.avatarCharacter = character
        _ui.update {
            it.copy(
                avatarCharacter = character,
                bootstrapMessage = when (character) {
                    AvatarCharacter.ARMATURE -> "3D character: armature"
                    AvatarCharacter.CONSTRUCTION_WORKER -> "3D character: construction worker"
                },
            )
        }
    }

    fun setVideoSource(source: VideoSource) {
        preferences.videoSource = source
        app.dictionary.reload(source)
        signHub.interrupt()
        _ui.update {
            it.copy(
                videoSource = source,
                suggestedWords = app.dictionary.suggestedSpokenWords(),
                glossStrip = emptyList(),
                currentGlossLabel = null,
                bootstrapMessage = when (source) {
                    VideoSource.VIKASOPS -> "Video source: Vikasops (${app.dictionary.size()} signs)"
                    VideoSource.CISLR -> {
                        val st = cislrPack.refreshStatus()
                        if (st.installed) {
                            "Video source: CISLR (${st.videoCount} clips)"
                        } else {
                            "CISLR selected — install pack in Settings"
                        }
                    }
                },
                cislrStatus = cislrPack.refreshStatus(),
            )
        }
    }

    fun setCislrPackUrl(url: String) {
        preferences.cislrPackUrl = url
        _ui.update { it.copy(cislrPackUrl = url) }
    }

    fun refreshCislrStatus() {
        _ui.update { it.copy(cislrStatus = cislrPack.refreshStatus()) }
    }

    fun downloadCislrPack() {
        val url = preferences.resolvedCislrPackUrl()
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    cislrStatus = it.cislrStatus.copy(downloading = true, message = "Downloading…"),
                    bootstrapMessage = "Downloading CISLR pack…",
                )
            }
            val result = cislrPack.downloadAndInstall(url)
            result.onSuccess { status ->
                app.dictionary.reload(VideoSource.CISLR)
                preferences.videoSource = VideoSource.CISLR
                _ui.update {
                    it.copy(
                        videoSource = VideoSource.CISLR,
                        cislrStatus = status,
                        suggestedWords = app.dictionary.suggestedSpokenWords(),
                        bootstrapMessage = "CISLR installed — ${status.videoCount} clips",
                    )
                }
            }.onFailure { e ->
                _ui.update {
                    it.copy(
                        cislrStatus = cislrPack.refreshStatus().copy(
                            downloading = false,
                            message = e.message ?: "Download failed",
                        ),
                        bootstrapMessage = e.message ?: "CISLR download failed",
                    )
                }
            }
        }
    }

    fun setLanguage(language: AsrLanguage) {
        _ui.update { it.copy(language = language) }
    }

    fun setRole(role: ConversationRole) {
        if (role == ConversationRole.SIGNER_CAMERA) {
            viewModelScope.launch { stopListening() }
        }
        _ui.update { it.copy(role = role) }
    }

    fun useMockAsrDemo() {
        viewModelScope.launch {
            try {
                asr.stop()
            } catch (_: Exception) {
            }
            clearSession()
            val phrase = app.dictionary.demoPhraseWords()
            if (phrase.isEmpty()) {
                _ui.update {
                    it.copy(
                        engineLabel = "Demo mode",
                        bootstrapMessage = when (_ui.value.videoSource) {
                            VideoSource.CISLR ->
                                "CISLR pack has no playable words yet — install/refresh pack in Settings"
                            VideoSource.VIKASOPS ->
                                "No demo videos available"
                        },
                    )
                }
                return@launch
            }
            val spoken = phrase.joinToString(" ")
            val mode = _ui.value.signOutputMode
            _ui.update {
                it.copy(
                    engineLabel = "Demo mode",
                    bootstrapMessage = when (mode) {
                        SignOutputMode.VIDEO -> "Playing ISL videos: $spoken"
                        SignOutputMode.AVATAR_3D -> "Animating 3D signer: $spoken"
                    },
                    isListening = false,
                    partialText = spoken,
                    committedText = spoken,
                    skippedWords = emptyList(),
                )
            }

            var played = 0
            for (word in phrase) {
                val requireVideo = mode == SignOutputMode.VIDEO
                val glosses = pipeline.processCommittedWords(listOf(word), requireVideo)
                if (glosses.isEmpty()) continue
                played += glosses.size
                val labels = glosses.map { it.displayLabel }
                _ui.update { s ->
                    s.copy(
                        glossStrip = (s.glossStrip + labels).takeLast(24),
                        currentGlossLabel = labels.first(),
                        lastGlossLabel = labels.first(),
                        isSigning = true,
                        partialText = "",
                        fallbackSignLabel = null,
                    )
                }
                signHub.enqueue(glosses)
                kotlinx.coroutines.delay(if (mode == SignOutputMode.VIDEO) 1600 else 1200)
            }

            _ui.update {
                it.copy(
                    isSigning = false,
                    bootstrapMessage = if (played == 0) {
                        "No matching clips for: $spoken"
                    } else {
                        "Demo finished — speak or tap Demo again"
                    },
                )
            }
        }
    }

    fun startListening() {
        committedTokenCount = 0
        lastPartialWordCount = 0
        viewModelScope.launch {
            try {
                asr.start(_ui.value.language)
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }

    fun stopListening() {
        viewModelScope.launch {
            asr.stop()
            val pending = _ui.value.partialText
            if (pending.isNotBlank()) {
                commitText(pending, asrLatencyMs = 0)
            }
        }
    }

    fun clearSession() {
        signHub.interrupt()
        committedTokenCount = 0
        lastPartialWordCount = 0
        _ui.update {
            it.copy(
                partialText = "",
                committedText = "",
                glossStrip = emptyList(),
                currentGlossLabel = null,
                lastGlossLabel = null,
                isSigning = false,
                isShowingVideo = false,
                fallbackSignLabel = null,
                error = null,
                skippedWords = emptyList(),
            )
        }
    }

    private fun onAsrPartial(partial: AsrPartial) {
        _ui.update {
            it.copy(
                partialText = partial.text,
                latency = it.latency.copy(lastAsrMs = partial.latencyMs),
                engineLabel = partial.engine.ifBlank { asr.engineLabel },
            )
        }
        if (partial.isFinal) {
            commitText(partial.text, partial.latencyMs)
        } else {
            val words = partial.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size > 1) {
                val stable = words.dropLast(1)
                if (stable.size > lastPartialWordCount) {
                    val newly = stable.drop(lastPartialWordCount)
                    lastPartialWordCount = stable.size
                    mapAndEnqueue(newly)
                }
            }
        }
    }

    private fun commitText(text: String, asrLatencyMs: Long) {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val remaining = if (words.size > lastPartialWordCount) {
            words.drop(lastPartialWordCount)
        } else {
            words
        }
        lastPartialWordCount = 0
        committedTokenCount += words.size
        _ui.update {
            it.copy(
                committedText = listOf(it.committedText, text).filter { s -> s.isNotBlank() }
                    .joinToString(" "),
                partialText = "",
                latency = it.latency.copy(lastAsrMs = asrLatencyMs),
            )
        }
        mapAndEnqueue(remaining)
    }

    private fun mapAndEnqueue(words: List<String>) {
        if (words.isEmpty()) return
        val requireVideo = _ui.value.signOutputMode == SignOutputMode.VIDEO
        val t0 = android.os.SystemClock.elapsedRealtime()
        val glosses: List<Gloss> = pipeline.processCommittedWords(words, requireVideo)
        val mapMs = android.os.SystemClock.elapsedRealtime() - t0
        val skipped = pipeline.lastSkippedWords
        _ui.update { s ->
            s.copy(
                skippedWords = skipped,
                latency = s.latency.copy(glossMapMs = mapMs),
                bootstrapMessage = when {
                    requireVideo && glosses.isEmpty() && skipped.isNotEmpty() ->
                        "No video yet for: ${skipped.joinToString(", ")} — try chips below or switch to 3D avatar"
                    requireVideo && skipped.isNotEmpty() ->
                        "Skipped (no clip): ${skipped.joinToString(", ")}"
                    else -> s.bootstrapMessage
                },
            )
        }
        if (glosses.isEmpty()) return
        val labels = glosses.map { it.displayLabel }
        _ui.update { s ->
            s.copy(
                glossStrip = (s.glossStrip + labels).takeLast(24),
                currentGlossLabel = labels.first(),
                lastGlossLabel = labels.first(),
                isSigning = true,
            )
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            signHub.enqueue(glosses)
        }
    }

    fun playSuggestedWord(word: String) {
        viewModelScope.launch {
            mapAndEnqueue(listOf(word))
            _ui.update {
                it.copy(
                    committedText = listOf(it.committedText, word)
                        .filter { s -> s.isNotBlank() }
                        .joinToString(" "),
                )
            }
        }
    }

    override fun onCleared() {
        asr.release()
        signHub.release()
        super.onCleared()
    }
}
