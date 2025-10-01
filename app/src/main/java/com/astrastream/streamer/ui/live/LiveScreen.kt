package com.astrastream.streamer.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.astrastream.avpush.presentation.widget.AVLiveView

@Composable
fun LiveScreen(
    state: LiveUiState,
    captureOptions: List<ResolutionOption>,
    streamOptions: List<ResolutionOption>,
    encoderOptions: List<EncoderOption>,
    onCaptureResolutionSelected: (ResolutionOption) -> Unit,
    onStreamResolutionSelected: (ResolutionOption) -> Unit,
    onEncoderSelected: (EncoderOption) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onBitrateInput: (String) -> Unit,
    onStreamUrlChanged: (String) -> Unit,
    onTogglePanel: () -> Unit,
    onShowUrlDialog: () -> Unit,
    onDismissUrlDialog: () -> Unit,
    onConfirmUrl: (String) -> Unit,
    onStatsToggle: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleLive: () -> Unit,
    onLiveViewReady: (AVLiveView) -> Unit
) {
    val hasUrl = state.streamUrl.isNotBlank()
    val cameraEnabled = !state.isConnecting
    val parameterEnabled = !state.isStreaming && !state.isConnecting

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!state.showParameterPanel) {
                val fabEnabled = !state.isConnecting
                val fabColor = when {
                    state.isStreaming -> MaterialTheme.colorScheme.error
                    hasUrl -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!fabEnabled) return@ExtendedFloatingActionButton
                        if (state.isStreaming) {
                            onToggleLive()
                        } else if (!hasUrl) {
                            onShowUrlDialog()
                        } else {
                            onToggleLive()
                        }
                    },
                    containerColor = fabColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    icon = {
                        if (state.isStreaming) {
                            Icon(imageVector = Icons.Rounded.Stop, contentDescription = "停止推流")
                        } else {
                            Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = "开始推流")
                        }
                    },
                    text = {
                        Text(
                            text = when {
                                state.isStreaming -> "结束直播"
                                state.isConnecting -> "连接中..."
                                state.streamUrl.isBlank() -> "填写推流地址"
                                else -> "开始直播"
                            }
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LivePreview(onLiveViewReady = onLiveViewReady, modifier = Modifier.fillMaxSize())

            CameraSwitchButton(
                onClick = onSwitchCamera,
                enabled = cameraEnabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            )

            if (state.showStats && !state.showParameterPanel) {
                StreamingStatsOverlay(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 20.dp, start = 20.dp)
                        .zIndex(1f)
                )
            }

            PanelToggleButton(
                expanded = state.showParameterPanel,
                onToggle = onTogglePanel,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )

            AnimatedVisibility(
                visible = state.isConnecting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
            }

            AnimatedVisibility(
                visible = state.showParameterPanel,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
            ) {
                ParameterPanelOverlay(
                    state = state,
                    captureOptions = captureOptions,
                    streamOptions = streamOptions,
                    encoderOptions = encoderOptions,
                    onCaptureResolutionSelected = onCaptureResolutionSelected,
                    onStreamResolutionSelected = onStreamResolutionSelected,
                    onEncoderSelected = onEncoderSelected,
                    onBitrateChanged = onBitrateChanged,
                    onBitrateInput = onBitrateInput,
                    onStreamUrlChanged = onStreamUrlChanged,
                    onStatsToggle = onStatsToggle,
                    onDismiss = onTogglePanel,
                    controlsEnabled = parameterEnabled
                )
            }
        }
    }

    if (state.showUrlDialog) {
        StreamUrlDialog(
            initialValue = state.streamUrl,
            onDismiss = onDismissUrlDialog,
            onConfirm = onConfirmUrl
        )
    }
}

@Composable
private fun LivePreview(onLiveViewReady: (AVLiveView) -> Unit, modifier: Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context -> AVLiveView(context).also(onLiveViewReady) },
        modifier = modifier
    )
}
