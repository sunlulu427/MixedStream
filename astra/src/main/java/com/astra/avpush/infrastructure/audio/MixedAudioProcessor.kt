package com.astra.avpush.infrastructure.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.runtime.ThreadImpl

class MixedAudioProcessor(
    private val audioConfiguration: AudioConfiguration,
    private val captureConfiguration: ScreenCaptureConfiguration
) : ThreadImpl() {

    private var recordListener: AudioProcessor.OnRecordListener? = null
    private var micRecord: AudioRecord? = null
    private var playbackRecord: AudioRecord? = null
    private var bufferSize = 0
    private var projection: MediaProjection? = null
    private var muted = false

    fun setRecordListener(listener: AudioProcessor.OnRecordListener) {
        recordListener = listener
    }

    fun updateProjection(mediaProjection: MediaProjection?) {
        projection = mediaProjection
        if (captureConfiguration.includePlayback) {
            rebuildPlaybackRecord()
        }
    }

    fun init() {
        val channelConfig = if (audioConfiguration.channelCount > 1) {
            AudioFormat.CHANNEL_IN_STEREO
        } else {
            AudioFormat.CHANNEL_IN_MONO
        }
        bufferSize = AudioRecord.getMinBufferSize(
            audioConfiguration.sampleRate,
            channelConfig,
            audioConfiguration.encoding
        ).coerceAtLeast(MIN_BUFFER_SIZE)

        if (captureConfiguration.includeMic) {
            micRecord = try {
                AudioRecord(
                    audioConfiguration.audioSource,
                    audioConfiguration.sampleRate,
                    channelConfig,
                    audioConfiguration.encoding,
                    bufferSize
                ).apply {
                    if (state != AudioRecord.STATE_INITIALIZED) {
                        AstraLog.e(TAG, "mic AudioRecord not initialized")
                        release()
                    }
                }
            } catch (security: SecurityException) {
                AstraLog.e(TAG, "microphone permission denied: ${security.message}")
                recordListener?.onError("麦克风权限未授予，无法采集音频")
                null
            }
        }

        if (captureConfiguration.includePlayback) {
            rebuildPlaybackRecord()
        }
    }

    private fun rebuildPlaybackRecord() {
        if (!captureConfiguration.includePlayback) return
        playbackRecord?.release()
        playbackRecord = createPlaybackRecord(projection)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildPlaybackConfig(projection: MediaProjection): AudioPlaybackCaptureConfiguration {
        return AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }

    private fun createPlaybackRecord(projection: MediaProjection?): AudioRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AstraLog.w(TAG, "audio playback capture requires API 29+")
            return null
        }
        val actualProjection = projection ?: run {
            AstraLog.w(TAG, "projection missing for playback capture")
            return null
        }
        val channelConfig = if (audioConfiguration.channelCount > 1) {
            AudioFormat.CHANNEL_IN_STEREO
        } else {
            AudioFormat.CHANNEL_IN_MONO
        }
        val config = buildPlaybackConfig(actualProjection)
        return try {
            AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setChannelMask(channelConfig)
                        .setEncoding(audioConfiguration.encoding)
                        .setSampleRate(audioConfiguration.sampleRate)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()
                .also {
                    if (it.state != AudioRecord.STATE_INITIALIZED) {
                        AstraLog.e(TAG, "playback AudioRecord not initialized")
                        it.release()
                        return null
                    }
                }
        } catch (security: SecurityException) {
            AstraLog.e(TAG, "playback capture permission denied: ${security.message}")
            recordListener?.onError("缺少录音权限，无法捕获播放音频")
            null
        }
    }

    fun startRecording() {
        val mic = micRecord
        val play = playbackRecord
        if (bufferSize <= 0 || (mic == null && play == null)) {
            recordListener?.onError("audio devices not ready")
            return
        }
        mic?.startRecording()
        play?.startRecording()
        val channelConfig = if (audioConfiguration.channelCount > 1) {
            AudioFormat.CHANNEL_IN_STEREO
        } else {
            AudioFormat.CHANNEL_IN_MONO
        }
        recordListener?.onStart(audioConfiguration.sampleRate, channelConfig, audioConfiguration.encoding)
        super.start { mainLoop() }
    }

    override fun stop() {
        super.stop()
        micRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) stop()
            release()
        }
        micRecord = null
        playbackRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) stop()
            release()
        }
        playbackRecord = null
        recordListener?.onStop()
    }

    fun setMute(mute: Boolean) {
        muted = mute
    }

    private fun mainLoop() {
        val mic = micRecord
        val play = playbackRecord
        val micBuffer = if (mic != null) ByteArray(bufferSize) else null
        val playbackBuffer = if (play != null) ByteArray(bufferSize) else null
        while (isRunning()) {
            awaitIfPaused()
            if (!isRunning()) break

            val micRead = if (mic != null && micBuffer != null) {
                mic.read(micBuffer, 0, micBuffer.size)
            } else 0
            val playbackRead = if (play != null && playbackBuffer != null) {
                play.read(playbackBuffer, 0, playbackBuffer.size)
            } else 0

            val length = maxOf(micRead, playbackRead)
            if (length <= 0) continue
            val mixed = ByteArray(length)
            mixInto(mixed, micBuffer, micRead, playbackBuffer, playbackRead)
            recordListener?.onPcmData(mixed)
        }
        AstraLog.d(TAG) { "audio mix loop finished" }
    }

    private fun mixInto(
        target: ByteArray,
        mic: ByteArray?,
        micLength: Int,
        playback: ByteArray?,
        playbackLength: Int
    ) {
        var index = 0
        while (index < target.size) {
            val micSample = if (mic != null && index + 1 < micLength) {
                toShort(mic, index)
            } else 0
            val playbackSample = if (playback != null && index + 1 < playbackLength) {
                toShort(playback, index)
            } else 0
            var mixed = if (muted) playbackSample else micSample + playbackSample
            if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE.toInt()
            if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE.toInt()
            fromShort(target, index, mixed.toShort())
            index += 2
        }
        if (target.size % 2 != 0) {
            target[target.lastIndex] = 0
        }
    }

    private fun toShort(source: ByteArray, offset: Int): Int {
        val low = source[offset].toInt() and 0xFF
        val high = source[offset + 1].toInt()
        return ((high shl 8) or low).toShort().toInt()
    }

    private fun fromShort(target: ByteArray, offset: Int, value: Short) {
        val intValue = value.toInt()
        target[offset] = (intValue and 0xFF).toByte()
        target[offset + 1] = ((intValue shr 8) and 0xFF).toByte()
    }

    companion object {
        private const val TAG = "MixedAudioProcessor"
        private const val MIN_BUFFER_SIZE = 4096
    }
}
