package com.liveisl.app.ui.conversation

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.liveisl.app.asr.AsrLanguage
import com.liveisl.app.session.ConversationRole
import com.liveisl.app.session.ConversationViewModel
import com.liveisl.app.session.LatencyStats
import com.liveisl.app.sign.AvatarCharacter
import com.liveisl.app.sign.SignOutputMode
import com.liveisl.app.sign.SignPreferences
import com.liveisl.app.ui.avatar.Avatar3DStage
import com.liveisl.app.ui.avatar.GlbAvatarStage
import com.liveisl.app.ui.phase2.Phase2CameraStub
import com.liveisl.app.ui.settings.SettingsSheet

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConversationScreen(
    vm: ConversationViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val micPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    if (state.showSettings) {
        SettingsSheet(
            currentMode = state.signOutputMode,
            currentCharacter = state.avatarCharacter,
            currentVideoSource = state.videoSource,
            cislrStatus = state.cislrStatus,
            cislrPackUrl = state.cislrPackUrl,
            playbackSpeed = state.playbackSpeed,
            showGlossCards = state.showGlossCards,
            onModeSelected = vm::setSignOutputMode,
            onCharacterSelected = vm::setAvatarCharacter,
            onVideoSourceSelected = vm::setVideoSource,
            onCislrPackUrlChange = vm::setCislrPackUrl,
            onDownloadCislr = vm::downloadCislrPack,
            onRefreshCislr = vm::refreshCislrStatus,
            onPlaybackSpeedSelected = vm::setPlaybackSpeed,
            onShowGlossCardsChange = vm::setShowGlossCards,
            onDismiss = vm::dismissSettings,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1F2A), Color(0xFF0F2F3F), Color(0xFF0B1F2A)),
                ),
            )
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        HeaderRow(
            onOpenSettings = vm::openSettings,
            onToggleLatency = vm::toggleLatencyOverlay,
            onClear = vm::clearSession,
            onDemo = vm::useMockAsrDemo,
        )

        Spacer(modifier = Modifier.height(8.dp))

        RoleAndLanguageRow(
            role = state.role,
            language = state.language,
            signMode = state.signOutputMode,
            onRole = vm::setRole,
            onLanguage = vm::setLanguage,
            onOpenSettings = vm::openSettings,
        )

        if (state.showLatencyOverlay) {
            Spacer(modifier = Modifier.height(8.dp))
            LatencyBar(latency = state.latency)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.role == ConversationRole.SIGNER_CAMERA) {
            Phase2CameraStub(modifier = Modifier.weight(1f))
        } else {
            SignStage(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                vm = vm,
                mode = state.signOutputMode,
                character = state.avatarCharacter,
                displayLabel = state.currentGlossLabel ?: state.lastGlossLabel,
                isSigning = state.isSigning || state.isShowingVideo,
                isShowingVideo = state.isShowingVideo,
                missingVideoLabel = state.fallbackSignLabel,
                glossStrip = state.glossStrip,
                playbackSpeed = state.playbackSpeed,
                onPlaybackSpeedCycle = {
                    val options = SignPreferences.PLAYBACK_SPEED_OPTIONS
                    val idx = options.indexOfFirst { kotlin.math.abs(it - state.playbackSpeed) < 0.01f }
                    val next = options[(idx.coerceAtLeast(0) + 1) % options.size]
                    vm.setPlaybackSpeed(next)
                },
            )
        }

        if (state.suggestedWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (state.signOutputMode == SignOutputMode.VIDEO) {
                    "Words with videos — tap to play"
                } else {
                    "Try a word — 3D avatar will sign"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.suggestedWords) { word ->
                    AssistChip(
                        onClick = { vm.playSuggestedWord(word) },
                        label = { Text(word) },
                        colors = chipColors(selected = false),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TranscriptPane(
            partial = state.partialText,
            committed = state.committedText,
            englishTranscript = state.englishTranscript,
            detectedLanguage = state.detectedLanguageLabel,
            engine = state.engineLabel,
            bootstrap = state.bootstrapMessage,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (state.role == ConversationRole.HEARING_SPEAKS) {
            MicControls(
                isListening = state.isListening,
                micGranted = micPermission.status.isGranted,
                onRequestMic = { micPermission.launchPermissionRequest() },
                onToggle = {
                    if (!micPermission.status.isGranted) {
                        micPermission.launchPermissionRequest()
                    } else if (state.isListening) {
                        vm.stopListening()
                    } else {
                        vm.startListening()
                    }
                },
            )
        }
    }
}

@Composable
private fun HeaderRow(
    onOpenSettings: () -> Unit,
    onToggleLatency: () -> Unit,
    onClear: () -> Unit,
    onDemo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Live ISL",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
            )
            Text(
                text = "On-device voice to Indian Sign Language",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onToggleLatency) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "Latency overlay",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDemo) {
            Text(text = "Demo", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun RoleAndLanguageRow(
    role: ConversationRole,
    language: AsrLanguage,
    signMode: SignOutputMode,
    onRole: (ConversationRole) -> Unit,
    onLanguage: (AsrLanguage) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = { onRole(ConversationRole.HEARING_SPEAKS) },
            label = { Text("You speak") },
            colors = chipColors(selected = role == ConversationRole.HEARING_SPEAKS),
            leadingIcon = {
                Icon(Icons.Default.Mic, null, Modifier.size(16.dp))
            },
        )
        AssistChip(
            onClick = { onRole(ConversationRole.SIGNER_CAMERA) },
            label = { Text("Camera ISL") },
            colors = chipColors(selected = role == ConversationRole.SIGNER_CAMERA),
            leadingIcon = {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(16.dp))
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        AssistChip(
            onClick = onOpenSettings,
            label = {
                Text(
                    when (signMode) {
                        SignOutputMode.VIDEO -> "Video"
                        SignOutputMode.AVATAR_3D -> "3D"
                    },
                )
            },
            colors = chipColors(selected = true),
        )
        AssistChip(
            onClick = { onLanguage(language.next()) },
            label = { Text(language.displayName) },
            colors = chipColors(selected = true),
        )
    }
}

@Composable
private fun chipColors(selected: Boolean) = AssistChipDefaults.assistChipColors(
    containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface
    },
    labelColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun LatencyBar(latency: LatencyStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x66143447))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("ASR ${latency.lastAsrMs}ms", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("gloss ${latency.glossMapMs}ms", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("clip ${latency.lastClipStartLagMs}ms", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("queue ${latency.queueDepth}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SignStage(
    modifier: Modifier,
    vm: ConversationViewModel,
    mode: SignOutputMode,
    character: AvatarCharacter,
    displayLabel: String?,
    isSigning: Boolean,
    isShowingVideo: Boolean,
    missingVideoLabel: String?,
    glossStrip: List<String>,
    playbackSpeed: Float,
    onPlaybackSpeedCycle: () -> Unit,
) {
    Column(modifier = modifier) {
        when (mode) {
            SignOutputMode.AVATAR_3D -> {
                val stageModifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                when (character) {
                    AvatarCharacter.ARMATURE -> Avatar3DStage(
                        renderer = vm.signHub.avatarRenderer,
                        glossLabel = displayLabel,
                        modifier = stageModifier,
                    )
                    AvatarCharacter.CONSTRUCTION_WORKER -> GlbAvatarStage(
                        renderer = vm.signHub.avatarRenderer,
                        glossLabel = displayLabel,
                        modifier = stageModifier,
                    )
                }
            }
            SignOutputMode.VIDEO -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val player = remember { vm.signHub.exoPlayer() }
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                this.player = player
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize(),
                    )

                    AssistChip(
                        onClick = onPlaybackSpeedCycle,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        label = {
                            Text(
                                if (playbackSpeed == 1f) "1×" else "${playbackSpeed}×",
                            )
                        },
                        colors = chipColors(selected = true),
                        leadingIcon = {
                            Icon(Icons.Default.Speed, null, Modifier.size(16.dp))
                        },
                    )

                    if (!isShowingVideo && missingVideoLabel != null) {
                        Text(
                            text = missingVideoLabel,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xEE143447))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 28.dp, vertical = 18.dp),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (!isSigning && displayLabel == null && missingVideoLabel == null) {
                        Text(
                            text = "Speak or tap Demo\nto see ISL signing videos",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF9BB4C4),
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (isShowingVideo && displayLabel != null) {
                        Text(
                            text = displayLabel,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xAA0B1F2A))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (glossStrip.isEmpty()) {
            Text("Gloss strip fills as speech is translated", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(glossStrip) { g ->
                    val isTextCard = g.startsWith("▭ ")
                    Text(
                        text = if (isTextCard) g.removePrefix("▭ ") else g,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isTextCard) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .border(
                                width = if (isTextCard) 1.dp else 0.dp,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isTextCard) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptPane(
    partial: String,
    committed: String,
    englishTranscript: String,
    detectedLanguage: String,
    engine: String,
    bootstrap: String,
) {
    val listState = rememberLazyListState()
    val lines = buildList {
        if (committed.isNotBlank()) add(committed)
        if (englishTranscript.isNotBlank() && englishTranscript != committed) {
            add("→ $englishTranscript")
        }
        if (partial.isNotBlank()) add(partial)
    }
    LaunchedEffect(lines.size, lines.lastOrNull(), bootstrap) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Transcript", style = MaterialTheme.typography.labelLarge)
            if (detectedLanguage.isNotBlank()) {
                Text(
                    text = detectedLanguage,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (lines.isEmpty()) {
                item {
                    Text(text = "-", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                items(lines.size) { idx ->
                    val line = lines[idx]
                    val isPartial = partial.isNotBlank() && idx == lines.lastIndex && line == partial
                    val isEnglish = line.startsWith("→ ")
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            isPartial -> MaterialTheme.colorScheme.secondary
                            isEnglish -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = engine,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Text(
            text = bootstrap,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
    }
}

@Composable
private fun MicControls(
    isListening: Boolean,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!micGranted) {
            TextButton(onClick = onRequestMic) { Text("Grant microphone") }
            Spacer(modifier = Modifier.width(12.dp))
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = if (isListening) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Start",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
