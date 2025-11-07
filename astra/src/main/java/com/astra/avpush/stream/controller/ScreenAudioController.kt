package com.astra.avpush.stream.controller

import android.media.projection.MediaProjection
import com.astra.avpush.domain.callback.IController
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.infrastructure.audio.AudioProcessor
import com.astra.avpush.infrastructure.audio.MixedAudioProcessor
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog

class ScreenAudioController(
    private val audioConfiguration: AudioConfiguration,
    private val screenConfiguration: ScreenCaptureConfiguration,
    projection: MediaProjection?,
    private val senderProvider: () -> Sender?
) : IController,
    AudioProcessor.OnRecordListener {

    private val processor = MixedAudioProcessor(audioConfiguration, screenConfiguration)

    init {
        processor.setRecordListener(this)
        processor.updateProjection(projection)
        processor.init()
    }

    fun updateProjection(projection: MediaProjection?) {
        processor.updateProjection(projection)
    }

    override fun start() {
        AstraLog.d(javaClass.simpleName) { "screen audio start" }
        senderProvider()?.configureAudio(audioConfiguration)
        senderProvider()?.startAudio()
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
        senderProvider()?.stopAudio()
    }

    override fun setMute(isMute: Boolean) {
        processor.setMute(isMute)
    }

    override fun onStart(sampleRate: Int, channels: Int, sampleFormat: Int) {
        AstraLog.d(javaClass.simpleName) { "audio capture started sampleRate=$sampleRate channels=$channels format=$sampleFormat" }
    }

    override fun onError(message: String?) {
        AstraLog.e(javaClass.simpleName, message)
    }

    override fun onPcmData(byteArray: ByteArray) {
        senderProvider()?.pushAudioPcm(byteArray, byteArray.size)
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

}
