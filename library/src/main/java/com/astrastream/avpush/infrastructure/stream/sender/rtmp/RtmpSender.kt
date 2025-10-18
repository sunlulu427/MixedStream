package com.astrastream.avpush.infrastructure.stream.sender.rtmp

import android.media.AudioFormat
import android.media.MediaCodec
import com.astrastream.avpush.core.RtmpErrorCode
import com.astrastream.avpush.domain.callback.OnConnectListener
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.VideoConfiguration
import com.astrastream.avpush.infrastructure.stream.sender.Sender
import java.nio.ByteBuffer

class RtmpSender : Sender {
    private var listener: OnConnectListener? = null
    private var rtmpUrl: String? = null

    companion object {
        init {
            System.loadLibrary("astra")
        }
    }

    override fun setDataSource(source: String) {
        rtmpUrl = source
    }

    override fun setOnConnectListener(listener: OnConnectListener?) {
        this.listener = listener
    }

    override fun connect() {
        nativeConnect(rtmpUrl)
    }

    override fun close() {
        nativeClose()
        listener?.onClose()
    }

    override fun configureVideo(config: VideoConfiguration) {
        val codecOrdinal = when (config.codec) {
            VideoConfiguration.VideoCodec.H264 -> 0
            VideoConfiguration.VideoCodec.H265 -> 1
        }
        nativeConfigureVideo(config.width, config.height, config.fps, codecOrdinal)
    }

    override fun configureAudio(config: AudioConfiguration, audioSpecificConfig: ByteArray?) {
        val sampleSizeBits = when (config.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 8
            else -> 16
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
        listener?.onConnecting()
    }

    fun onConnected() {
        listener?.onConnected()
    }

    fun onError(errorCode: Int) {
        val readable = RtmpErrorCode.fromCode(errorCode)?.let { code ->
            when (code) {
                RtmpErrorCode.CONNECT_FAILURE -> "RTMP server connection failed"
                RtmpErrorCode.INIT_FAILURE -> "RTMP native initialization failed"
                RtmpErrorCode.URL_SETUP_FAILURE -> "RTMP URL setup failed"
            }
        } ?: "Unknown streaming error"
        listener?.onFail(readable)
    }

    private external fun nativeConnect(url: String?)
    private external fun nativeClose()
    private external fun nativeConfigureVideo(width: Int, height: Int, fps: Int, codecOrdinal: Int)
    private external fun nativeConfigureAudio(sampleRate: Int, channels: Int, sampleSizeBits: Int, asc: ByteArray?)
    private external fun nativePushVideoFrame(buffer: ByteBuffer, offset: Int, size: Int, ptsUs: Long)
    private external fun nativePushAudioFrame(buffer: ByteBuffer, offset: Int, size: Int, ptsUs: Long)
}
