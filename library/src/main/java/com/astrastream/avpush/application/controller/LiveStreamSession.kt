package com.astrastream.avpush.application.controller

import android.content.Context
import com.astrastream.avpush.infrastructure.camera.Watermark
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.VideoConfiguration
import com.astrastream.avpush.infrastructure.stream.packer.Packer
import com.astrastream.avpush.infrastructure.stream.sender.Sender
import javax.microedition.khronos.egl.EGLContext

/**
 * 推流用例抽象，隔离 UI 与底层实现，遵循 Clean Architecture 依赖内向原则。
 */
interface LiveStreamSession {

    interface StatsListener {
        fun onVideoStats(bitrateKbps: Int, fps: Int)
    }

    fun setAudioConfigure(audioConfiguration: AudioConfiguration)

    fun setVideoConfigure(videoConfiguration: VideoConfiguration)

    fun setPacker(packer: Packer)

    fun setSender(sender: Sender)

    fun prepare(context: Context, textureId: Int, eglContext: EGLContext?)

    fun start()

    fun pause()

    fun resume()

    fun stop()

    fun setMute(isMute: Boolean)

    fun setVideoBps(bps: Int)

    fun setWatermark(watermark: Watermark)

    fun setStatsListener(listener: StatsListener?)
}
