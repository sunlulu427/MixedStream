package com.devyk.av.rtmp.library.config

import android.media.AudioFormat
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder

/**
 * <pre>
 *     author  : devyk on 2020-06-13 15:26
 *     blog    : https://juejin.im/user/578259398ac2470061f3a3fb/posts
 *     github  : https://github.com/yangkun19921001
 *     mailbox : yang1001yk@gmail.com
 *     desc    : This is AudioConfiguration
 * </pre>
 */
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
