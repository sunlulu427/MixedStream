package com.astrastream.avpush.infrastructure.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.astrastream.avpush.core.STREAM_LOG_TAG
import com.astrastream.avpush.core.concurrency.ThreadImpl
import com.astrastream.avpush.core.utils.LogHelper
import java.util.Arrays

class AudioProcessor : ThreadImpl() {
    private val lock = Object()
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
        } else {
            synchronized(lock) { lock.notifyAll() }
            recordListener?.onResume()
        }
    }

    fun startRecording() {
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return
        super.start { mainLoop() }
        audioRecord?.startRecording()
        recordListener?.onStart(currentSampleRate, currentChannelConfig, currentEncoding)
    }

    override fun stop() {
        super.stop()
        audioRecord?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }?.stop()
        recordListener?.onStop()
    }

    fun setMute(muted: Boolean) {
        isMuted = muted
    }

    private fun mainLoop() {
        val data = ByteArray(readSize)
        while (isRunning()) {
            synchronized(lock) {
                if (isPause()) {
                    lock.wait()
                    return@synchronized
                }

                if (isMuted) {
                    Arrays.fill(data, 0)
                    recordListener?.onPcmData(data)
                    return@synchronized
                }

                val record = audioRecord ?: return@synchronized
                val read = record.read(data, 0, data.size)
                if (read > 0) {
                    recordListener?.onPcmData(data.copyOf(read))
                }
            }
        }
    }

    fun addRecordListener(listener: OnRecordListener) {
        recordListener = listener
    }

    fun getBufferSize(): Int = bufferSize

    fun release() {
        audioRecord?.release()
        audioRecord = null
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
