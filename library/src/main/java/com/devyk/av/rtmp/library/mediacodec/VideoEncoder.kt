package com.devyk.av.rtmp.library.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import com.devyk.av.rtmp.library.callback.OnVideoEncodeListener
import java.nio.ByteBuffer

open class VideoEncoder : BaseVideoEncoder() {


    override fun onVideoOutformat(outputFormat: MediaFormat?) {
        mListener?.onVideoOutformat(outputFormat)
    }

    private var mListener: OnVideoEncodeListener? = null

    /**
     * 视频编码完成的回调
     */
    override fun onVideoEncode(bb: ByteBuffer?, bi: MediaCodec.BufferInfo) {
        mListener?.onVideoEncode(bb!!, bi)
    }

    /**
     * 设置编码回调
     */
    fun setOnVideoEncodeListener(listener: OnVideoEncodeListener) {
        mListener = listener
    }
}