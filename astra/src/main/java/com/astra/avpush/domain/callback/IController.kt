package com.astra.avpush.domain.callback

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

interface IController {

    fun start()

    fun pause()

    fun resume()

    fun stop()

    fun setMute(isMute: Boolean) {}

    fun setAudioDataListener(audioDataListener: OnAudioDataListener) {}
    fun setVideoDataListener(videoDataListener: OnVideoDataListener) {}
    fun setVideoBps(bps:Int){}


    interface OnAudioDataListener {
        /**
         * 当 Audio 编码数据的时候
         */
        fun onAudioData(bb: ByteBuffer, bi: MediaCodec.BufferInfo);

        /**
         * 编码的输出格式
         */
        fun onAudioOutformat(outputFormat: MediaFormat?)

        fun onError(error:String?);


    }

    interface OnVideoDataListener {
        /**
         * 当 Audio 编码数据的时候
         */
        fun onVideoData(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?);

        /**
         * 编码的输出格式
         */
        fun onVideoOutformat(outputFormat: MediaFormat?);

        fun onError(error:String?);
    }
}
