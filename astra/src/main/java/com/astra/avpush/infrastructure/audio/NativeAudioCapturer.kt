package com.astra.avpush.infrastructure.audio

import android.media.AudioFormat
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.runtime.AstraLog

internal class NativeAudioCapturer {

    companion object {
        init {
            System.loadLibrary("astra")
        }
        private const val TAG = "NativeAudioCapturer"
    }

    fun configure(configuration: AudioConfiguration) {
        val bytesPerSample = when (configuration.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val ok = nativeConfigure(
            configuration.sampleRate,
            configuration.channelCount,
            bytesPerSample
        )
        if (!ok) {
            throw IllegalStateException("Native audio configure failed")
        }
    }

    fun start() {
        if (!nativeStart()) {
            AstraLog.e(TAG, "native audio start failed")
        }
    }

    fun stop() = nativeStop()

    fun release() = nativeRelease()

    fun setMute(muted: Boolean) = nativeSetMute(muted)

    private external fun nativeConfigure(sampleRate: Int, channels: Int, bytesPerSample: Int): Boolean
    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeRelease()
    private external fun nativeSetMute(muted: Boolean)

}
