package com.astrastream.avpush.stream.pipeline

import com.astrastream.avpush.stream.controller.AudioController
import com.astrastream.avpush.stream.pipeline.core.PipelineRole
import com.astrastream.avpush.stream.pipeline.core.PipelineSource
import com.astrastream.avpush.stream.pipeline.frame.EncodedAudioFrame
import com.astrastream.avpush.runtime.LogHelper
import com.astrastream.avpush.domain.callback.IController
import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer


class AudioCaptureNode(
    private val controller: AudioController,
    private val onOutputFormat: (MediaFormat?) -> Unit,
    private val onError: (String?) -> Unit
) : PipelineSource<EncodedAudioFrame>("audio-capture", PipelineRole.SOURCE),
    IController.OnAudioDataListener {

    private var stopped = false

    init {
        controller.setAudioDataListener(this)
    }

    override fun start() {
        LogHelper.d(name) { "starting audio capture" }
        stopped = false
        controller.start()
    }

    override fun pause() {
        LogHelper.d(name) { "pausing audio capture" }
        controller.pause()
    }

    override fun resume() {
        LogHelper.d(name) { "resuming audio capture" }
        controller.resume()
    }

    override fun stop() {
        LogHelper.d(name) { "stopping audio capture" }
        controller.stop()
        stopped = true
    }

    override fun release() {
        if (!stopped) {
            controller.stop()
        }
        LogHelper.d(name) { "releasing audio capture node" }
        super.release()
    }

    override fun onAudioData(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        emit(EncodedAudioFrame(bb, bi))
    }

    override fun onAudioOutformat(outputFormat: MediaFormat?) {
        LogHelper.i(name) { "audio codec output format changed: ${outputFormat?.toString() ?: "null"}" }
        onOutputFormat(outputFormat)
    }

    override fun onError(error: String?) {
        LogHelper.e(name, error)
        onError(error)
    }

    fun setMute(mute: Boolean) {
        LogHelper.d(name) { "mute toggled: $mute" }
        controller.setMute(mute)
    }
}
