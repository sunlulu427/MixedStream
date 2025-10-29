package com.astra.avpush.stream.controller

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import com.astra.avpush.domain.callback.IController
import com.astra.avpush.domain.callback.OnAudioEncodeListener
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.infrastructure.audio.AudioProcessor
import com.astra.avpush.infrastructure.audio.MixedAudioProcessor
import com.astra.avpush.infrastructure.codec.AudioEncoder
import com.astra.avpush.runtime.AstraLog
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
        AstraLog.d(javaClass.simpleName) { "screen audio start" }
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
        AstraLog.d(javaClass.simpleName) { "audio capture started sampleRate=$sampleRate channels=$channels format=$sampleFormat" }
    }

    override fun onError(message: String?) {
        AstraLog.e(javaClass.simpleName, message)
        listener?.onError(message)
    }

    override fun onPcmData(byteArray: ByteArray) {
        encoder.enqueueCodec(byteArray)
    }

    override fun onPause() {
        AstraLog.d(javaClass.simpleName) { "audio paused" }
    }

    override fun onResume() {
        AstraLog.d(javaClass.simpleName) { "audio resumed" }
    }

    override fun onStop() {
        AstraLog.d(javaClass.simpleName) { "audio stopped" }
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
