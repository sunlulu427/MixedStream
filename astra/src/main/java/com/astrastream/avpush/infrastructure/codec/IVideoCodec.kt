package com.astrastream.avpush.infrastructure.codec

import android.media.MediaCodec
import com.astrastream.avpush.domain.config.VideoConfiguration
import java.nio.ByteBuffer


interface IVideoCodec {
    /**
     * 初始化编码器
     */
    fun prepare(videoConfiguration: VideoConfiguration = VideoConfiguration()) {}

    /**
     * start 编码
     */
    fun start();

    /**
     * 停止编码
     */
    fun stop();

    /**
     * 返回编码好的 H264 数据
     */
    fun onVideoEncode(bb: ByteBuffer?, mBufferInfo: MediaCodec.BufferInfo)
}
