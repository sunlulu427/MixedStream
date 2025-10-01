package com.devyk.av.rtmp.library.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import com.devyk.av.rtmp.library.callback.OnAudioEncodeListener
import com.devyk.av.rtmp.library.config.AudioConfiguration
import java.nio.ByteBuffer


class AudioEncoder(mAudioConfiguration: AudioConfiguration?) : BaseAudioCodec(mAudioConfiguration) {

    override fun onAudioOutformat(outputFormat: MediaFormat?) {
        mListener?.onAudioOutformat(outputFormat)
    }

    private var mListener: OnAudioEncodeListener? = null

    override fun onAudioData(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        mListener?.onAudioEncode(bb, bi)
    }

    fun setOnAudioEncodeListener(listener: OnAudioEncodeListener?) {
        mListener = listener
    }

    override fun stop() {
        super.stop()
        mListener = null
    }
}
