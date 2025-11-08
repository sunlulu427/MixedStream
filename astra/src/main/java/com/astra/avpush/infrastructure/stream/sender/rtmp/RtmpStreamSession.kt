package com.astra.avpush.infrastructure.stream.sender.rtmp

import android.media.AudioFormat
import android.view.Surface
import com.astra.avpush.domain.callback.OnConnectListener
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.runtime.RtmpErrorCode

/**
 * Represents the full RTMP streaming session: connection, encoder control, surfaces, and stats.
 */
class RtmpStreamSession : Sender {
    private val tag = javaClass.simpleName
    private var listener: OnConnectListener? = null
    private var rtmpUrl: String? = null
    private var statsListener: ((Int, Int) -> Unit)? = null
    private var videoSurface: Surface? = null
    private var videoConfig: VideoConfiguration = VideoConfiguration()
    private var audioConfig: AudioConfiguration = AudioConfiguration()

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

    override fun setOnStatsListener(listener: ((Int, Int) -> Unit)?) {
        statsListener = listener
        AstraLog.d(tag) { "stats listener updated=${listener != null}" }
    }

    override fun connect() {
        AstraLog.d(tag) { "connect invoked url=${maskUrl(rtmpUrl)}" }
        nativeConnect(rtmpUrl)
    }

    override fun close() {
        AstraLog.d(tag) { "close invoked" }
        releaseVideoSurface()
        nativeClose()
        listener?.onClose()
        statsListener = null
    }

    override fun configureVideo(config: VideoConfiguration) {
        videoConfig = config
        AstraLog.d(tag) {
            "cache video config width=${config.width} height=${config.height} fps=${config.fps} codec=${config.codec}"
        }
    }

    override fun configureAudio(config: AudioConfiguration) {
        audioConfig = config
        val bytesPerSample = when (config.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        AstraLog.d(tag) {
            "configure audio sampleRate=${config.sampleRate} channels=${config.channelCount} bytesPerSample=$bytesPerSample"
        }
        nativeConfigureAudioEncoder(
            config.sampleRate,
            config.channelCount,
            config.maxBps,
            bytesPerSample
        )
    }

    override fun prepareVideoSurface(config: VideoConfiguration): Surface? {
        videoConfig = config
        if (videoSurface != null) {
            return videoSurface
        }
        val codecOrdinal = when (config.codec) {
            VideoConfiguration.VideoCodec.H264 -> 0
            VideoConfiguration.VideoCodec.H265 -> 1
        }
        AstraLog.d(tag) {
            "prepare video surface width=${config.width} height=${config.height} fps=${config.fps} codec=${config.codec}"
        }
        videoSurface = nativePrepareVideoSurface(
            config.width,
            config.height,
            config.fps,
            config.maxBps,
            config.ifi,
            codecOrdinal
        )
        return videoSurface
    }

    override fun releaseVideoSurface() {
        AstraLog.d(tag) { "release video surface" }
        videoSurface?.release()
        videoSurface = null
        nativeReleaseVideoSurface()
    }

    override fun startVideo() {
        AstraLog.d(tag) { "start video encoder" }
        nativeStartVideoEncoder()
    }

    override fun stopVideo() {
        AstraLog.d(tag) { "stop video encoder" }
        nativeStopVideoEncoder()
    }

    override fun updateVideoBps(bps: Int) {
        AstraLog.d(tag) { "update video bitrate=$bps" }
        nativeUpdateVideoBitrate(bps)
    }

    override fun startAudio() {
        AstraLog.d(tag) { "start audio encoder" }
        nativeStartAudioEncoder()
    }

    override fun stopAudio() {
        AstraLog.d(tag) { "stop audio encoder" }
        nativeStopAudioEncoder()
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

    fun onStreamStats(bitrateKbps: Int, fps: Int) {
        AstraLog.d(tag) { "native stats bitrate=${bitrateKbps}kbps fps=$fps" }
        statsListener?.invoke(bitrateKbps, fps)
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
    private external fun nativePrepareVideoSurface(
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        iframeInterval: Int,
        codecOrdinal: Int
    ): Surface?
    private external fun nativeReleaseVideoSurface()
    private external fun nativeStartVideoEncoder()
    private external fun nativeStopVideoEncoder()
    private external fun nativeUpdateVideoBitrate(bitrateKbps: Int)
    private external fun nativeConfigureAudioEncoder(
        sampleRate: Int,
        channels: Int,
        bitrateKbps: Int,
        bytesPerSample: Int
    )
    private external fun nativeStartAudioEncoder()
    private external fun nativeStopAudioEncoder()
}
