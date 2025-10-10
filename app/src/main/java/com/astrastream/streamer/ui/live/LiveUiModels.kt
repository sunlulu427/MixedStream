package com.astrastream.streamer.ui.live

import com.astrastream.avpush.domain.config.VideoConfiguration

data class ResolutionOption(val width: Int, val height: Int, val label: String)

data class EncoderOption(
    val label: String,
    val useHardware: Boolean,
    val videoCodec: VideoConfiguration.VideoCodec = VideoConfiguration.VideoCodec.H264,
    val description: String = ""
)

data class LiveUiState(
    val streamUrl: String,
    val isStreaming: Boolean = false,
    val isConnecting: Boolean = false,
    val showParameterPanel: Boolean = false,
    val showUrlDialog: Boolean = false,
    val showStats: Boolean = true,
    val pullUrls: List<String> = emptyList(),
    val captureResolution: ResolutionOption,
    val streamResolution: ResolutionOption,
    val encoder: EncoderOption,
    val targetBitrate: Int,
    val minBitrate: Int = 300,
    val maxBitrate: Int = 3500,
    val videoFps: Int = 30,
    val gop: Int = 5,
    val currentBitrate: Int = 0,
    val currentFps: Int = 30
)
