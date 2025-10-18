package com.astrastream.avpush.application.pipeline

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import com.astrastream.avpush.application.controller.AudioController
import com.astrastream.avpush.core.pipeline.PipelineRole
import com.astrastream.avpush.core.pipeline.PipelineSource
import com.astrastream.avpush.core.pipeline.frame.EncodedAudioFrame
import com.astrastream.avpush.domain.callback.IController


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
        stopped = false
        controller.start()
    }

    override fun pause() {
        controller.pause()
    }

    override fun resume() {
        controller.resume()
    }

    override fun stop() {
        controller.stop()
        stopped = true
    }

    override fun release() {
        if (!stopped) {
            controller.stop()
        }
        super.release()
    }

    override fun onAudioData(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        emit(EncodedAudioFrame(bb, bi))
    }

    override fun onAudioOutformat(outputFormat: MediaFormat?) {
        onOutputFormat(outputFormat)
    }

    override fun onError(error: String?) {
        onError(error)
    }

    fun setMute(mute: Boolean) {
        controller.setMute(mute)
    }
}
