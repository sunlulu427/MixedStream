package com.astrastream.streamer.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ScreenLiveScreen(
    state: ScreenLiveUiState,
    onStreamUrlChanged: (String) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onToggleMic: (Boolean) -> Unit,
    onTogglePlayback: (Boolean) -> Unit,
    onToggleStats: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Screen Live Streaming", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val text = "Bitrate: ${state.currentBitrate} kbps\nFPS: ${state.currentFps}"
                    clipboard.setText(AnnotatedString(text))
                }) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy stats")
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Projection", style = MaterialTheme.typography.titleMedium)
                    Text(text = if (state.projectionReady) "Ready (${state.resolutionLabel})" else "Waiting for permission")
                }
            }

            OutlinedTextField(
                value = state.streamUrl,
                onValueChange = onStreamUrlChanged,
                label = { Text(text = "Publish URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Target Bitrate: ${state.targetBitrate} kbps", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = state.targetBitrate.toFloat(),
                    onValueChange = { onBitrateChanged(it.toInt()) },
                    valueRange = 500f..6000f,
                    steps = 10
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Include Microphone", modifier = Modifier.weight(1f))
                Switch(checked = state.includeMic, onCheckedChange = onToggleMic)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Capture Game Audio", modifier = Modifier.weight(1f))
                Switch(checked = state.includePlayback, onCheckedChange = onTogglePlayback)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Show Stats", modifier = Modifier.weight(1f))
                Switch(checked = state.showStats, onCheckedChange = onToggleStats)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Floating Overlay", modifier = Modifier.weight(1f))
                if (state.overlayPermissionGranted) {
                    Text(text = "Enabled", color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = onRequestOverlay) {
                        Text(text = "Grant")
                    }
                }
            }

            AnimatedVisibility(
                visible = state.showStats,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Live Metrics", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Bitrate: ${state.currentBitrate} kbps")
                        Text(text = "FPS: ${state.currentFps}")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.isStreaming) {
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Stop Broadcasting")
                    }
                } else {
            Button(
                onClick = onStart,
                enabled = !state.isConnecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (state.isConnecting) "Connecting..." else "Start Broadcasting")
            }
        }
    }
}
    }
}
