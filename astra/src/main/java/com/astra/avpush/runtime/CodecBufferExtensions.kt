package com.astra.avpush.runtime

import android.media.MediaCodec
import java.nio.ByteBuffer

fun ByteBuffer.prepareForCodec(info: MediaCodec.BufferInfo) {
    position(info.offset)
    limit(info.offset + info.size)
}
