package com.liveisl.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.liveisl.app.sign.AvatarCharacter
import com.liveisl.app.sign.CislrPackDefaults
import com.liveisl.app.sign.CislrPackStatus
import com.liveisl.app.sign.SignOutputMode
import com.liveisl.app.sign.SignPreferences
import com.liveisl.app.sign.VideoSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    currentMode: SignOutputMode,
    currentCharacter: AvatarCharacter,
    currentVideoSource: VideoSource,
    cislrStatus: CislrPackStatus,
    cislrPackUrl: String,
    playbackSpeed: Float,
    onModeSelected: (SignOutputMode) -> Unit,
    onCharacterSelected: (AvatarCharacter) -> Unit,
    onVideoSourceSelected: (VideoSource) -> Unit,
    onCislrPackUrlChange: (String) -> Unit,
    onDownloadCislr: () -> Unit,
    onRefreshCislr: () -> Unit,
    onPlaybackSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose how ISL signs are shown",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("Sign output")
            SignOutputMode.entries.forEach { mode ->
                SettingsRadioRow(
                    selected = mode == currentMode,
                    title = mode.label,
                    subtitle = mode.description,
                    onClick = { onModeSelected(mode) },
                )
            }

            if (currentMode == SignOutputMode.VIDEO) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("Playback speed")
                Text(
                    text = "Slow down or speed up ISL video clips",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SignPreferences.PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                    val label = if (speed == 1f) "1× (normal)" else "${speed}×"
                    SettingsRadioRow(
                        selected = kotlin.math.abs(playbackSpeed - speed) < 0.01f,
                        title = label,
                        subtitle = when {
                            speed < 1f -> "Slower — easier to follow"
                            speed > 1f -> "Faster signing"
                            else -> "Default pace"
                        },
                        onClick = { onPlaybackSpeedSelected(speed) },
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("Video source")
                Text(
                    text = "Switch the ISL clip corpus used for playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                VideoSource.entries.forEach { source ->
                    SettingsRadioRow(
                        selected = source == currentVideoSource,
                        title = source.label,
                        subtitle = source.description,
                        onClick = { onVideoSourceSelected(source) },
                    )
                }

                if (currentVideoSource == VideoSource.CISLR) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = cislrStatus.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (cislrStatus.installed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cislrPackUrl,
                        onValueChange = onCislrPackUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Pack zip URL override (optional)") },
                        placeholder = { Text(CislrPackDefaults.PACK_ZIP_URL) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (cislrStatus.downloading) {
                        LinearProgressIndicator(
                            progress = { cislrStatus.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onDownloadCislr,
                            enabled = !cislrStatus.downloading,
                        ) {
                            Text(if (cislrStatus.downloading) "Installing…" else "Download pack")
                        }
                        TextButton(onClick = onRefreshCislr) {
                            Text("Refresh status")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (cislrStatus.installed) {
                            "Pack is on this device. Re-download only if you need a fresh copy (~800 MB)."
                        } else {
                            "Tap Download pack to fetch the hosted CISLR zip (~800 MB / ~4.7k words), " +
                                "extract it on-device, then delete the zip automatically."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (currentMode == SignOutputMode.AVATAR_3D) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("3D character")
                Text(
                    text = "Armature stays available; switch to the rigged worker anytime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AvatarCharacter.entries.forEach { character ->
                    SettingsRadioRow(
                        selected = character == currentCharacter,
                        title = character.label,
                        subtitle = character.description,
                        onClick = { onCharacterSelected(character) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsRadioRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
