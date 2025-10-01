package com.devyk.av.rtmp.library.stream.packer

import android.media.MediaCodec
import com.devyk.av.rtmp.library.stream.PacketType
import java.nio.ByteBuffer


interface Packer {
    interface OnPacketListener {
        fun onPacket(byteArray: ByteArray, packetType: PacketType)
        fun onPacket(sps: ByteArray?,pps: ByteArray?, packetType: PacketType){}
    }

    /**
     * 设置打包监听器
     */
    fun setPacketListener(packetListener: OnPacketListener)

    /**
     *处理视频硬编编码器输出的数据
     */
    fun onVideoData(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?)

    /**
     * 处理音频硬编编码器输出的数据
     * */
    fun onAudioData(bb: ByteBuffer, bi: MediaCodec.BufferInfo)

    /**
     * 处理视频 SPS PPS 数据
     */
    fun onVideoSpsPpsData(sps: ByteArray, pps: ByteArray, spsPps: PacketType) {

    }


    fun start();
    fun stop();
}
