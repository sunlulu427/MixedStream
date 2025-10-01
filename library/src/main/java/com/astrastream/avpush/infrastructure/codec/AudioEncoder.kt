package com.astrastream.avpush.infrastructure.codec

import android.media.MediaCodec
import android.media.MediaFormat
import com.astrastream.avpush.domain.callback.OnAudioEncodeListener
import com.astrastream.avpush.domain.config.AudioConfiguration
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
