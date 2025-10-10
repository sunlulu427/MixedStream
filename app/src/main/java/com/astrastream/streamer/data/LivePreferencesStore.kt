package com.astrastream.streamer.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.max
import kotlin.math.roundToInt
import com.astrastream.streamer.ui.live.EncoderOption
import com.astrastream.streamer.ui.live.LiveUiState
import com.astrastream.streamer.ui.live.ResolutionOption
import com.astrastream.streamer.ui.live.StreamUrlFormatter

class LivePreferencesStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(
        defaultState: LiveUiState,
        captureOptions: List<ResolutionOption>,
        streamOptions: List<ResolutionOption>,
        encoderOptions: List<EncoderOption>
    ): LiveUiState {
        val streamUrl = preferences.getString(KEY_STREAM_URL, defaultState.streamUrl) ?: ""

        val captureWidth = preferences.getInt(KEY_CAPTURE_WIDTH, defaultState.captureResolution.width)
        val captureHeight = preferences.getInt(KEY_CAPTURE_HEIGHT, defaultState.captureResolution.height)
        val captureResolution = findResolution(
            captureWidth,
            captureHeight,
            defaultState.captureResolution,
            captureOptions
        )

        val streamWidth = preferences.getInt(KEY_STREAM_WIDTH, defaultState.streamResolution.width)
        val streamHeight = preferences.getInt(KEY_STREAM_HEIGHT, defaultState.streamResolution.height)
        val streamResolution = findResolution(
            streamWidth,
            streamHeight,
            defaultState.streamResolution,
            streamOptions
        )

        val encoderLabel = preferences.getString(KEY_ENCODER_LABEL, defaultState.encoder.label)
        val encoderCodec = preferences.getString(KEY_ENCODER_CODEC, defaultState.encoder.videoCodec.name)
        val encoderHardware = preferences.getBoolean(KEY_ENCODER_HARDWARE, defaultState.encoder.useHardware)
        val encoder = findEncoder(
            encoderLabel,
            encoderCodec,
            encoderHardware,
            defaultState.encoder,
            encoderOptions
        )

        val storedBitrate = preferences.getInt(KEY_TARGET_BITRATE, defaultState.targetBitrate)
        val showPanel = preferences.getBoolean(KEY_SHOW_PANEL, defaultState.showParameterPanel)
        val showStats = preferences.getBoolean(KEY_SHOW_STATS, defaultState.showStats)

        val minBitrate = max(300, (storedBitrate * 0.7f).roundToInt())
        val maxBitrate = max(minBitrate + 200, (storedBitrate * 1.3f).roundToInt())
        val targetBitrate = storedBitrate.coerceIn(minBitrate, maxBitrate)

        return defaultState.copy(
            streamUrl = streamUrl,
            pullUrls = StreamUrlFormatter.buildPullUrls(streamUrl),
            captureResolution = captureResolution,
            streamResolution = streamResolution,
            encoder = encoder,
            targetBitrate = targetBitrate,
            minBitrate = minBitrate,
            maxBitrate = maxBitrate,
            showParameterPanel = showPanel,
            showStats = showStats
        )
    }

    fun save(state: LiveUiState) {
        preferences.edit().apply {
            putString(KEY_STREAM_URL, state.streamUrl)
            putInt(KEY_CAPTURE_WIDTH, state.captureResolution.width)
            putInt(KEY_CAPTURE_HEIGHT, state.captureResolution.height)
            putInt(KEY_STREAM_WIDTH, state.streamResolution.width)
            putInt(KEY_STREAM_HEIGHT, state.streamResolution.height)
            putString(KEY_ENCODER_LABEL, state.encoder.label)
            putString(KEY_ENCODER_CODEC, state.encoder.videoCodec.name)
            putBoolean(KEY_ENCODER_HARDWARE, state.encoder.useHardware)
            putInt(KEY_TARGET_BITRATE, state.targetBitrate)
            putBoolean(KEY_SHOW_PANEL, state.showParameterPanel)
            putBoolean(KEY_SHOW_STATS, state.showStats)
        }.apply()
    }

    private fun findResolution(
        width: Int,
        height: Int,
        fallback: ResolutionOption,
        options: List<ResolutionOption>
    ): ResolutionOption {
        if (width <= 0 || height <= 0) return fallback
        return options.firstOrNull { it.width == width && it.height == height }
            ?: ResolutionOption(width, height, "${width} × ${height}")
    }

    private fun findEncoder(
        label: String?,
        codecName: String?,
        useHardware: Boolean,
        fallback: EncoderOption,
        options: List<EncoderOption>
    ): EncoderOption {
        if (label.isNullOrBlank() || codecName.isNullOrBlank()) return fallback
        return options.firstOrNull {
            it.label == label && it.videoCodec.name == codecName && it.useHardware == useHardware
        } ?: fallback
    }

    companion object {
        private const val PREF_NAME = "live_session_preferences"
        private const val KEY_STREAM_URL = "stream_url"
        private const val KEY_CAPTURE_WIDTH = "capture_width"
        private const val KEY_CAPTURE_HEIGHT = "capture_height"
        private const val KEY_STREAM_WIDTH = "stream_width"
        private const val KEY_STREAM_HEIGHT = "stream_height"
        private const val KEY_ENCODER_LABEL = "encoder_label"
        private const val KEY_ENCODER_CODEC = "encoder_codec"
        private const val KEY_ENCODER_HARDWARE = "encoder_hardware"
        private const val KEY_TARGET_BITRATE = "target_bitrate"
        private const val KEY_SHOW_PANEL = "show_parameter_panel"
        private const val KEY_SHOW_STATS = "show_stats_overlay"
    }

}
