package com.astra.avpush.stream.controller

import com.astra.avpush.domain.callback.IController
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.infrastructure.audio.AudioProcessor
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog

class AudioController(
    private val audioConfiguration: AudioConfiguration,
    private val senderProvider: () -> Sender?
) : IController,
    AudioProcessor.OnRecordListener {

    /**
     * 音频采集用到的实体程序
     */
    private val mAudioProcessor: AudioProcessor = AudioProcessor()

    init {
        mAudioProcessor.init(
            audioConfiguration.audioSource,
            audioConfiguration.sampleRate,
            audioConfiguration.channelCount
        )
        mAudioProcessor.addRecordListener(this)
    }

    /**
     * 触发 开始
     */
    override fun start() {
        AstraLog.d(javaClass.simpleName) { "audio processor start" }
        mAudioProcessor.startRecording()
    }

    /**
     * 触发 暂停
     */
    override fun pause() {
        AstraLog.d(javaClass.simpleName) { "audio processor pause" }
        mAudioProcessor.setPause(true)
    }

    /**
     * 触发恢复
     */
    override fun resume() {
        AstraLog.d(javaClass.simpleName) { "audio processor resume" }
        mAudioProcessor.setPause(false)
    }

    /**
     * 触发停止
     */
    override fun stop() {
        AstraLog.d(javaClass.simpleName) { "audio processor stop" }
        mAudioProcessor.stop()
        mAudioProcessor.release()

    }

    /**
     * 当采集 PCM 数据的时候返回
     */
    override fun onPcmData(byteArray: ByteArray) {
        senderProvider()?.pushAudioPcm(byteArray, byteArray.size)
    }

    /**
     * 当开始采集
     */
    override fun onStart(sampleRate: Int, channels: Int, sampleFormat: Int) {
        AstraLog.d(javaClass.simpleName) { "audio recorder started sampleRate=$sampleRate channels=$channels format=$sampleFormat" }
        senderProvider()?.startAudio()
    }

    /**
     * 设置禁言
     */
    override fun setMute(isMute: Boolean) {
        super.setMute(isMute)
        AstraLog.d(javaClass.simpleName) { "audio mute toggled: $isMute" }
        mAudioProcessor.setMute(isMute)
    }

    override fun onStop() {
        super.onStop()
        AstraLog.d(javaClass.simpleName) { "audio recorder stop callback" }
        senderProvider()?.stopAudio()
    }

    /**
     * 当采集出现错误
     */
    override fun onError(meg: String?) {
        AstraLog.e(javaClass.simpleName, meg)
    }

    /**
     * 当 Audio 编码数据的时候
     */
}
