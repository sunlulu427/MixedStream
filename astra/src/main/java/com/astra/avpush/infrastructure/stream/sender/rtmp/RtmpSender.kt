package com.astra.avpush.infrastructure.stream.sender.rtmp

import android.media.AudioFormat
import android.media.MediaCodec
import com.astra.avpush.domain.callback.OnConnectListener
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.runtime.RtmpErrorCode
import java.nio.ByteBuffer

class RtmpSender : Sender {
    private val tag = javaClass.simpleName
    private var listener: OnConnectListener? = null
    private var rtmpUrl: String? = null

    companion object {
        init {
            System.loadLibrary("astra")
        }
    }

    override fun setDataSource(source: String) {
        rtmpUrl = source
        AstraLog.i(tag) { "data source configured url=${maskUrl(source)}" }
    }

    override fun setOnConnectListener(listener: OnConnectListener?) {
        this.listener = listener
        AstraLog.d(tag) { "connect listener updated=${listener != null}" }
    }

    override fun connect() {
        AstraLog.d(tag) { "connect invoked url=${maskUrl(rtmpUrl)}" }
        nativeConnect(rtmpUrl)
    }

    override fun close() {
        AstraLog.d(tag) { "close invoked" }
        nativeClose()
        listener?.onClose()
    }

    override fun configureVideo(config: VideoConfiguration) {
        val codecOrdinal = when (config.codec) {
            VideoConfiguration.VideoCodec.H264 -> 0
            VideoConfiguration.VideoCodec.H265 -> 1
        }
        AstraLog.d(tag) {
            "configure video width=${config.width} height=${config.height} fps=${config.fps} codec=${config.codec}"
        }
        nativeConfigureVideo(config.width, config.height, config.fps, codecOrdinal)
    }

    override fun configureAudio(config: AudioConfiguration, audioSpecificConfig: ByteArray?) {
        val sampleSizeBits = when (config.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 8
            else -> 16
        }
        AstraLog.d(tag) {
            "configure audio sampleRate=${config.sampleRate} channels=${config.channelCount} sampleSizeBits=$sampleSizeBits asc=${audioSpecificConfig?.size ?: 0}"
        }
        nativeConfigureAudio(config.sampleRate, config.channelCount, sampleSizeBits, audioSpecificConfig)
    }

    override fun pushVideo(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        nativePushVideoFrame(buffer, info.offset, info.size, info.presentationTimeUs)
    }

    override fun pushAudio(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        nativePushAudioFrame(buffer, info.offset, info.size, info.presentationTimeUs)
    }

    fun onConnecting() {
        AstraLog.i(tag) { "native reported connecting" }
        listener?.onConnecting()
    }

    fun onConnected() {
        AstraLog.i(tag) { "native reported connected" }
        listener?.onConnected()
    }

    fun onClose() {
        AstraLog.i(tag) { "native reported closed" }
        listener?.onClose()
    }

    fun onError(errorCode: Int) {
        AstraLog.e(tag) { "native reported errorCode=$errorCode" }
        val readable = RtmpErrorCode.Companion.fromCode(errorCode)?.let { code ->
            when (code) {
                RtmpErrorCode.CONNECT_FAILURE -> "RTMP server connection failed"
                RtmpErrorCode.INIT_FAILURE -> "RTMP native initialization failed"
                RtmpErrorCode.URL_SETUP_FAILURE -> "RTMP URL setup failed"
            }
        } ?: "Unknown streaming error"
        AstraLog.e(tag) { "error translated message=$readable" }
        listener?.onFail(readable)
    }

    private fun maskUrl(url: String?): String {
        if (url.isNullOrBlank()) return "null"
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

    private external fun nativeConnect(url: String?)
    private external fun nativeClose()
    private external fun nativeConfigureVideo(width: Int, height: Int, fps: Int, codecOrdinal: Int)
    private external fun nativeConfigureAudio(sampleRate: Int, channels: Int, sampleSizeBits: Int, asc: ByteArray?)
    private external fun nativePushVideoFrame(buffer: ByteBuffer, offset: Int, size: Int, ptsUs: Long)
    private external fun nativePushAudioFrame(buffer: ByteBuffer, offset: Int, size: Int, ptsUs: Long)
}
