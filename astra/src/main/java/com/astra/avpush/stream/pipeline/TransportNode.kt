package com.astra.avpush.stream.pipeline

import com.astra.avpush.stream.pipeline.core.PipelinePad
import com.astra.avpush.stream.pipeline.core.PipelineRole
import com.astra.avpush.stream.pipeline.core.PipelineStage
import com.astra.avpush.stream.pipeline.frame.EncodedAudioFrame
import com.astra.avpush.stream.pipeline.frame.EncodedVideoFrame
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.runtime.NativeStats
import com.astra.avpush.runtime.prepareForCodec
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.stream.sender.Sender
import android.os.SystemClock


class TransportNode(
    private val senderProvider: () -> Sender?
) : PipelineStage {

    override val name: String = "transport"
    override val role: PipelineRole = PipelineRole.SINK

    val audioPad: PipelinePad<EncodedAudioFrame> = PipelinePad { frame -> dispatchAudio(frame) }
    val videoPad: PipelinePad<EncodedVideoFrame> = PipelinePad { frame -> dispatchVideo(frame) }

    private var audioConfiguration: AudioConfiguration? = null
    private var videoConfiguration: VideoConfiguration? = null
    private var audioSpecificConfig: ByteArray? = null
    private var statsListener: ((Int, Int) -> Unit)? = null
    private val statsHandle: Long = NativeStats.create()
    private var statsReleased = false

    fun updateAudioConfiguration(configuration: AudioConfiguration, asc: ByteArray?) {
        audioConfiguration = configuration
        audioSpecificConfig = asc
        configureSender()
        AstraLog.i(name) { "audio configuration applied: sampleRate=${configuration.sampleRate}, channels=${configuration.channelCount}" }
    }

    fun updateVideoConfiguration(configuration: VideoConfiguration) {
        videoConfiguration = configuration
        configureSender()
        AstraLog.i(name) { "video configuration applied: ${configuration.width}x${configuration.height}@${configuration.fps}" }
    }

    fun setStatsListener(listener: ((Int, Int) -> Unit)?) {
        statsListener = listener
    }

    override fun start() {
        AstraLog.d(name) { "transport pipeline starting" }
        resetStats()
    }

    override fun pause() {
        AstraLog.d(name) { "transport pipeline paused" }
    }

    override fun resume() {
        AstraLog.d(name) { "transport pipeline resumed" }
    }

    override fun stop() {
        AstraLog.d(name) { "transport pipeline stopped" }
        resetStats()
    }

    override fun release() {
        AstraLog.d(name) { "releasing transport pipeline" }
        if (!statsReleased) {
            NativeStats.release(statsHandle)
            statsReleased = true
        }
    }

    private fun configureSender() {
        val sender = senderProvider() ?: return
        try {
            videoConfiguration?.let(sender::configureVideo)
            audioConfiguration?.let { sender.configureAudio(it, audioSpecificConfig) }
        } catch (error: Exception) {
            AstraLog.e(name, error, "Unable to configure sender")
        }
    }

    private fun dispatchAudio(frame: EncodedAudioFrame) {
        val sender = senderProvider() ?: return
        frame.buffer.prepareForCodec(frame.info)
        sender.pushAudio(frame.buffer, frame.info)
    }

    private fun dispatchVideo(frame: EncodedVideoFrame) {
        val sender = senderProvider() ?: return
        frame.buffer.prepareForCodec(frame.info)
        sender.pushVideo(frame.buffer, frame.info)
        accumulateStats(frame.info.size)
    }

    private fun accumulateStats(bytes: Int) {
        if (statsReleased) return
        val stats = NativeStats.onVideoSample(statsHandle, bytes, SystemClock.elapsedRealtime())
        if (stats != null && stats.size == 2) {
            statsListener?.invoke(stats[0].coerceAtLeast(0), stats[1].coerceAtLeast(0))
            AstraLog.d(name) { "video stats bitrate=${stats[0]}kbps fps=${stats[1]}" }
        }
    }

    private fun resetStats(referenceTime: Long = SystemClock.elapsedRealtime()) {
        if (!statsReleased) {
            NativeStats.reset(statsHandle, referenceTime)
        }
    }
}
