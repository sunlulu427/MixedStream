package com.astra.avpush.stream.controller

import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.infrastructure.audio.NativeAudioCapturer
import com.astra.avpush.infrastructure.stream.nativebridge.NativeSender
import com.astra.avpush.runtime.AstraLog

class AudioController(
    private var audioConfiguration: AudioConfiguration,
    private val senderProvider: () -> NativeSender?
) {
    private val nativeCapturer = NativeAudioCapturer()

    init {
        nativeCapturer.configure(audioConfiguration)
    }

    fun updateConfiguration(configuration: AudioConfiguration) {
        AstraLog.d(javaClass.simpleName) {
            "audio configuration updated: sampleRate=${configuration.sampleRate} channels=${configuration.channelCount}"
        }
        audioConfiguration = configuration
        nativeCapturer.configure(configuration)
        senderProvider()?.configureAudio(configuration)
    }

    fun start() {
        AstraLog.d(javaClass.simpleName) { "audio capture start" }
        nativeCapturer.configure(audioConfiguration)
        senderProvider()?.configureAudio(audioConfiguration)
        senderProvider()?.startAudio()
        nativeCapturer.start()
    }

    fun pause() {
        AstraLog.d(javaClass.simpleName) { "audio capture pause" }
        nativeCapturer.stop()
        senderProvider()?.stopAudio()
    }

    fun resume() {
        AstraLog.d(javaClass.simpleName) { "audio capture resume" }
        senderProvider()?.startAudio()
        nativeCapturer.start()
    }

    fun stop() {
        AstraLog.d(javaClass.simpleName) { "audio capture stop" }
        nativeCapturer.stop()
        nativeCapturer.release()
        senderProvider()?.stopAudio()
    }

    fun setMute(isMute: Boolean) {
        AstraLog.d(javaClass.simpleName) { "audio mute toggled: $isMute" }
        nativeCapturer.setMute(isMute)
    }
}
