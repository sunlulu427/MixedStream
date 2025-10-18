package com.astrastream.avpush.core.pipeline.frame

import android.media.MediaCodec
import java.nio.ByteBuffer

data class EncodedVideoFrame(
    val buffer: ByteBuffer,
    val info: MediaCodec.BufferInfo
)
