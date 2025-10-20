package com.astrastream.avpush.stream.controller

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import com.astrastream.avpush.domain.callback.IController
import com.astrastream.avpush.domain.callback.OnAudioEncodeListener
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.ScreenCaptureConfiguration
import com.astrastream.avpush.infrastructure.audio.AudioProcessor
import com.astrastream.avpush.infrastructure.audio.MixedAudioProcessor
import com.astrastream.avpush.infrastructure.codec.AudioEncoder
import com.astrastream.avpush.runtime.LogHelper
import java.nio.ByteBuffer

class ScreenAudioController(
    private val audioConfiguration: AudioConfiguration,
    private val screenConfiguration: ScreenCaptureConfiguration,
    projection: MediaProjection?
) : IController,
    AudioProcessor.OnRecordListener,
    OnAudioEncodeListener {

    private val encoder = AudioEncoder(audioConfiguration)
    private val processor = MixedAudioProcessor(audioConfiguration, screenConfiguration)

    private var listener: IController.OnAudioDataListener? = null

    init {
        processor.setRecordListener(this)
        processor.updateProjection(projection)
        processor.init()
        encoder.setOnAudioEncodeListener(this)
    }

    fun updateProjection(projection: MediaProjection?) {
        processor.updateProjection(projection)
    }

    override fun start() {
        LogHelper.d(javaClass.simpleName) { "screen audio start" }
        encoder.start()
        processor.startRecording()
    }

    override fun pause() {
        processor.setPause(true)
    }

    override fun resume() {
        processor.setPause(false)
    }

    override fun stop() {
        processor.stop()
        encoder.stop()
    }

    override fun setMute(isMute: Boolean) {
        processor.setMute(isMute)
    }

    override fun onStart(sampleRate: Int, channels: Int, sampleFormat: Int) {
        LogHelper.d(javaClass.simpleName) { "audio capture started sampleRate=$sampleRate channels=$channels format=$sampleFormat" }
    }

    override fun onError(message: String?) {
        LogHelper.e(javaClass.simpleName, message)
        listener?.onError(message)
    }

    override fun onPcmData(byteArray: ByteArray) {
        encoder.enqueueCodec(byteArray)
    }

    override fun onPause() {
        LogHelper.d(javaClass.simpleName) { "audio paused" }
    }

    override fun onResume() {
        LogHelper.d(javaClass.simpleName) { "audio resumed" }
    }

    override fun onStop() {
        LogHelper.d(javaClass.simpleName) { "audio stopped" }
    }

    override fun onAudioEncode(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        listener?.onAudioData(bb, bi)
    }

    override fun onAudioOutformat(outputFormat: MediaFormat?) {
        listener?.onAudioOutformat(outputFormat)
    }

    override fun setAudioDataListener(audioDataListener: IController.OnAudioDataListener) {
        listener = audioDataListener
    }
}
