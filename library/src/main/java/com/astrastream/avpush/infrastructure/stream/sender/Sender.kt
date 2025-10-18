package com.astrastream.avpush.infrastructure.stream.sender

import android.media.MediaCodec
import com.astrastream.avpush.domain.callback.OnConnectListener
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.VideoConfiguration
import java.nio.ByteBuffer

interface Sender {
    fun setDataSource(source: String)
    fun setOnConnectListener(listener: OnConnectListener?)
    fun connect()
    fun close()

    fun configureVideo(config: VideoConfiguration)
    fun configureAudio(config: AudioConfiguration, audioSpecificConfig: ByteArray?)

    fun pushVideo(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
    fun pushAudio(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
}
