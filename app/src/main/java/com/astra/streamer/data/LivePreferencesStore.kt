package com.astra.streamer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.astra.streamer.ui.live.EncoderOption
import com.astra.streamer.ui.live.LiveUiState
import com.astra.streamer.ui.live.ResolutionOption
import com.astra.streamer.ui.live.StreamUrlFormatter
import kotlin.math.max
import kotlin.math.roundToInt

class LivePreferencesStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(
        defaultState: LiveUiState,
        captureOptions: List<ResolutionOption>,
        streamOptions: List<ResolutionOption>,
        encoderOptions: List<EncoderOption>
    ): LiveUiState {
        val streamUrl = preferences.getString(PreferenceKey.STREAM_URL.key, defaultState.streamUrl) ?: ""

        val captureWidth = preferences.getInt(PreferenceKey.CAPTURE_WIDTH.key, defaultState.captureResolution.width)
        val captureHeight = preferences.getInt(PreferenceKey.CAPTURE_HEIGHT.key, defaultState.captureResolution.height)
        val captureResolution = findResolution(
            captureWidth,
            captureHeight,
            defaultState.captureResolution,
            captureOptions
        )

        val streamWidth = preferences.getInt(PreferenceKey.STREAM_WIDTH.key, defaultState.streamResolution.width)
        val streamHeight = preferences.getInt(PreferenceKey.STREAM_HEIGHT.key, defaultState.streamResolution.height)
        val streamResolution = findResolution(
            streamWidth,
            streamHeight,
            defaultState.streamResolution,
            streamOptions
        )

        val encoderLabel = preferences.getString(PreferenceKey.ENCODER_LABEL.key, defaultState.encoder.label)
        val encoderCodec = preferences.getString(PreferenceKey.ENCODER_CODEC.key, defaultState.encoder.videoCodec.name)
        val encoderHardware = preferences.getBoolean(PreferenceKey.ENCODER_HARDWARE.key, defaultState.encoder.useHardware)
        val encoder = findEncoder(
            encoderLabel,
            encoderCodec,
            encoderHardware,
            defaultState.encoder,
            encoderOptions
        )

        val storedBitrate = preferences.getInt(PreferenceKey.TARGET_BITRATE.key, defaultState.targetBitrate)
        val showPanel = preferences.getBoolean(PreferenceKey.SHOW_PANEL.key, defaultState.showParameterPanel)
        val showStats = preferences.getBoolean(PreferenceKey.SHOW_STATS.key, defaultState.showStats)

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
        preferences.edit {
            putString(PreferenceKey.STREAM_URL.key, state.streamUrl)
            putInt(PreferenceKey.CAPTURE_WIDTH.key, state.captureResolution.width)
            putInt(PreferenceKey.CAPTURE_HEIGHT.key, state.captureResolution.height)
            putInt(PreferenceKey.STREAM_WIDTH.key, state.streamResolution.width)
            putInt(PreferenceKey.STREAM_HEIGHT.key, state.streamResolution.height)
            putString(PreferenceKey.ENCODER_LABEL.key, state.encoder.label)
            putString(PreferenceKey.ENCODER_CODEC.key, state.encoder.videoCodec.name)
            putBoolean(PreferenceKey.ENCODER_HARDWARE.key, state.encoder.useHardware)
            putInt(PreferenceKey.TARGET_BITRATE.key, state.targetBitrate)
            putBoolean(PreferenceKey.SHOW_PANEL.key, state.showParameterPanel)
            putBoolean(PreferenceKey.SHOW_STATS.key, state.showStats)
        }
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

        private enum class PreferenceKey(val key: String) {
            STREAM_URL("stream_url"),
            CAPTURE_WIDTH("capture_width"),
            CAPTURE_HEIGHT("capture_height"),
            STREAM_WIDTH("stream_width"),
            STREAM_HEIGHT("stream_height"),
            ENCODER_LABEL("encoder_label"),
            ENCODER_CODEC("encoder_codec"),
            ENCODER_HARDWARE("encoder_hardware"),
            TARGET_BITRATE("target_bitrate"),
            SHOW_PANEL("show_parameter_panel"),
            SHOW_STATS("show_stats_overlay")
        }
    }

}
