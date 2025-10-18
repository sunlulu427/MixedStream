package com.astrastream.avpush.application.pipeline

import android.os.SystemClock
import com.astrastream.avpush.core.pipeline.PipelinePad
import com.astrastream.avpush.core.pipeline.PipelineRole
import com.astrastream.avpush.core.pipeline.PipelineStage
import com.astrastream.avpush.core.pipeline.frame.EncodedAudioFrame
import com.astrastream.avpush.core.pipeline.frame.EncodedVideoFrame
import com.astrastream.avpush.core.utils.LogHelper
import com.astrastream.avpush.core.utils.prepareForCodec
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.VideoConfiguration
import com.astrastream.avpush.infrastructure.stream.sender.Sender
import kotlin.math.roundToInt


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
    private var statsWindowBytes = 0L
    private var statsWindowFrames = 0
    private var statsWindowStart = SystemClock.elapsedRealtime()

    fun updateAudioConfiguration(configuration: AudioConfiguration, asc: ByteArray?) {
        audioConfiguration = configuration
        audioSpecificConfig = asc
        configureSender()
    }

    fun updateVideoConfiguration(configuration: VideoConfiguration) {
        videoConfiguration = configuration
        configureSender()
    }

    fun setStatsListener(listener: ((Int, Int) -> Unit)?) {
        statsListener = listener
    }

    override fun start() {
        resetStats()
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun stop() {
        resetStats()
    }

    override fun release() {
        resetStats()
    }

    private fun configureSender() {
        val sender = senderProvider() ?: return
        try {
            videoConfiguration?.let(sender::configureVideo)
            audioConfiguration?.let { sender.configureAudio(it, audioSpecificConfig) }
        } catch (error: Exception) {
            LogHelper.e(name, error, "Unable to configure sender")
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
        statsWindowBytes += bytes
        statsWindowFrames += 1
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - statsWindowStart
        if (elapsed >= 1000) {
            val bitrateKbps = ((statsWindowBytes * 8f) / elapsed).roundToInt()
            val fps = ((statsWindowFrames * 1000f) / elapsed).roundToInt()
            statsListener?.invoke(bitrateKbps.coerceAtLeast(0), fps.coerceAtLeast(0))
            resetStats(now)
        }
    }

    private fun resetStats(referenceTime: Long = SystemClock.elapsedRealtime()) {
        statsWindowBytes = 0
        statsWindowFrames = 0
        statsWindowStart = referenceTime
    }
}
