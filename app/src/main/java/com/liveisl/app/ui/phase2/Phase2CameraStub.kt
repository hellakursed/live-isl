package com.liveisl.app.ui.phase2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liveisl.app.phase2.IslLandmarkStub

/**
 * Phase 2 UI stub without CameraX/MediaPipe native libs (16 KB page-size safe).
 */
@Composable
fun Phase2CameraStub(modifier: Modifier = Modifier) {
    val stub = remember { IslLandmarkStub() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF143447))
            .padding(12.dp),
    ) {
        Text("ISL to Voice (Phase 2)", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Camera + MediaPipe landmarks -> LiteRT classifier -> on-device TTS",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1F2A)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Camera preview reserved for Phase 2\n(MediaPipe not linked yet)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(text = stub.describe(), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Recognition model not trained yet — architecture reserved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )

        DisposableEffect(Unit) {
            onDispose { stub.close() }
        }
    }
}
