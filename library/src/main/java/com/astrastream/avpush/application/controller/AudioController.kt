package com.astrastream.avpush.application.controller

import android.media.MediaCodec
import android.media.MediaFormat
import com.astrastream.avpush.infrastructure.audio.AudioProcessor
import com.astrastream.avpush.domain.callback.IController
import com.astrastream.avpush.domain.callback.OnAudioEncodeListener
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.infrastructure.codec.AudioEncoder
import com.astrastream.avpush.core.utils.LogHelper
import java.nio.ByteBuffer

class AudioController(private val audioConfiguration: AudioConfiguration) : IController,
    AudioProcessor.OnRecordListener,
    OnAudioEncodeListener {

    /**
     * 音频编解码用到的实体程序
     */
    private val mAudioEncoder: AudioEncoder = AudioEncoder(audioConfiguration)

    /**
     * 音频采集用到的实体程序
     */
    private val mAudioProcessor: AudioProcessor = AudioProcessor()

    /**
     * 音频数据的监听
     */
    private var mAudioDataListener: IController.OnAudioDataListener? = null


    init {
        mAudioProcessor.init(
            audioConfiguration.audioSource,
            audioConfiguration.sampleRate,
            audioConfiguration.channelCount
        )
        mAudioProcessor.addRecordListener(this)
        mAudioEncoder.setOnAudioEncodeListener(this)
    }

    /**
     * 触发 开始
     */
    override fun start() {
        LogHelper.d(javaClass.simpleName) { "audio processor start" }
        mAudioProcessor.startRecording()
    }

    /**
     * 触发 暂停
     */
    override fun pause() {
        LogHelper.d(javaClass.simpleName) { "audio processor pause" }
        mAudioProcessor.setPause(true)
    }

    /**
     * 触发恢复
     */
    override fun resume() {
        LogHelper.d(javaClass.simpleName) { "audio processor resume" }
        mAudioProcessor.setPause(false)
    }

    /**
     * 触发停止
     */
    override fun stop() {
        LogHelper.d(javaClass.simpleName) { "audio processor stop" }
        mAudioProcessor.stop()
        mAudioProcessor.release()

    }

    /**
     * 当采集 PCM 数据的时候返回
     */
    override fun onPcmData(byteArray: ByteArray) {
        mAudioEncoder.enqueueCodec(byteArray)
    }

    /**
     * 当开始采集
     */
    override fun onStart(sampleRate: Int, channels: Int, sampleFormat: Int) {
        LogHelper.d(javaClass.simpleName) { "audio recorder started sampleRate=$sampleRate channels=$channels format=$sampleFormat" }
        mAudioEncoder.start()
    }

    /**
     * 设置禁言
     */
    override fun setMute(isMute: Boolean) {
        super.setMute(isMute)
        LogHelper.d(javaClass.simpleName) { "audio mute toggled: $isMute" }
        mAudioProcessor.setMute(isMute)
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(javaClass.simpleName) { "audio recorder stop callback" }
        mAudioEncoder.stop()
    }

    /**
     * 当采集出现错误
     */
    override fun onError(meg: String?) {
        LogHelper.e(javaClass.simpleName, meg)
        mAudioDataListener?.onError(meg)
    }

    /**
     * 当 Audio 编码数据的时候
     */
    override fun onAudioEncode(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        mAudioDataListener?.onAudioData(bb, bi)
    }

    /**
     * 编码的输出格式
     */
    override fun onAudioOutformat(outputFormat: MediaFormat?) {
        mAudioDataListener?.onAudioOutformat(outputFormat)
    }

    override fun setAudioDataListener(audioDataListener: IController.OnAudioDataListener) {
        super.setAudioDataListener(audioDataListener)
        mAudioDataListener = audioDataListener
    }
}
