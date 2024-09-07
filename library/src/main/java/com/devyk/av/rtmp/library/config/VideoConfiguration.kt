package com.devyk.av.rtmp.library.config

import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer

/**
 * <pre>
 *     author  : devyk on 2020-06-14 22:07
 *     blog    : https://juejin.im/user/578259398ac2470061f3a3fb/posts
 *     github  : https://github.com/yangkun19921001
 *     mailbox : yang1001yk@gmail.com
 *     desc    : This is VideoConfiguration
 * </pre>
 */
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