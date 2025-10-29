package com.astra.avpush.domain.callback

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * 编码回调
 */
interface OnVideoEncodeListener {
    fun onVideoEncode(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?)
    fun onVideoOutformat(outputFormat: MediaFormat?)
}
