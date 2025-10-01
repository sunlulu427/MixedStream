package com.devyk.av.rtmp.library.callback

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

interface OnAudioEncodeListener {
    fun onAudioEncode(bb: ByteBuffer, bi: MediaCodec.BufferInfo)
    fun onAudioOutformat(outputFormat: MediaFormat?)
}