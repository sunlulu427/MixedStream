package com.astrastream.avpush.domain.config

import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer

class VideoConfiguration(
    val width: Int = 720,
    val height: Int = 1280,
    val fps: Int = 30,
    val maxBps: Int = 1800,
    val minBps: Int = 400,
    val ifi: Int = 2,
    val mediaCodec: Boolean = true,
    val codeType: ICODEC = ICODEC.ENCODE,
    val mime: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    val spspps: ByteBuffer? = null,
    val surface: Surface? = null
) {
    enum class ICODEC { ENCODE, DECODE, }
}
