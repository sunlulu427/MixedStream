package com.astra.avpush.domain.config

data class ScreenCaptureConfiguration(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val fps: Int = 30,
    val includeMic: Boolean = true,
    val includePlayback: Boolean = true
)
