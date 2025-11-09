package com.astra.avpush.infrastructure.stream.nativebridge

import android.media.AudioFormat
import android.view.Surface
import com.astra.avpush.domain.callback.OnConnectListener
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.unified.TransportProtocol

class NativeSender internal constructor(
    private val handle: Long,
    val protocol: TransportProtocol
) {

    private val tag = "NativeSender-${protocol.name}"
    private val callbackProxy = NativeSenderCallbackProxy(handle)

    init {
        NativeSenderRegistry.register(handle)
    }

    fun setOnConnectListener(listener: OnConnectListener?) {
        NativeSenderRegistry.updateConnectListener(handle, listener)
    }

    fun setOnStatsListener(listener: ((Int, Int) -> Unit)?) {
        NativeSenderRegistry.updateStatsListener(handle, listener)
    }

    fun connect(url: String) {
        AstraLog.d(tag) { "connect invoked url=${maskUrl(url)}" }
        NativeSenderBridge.nativeConnect(handle, callbackProxy, url)
    }

    fun close() {
        AstraLog.d(tag) { "close invoked" }
        NativeSenderBridge.nativeClose(handle)
        NativeSenderRegistry.onClosed(handle)
    }

    fun configureVideo(config: VideoConfiguration) {
        NativeSenderBridge.nativeConfigureVideo(
            handle,
            config.width,
            config.height,
            config.fps,
            config.maxBps,
            config.ifi,
            config.codec.ordinal
        )
    }

    fun configureAudio(config: AudioConfiguration) {
        val bytesPerSample = when (config.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        NativeSenderBridge.nativeConfigureAudioEncoder(
            handle,
            config.sampleRate,
            config.channelCount,
            config.maxBps,
            bytesPerSample
        )
    }

    fun prepareVideoSurface(config: VideoConfiguration): Surface? {
        return NativeSenderBridge.nativePrepareVideoSurface(
            handle,
            config.width,
            config.height,
            config.fps,
            config.maxBps,
            config.ifi,
            config.codec.ordinal
        )
    }

    fun releaseVideoSurface() {
        NativeSenderBridge.nativeReleaseVideoSurface(handle)
    }

    fun startVideo() {
        NativeSenderBridge.nativeStartVideo(handle)
    }

    fun stopVideo() {
        NativeSenderBridge.nativeStopVideo(handle)
    }

    fun updateVideoBps(bps: Int) {
        NativeSenderBridge.nativeUpdateVideoBitrate(handle, bps)
    }

    fun startAudio() {
        NativeSenderBridge.nativeStartAudio(handle)
    }

    fun stopAudio() {
        NativeSenderBridge.nativeStopAudio(handle)
    }

    fun dispose() {
        AstraLog.d(tag) { "dispose invoked" }
        NativeSenderBridge.nativeDestroySender(handle)
        NativeSenderRegistry.unregister(handle)
    }

    private fun maskUrl(url: String): String {
        val separatorIndex = url.lastIndexOf('/')
        if (separatorIndex <= 0 || separatorIndex == url.lastIndex) {
            return url
        }
        val suffix = url.substring(separatorIndex + 1)
        val maskedSuffix = when {
            suffix.length <= 2 -> "**"
            suffix.length <= 4 -> suffix.take(1) + "***"
            else -> suffix.take(2) + "***" + suffix.takeLast(2)
        }
        return url.substring(0, separatorIndex + 1) + maskedSuffix
    }
}
