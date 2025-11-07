package com.astra.avpush.infrastructure.stream.sender

import com.astra.avpush.domain.callback.OnConnectListener
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import android.view.Surface

interface Sender {
    fun setDataSource(source: String)
    fun setOnConnectListener(listener: OnConnectListener?)
    fun setOnStatsListener(listener: ((Int, Int) -> Unit)?)
    fun connect()
    fun close()

    fun configureVideo(config: VideoConfiguration)
    fun configureAudio(config: AudioConfiguration)

    fun prepareVideoSurface(config: VideoConfiguration): Surface?
    fun releaseVideoSurface()

    fun startVideo()
    fun stopVideo()
    fun updateVideoBps(bps: Int)

    fun startAudio()
    fun stopAudio()
    fun pushAudioPcm(data: ByteArray, length: Int)
}
