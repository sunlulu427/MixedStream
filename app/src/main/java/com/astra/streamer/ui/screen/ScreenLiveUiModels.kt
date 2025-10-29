package com.astra.streamer.ui.screen

data class ScreenLiveUiState(
    val streamUrl: String,
    val targetBitrate: Int = 1200,
    val includeMic: Boolean = true,
    val includePlayback: Boolean = true,
    val isStreaming: Boolean = false,
    val isConnecting: Boolean = false,
    val showStats: Boolean = true,
    val currentBitrate: Int = 0,
    val currentFps: Int = 0,
    val projectionReady: Boolean = false,
    val resolutionLabel: String = "",
    val overlayPermissionGranted: Boolean = false
)
