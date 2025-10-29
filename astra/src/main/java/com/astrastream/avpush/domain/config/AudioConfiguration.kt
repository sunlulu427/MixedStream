package com.astrastream.avpush.domain.config

import android.media.AudioFormat
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder

class AudioConfiguration(
    val minBps: Int = 32,
    val maxBps: Int = 64,
    val adts: Int = 0,
    val sampleRate: Int = 44100,
    val channelCount: Int = 1,
    val codeType: CodeType = CodeType.ENCODE,
    val mime: String = MediaFormat.MIMETYPE_AUDIO_AAC,
    val aacProfile: Int = MediaCodecInfo.CodecProfileLevel.AACObjectLC,
    val aec: Boolean = false,
    val mediaCodec: Boolean = true,
    val audioSource: Int = MediaRecorder.AudioSource.MIC,
    val encoding: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    enum class CodeType { ENCODE, DECODE }
}
