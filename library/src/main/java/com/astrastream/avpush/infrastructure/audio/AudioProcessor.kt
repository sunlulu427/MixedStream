package com.astrastream.avpush.infrastructure.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.astrastream.avpush.core.STREAM_LOG_TAG
import com.astrastream.avpush.core.concurrency.ThreadImpl
import com.astrastream.avpush.core.utils.LogHelper
import java.util.Arrays

class AudioProcessor : ThreadImpl() {
    private var recordListener: OnRecordListener? = null
    private var audioRecord: AudioRecord? = null
    private var bufferSize = 0
    private var readSize = 1024
    private var isMuted = false

    private var currentSource = MediaRecorder.AudioSource.MIC
    private var currentSampleRate = 44100
    private var currentChannelConfig = AudioFormat.CHANNEL_IN_MONO
    private var currentEncoding = AudioFormat.ENCODING_PCM_16BIT

    fun init(
        audioSource: Int = MediaRecorder.AudioSource.MIC,
        sampleRateInHz: Int = 44100,
        channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
    ) {
        release()
        currentSource = audioSource
        currentSampleRate = sampleRateInHz
        currentChannelConfig = channelConfig
        currentEncoding = audioFormat

        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            val error = "Invalid AudioRecord buffer size"
            recordListener?.onError(error)
            throw IllegalStateException(error)
        }

        LogHelper.d(javaClass.simpleName) {
            "init audio processor source=$audioSource sampleRate=$sampleRateInHz channels=$channelConfig format=$audioFormat bufferSize=$bufferSize"
        }

        readSize = bufferSize

        audioRecord = try {
            AudioRecord(
                audioSource,
                sampleRateInHz,
                channelConfig,
                audioFormat,
                bufferSize
            ).also {
                if (it.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord initialization failed")
                }
            }
        } catch (error: Exception) {
            recordListener?.onError(error.message)
            LogHelper.e(STREAM_LOG_TAG, error.message)
            throw error
        }
    }

    override fun setPause(pause: Boolean) {
        super.setPause(pause)
        if (pause) {
            recordListener?.onPause()
            LogHelper.d(javaClass.simpleName) { "audio processor paused" }
        } else {
            recordListener?.onResume()
            LogHelper.d(javaClass.simpleName) { "audio processor resumed" }
        }
    }

    fun startRecording() {
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return
        super.start { mainLoop() }
        audioRecord?.startRecording()
        recordListener?.onStart(currentSampleRate, currentChannelConfig, currentEncoding)
        LogHelper.d(javaClass.simpleName) { "AudioRecord started" }
    }

    override fun stop() {
        super.stop()
        audioRecord?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }?.stop()
        recordListener?.onStop()
        LogHelper.d(javaClass.simpleName) { "AudioRecord stopped" }
    }

    fun setMute(muted: Boolean) {
        isMuted = muted
    }

    private fun mainLoop() {
        val data = ByteArray(readSize)
        while (isRunning()) {
            awaitIfPaused()
            if (!isRunning()) break

            val record = audioRecord ?: continue
            if (isMuted) {
                Arrays.fill(data, 0)
                recordListener?.onPcmData(data.copyOf())
                continue
            }

            val read = record.read(data, 0, data.size)
            if (read > 0) {
                recordListener?.onPcmData(data.copyOf(read))
            }
        }
        LogHelper.d(javaClass.simpleName) { "audio capture loop finished" }
    }

    fun addRecordListener(listener: OnRecordListener) {
        recordListener = listener
    }

    fun release() {
        audioRecord?.release()
        audioRecord = null
        LogHelper.d(javaClass.simpleName) { "AudioRecord released" }
    }

    interface OnRecordListener {
        fun onStart(sampleRate: Int, channels: Int, sampleFormat: Int)
        fun onError(message: String?)
        fun onPcmData(byteArray: ByteArray)
        fun onPause() {}
        fun onResume() {}
        fun onStop() {}
    }
}
