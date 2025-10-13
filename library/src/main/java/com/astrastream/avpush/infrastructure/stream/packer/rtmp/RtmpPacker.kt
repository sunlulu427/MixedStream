package com.astrastream.avpush.infrastructure.stream.packer.rtmp

import android.media.MediaCodec
import com.astrastream.avpush.infrastructure.stream.AnnexbHelper
import com.astrastream.avpush.infrastructure.stream.VideoCodecHelper
import com.astrastream.avpush.infrastructure.stream.PacketType
import com.astrastream.avpush.infrastructure.stream.packer.Packer
import com.astrastream.avpush.infrastructure.stream.packer.flv.FlvPackerHelper
import com.astrastream.avpush.domain.config.VideoConfiguration
import java.nio.ByteBuffer


class RtmpPacker(private val videoConfiguration: VideoConfiguration) : Packer,
    AnnexbHelper.AnnexbNaluListener, VideoCodecHelper.VideoNaluListener {

    private var packetListener: Packer.OnPacketListener? = null
    private var isHeaderWrite: Boolean = false
    private var isKeyFrameWrite: Boolean = false

    private var mAudioSampleRate: Int = 44100
    private var mAudioSampleSize: Int = 16
    private var mIsStereo: Boolean = false

    // Use legacy AnnexbHelper for backward compatibility or new VideoCodecHelper for enhanced support
    private val mAnnexbHelper = AnnexbHelper()
    private val mVideoCodecHelper = VideoCodecHelper(videoConfiguration.codec)

    override fun setPacketListener(listener: Packer.OnPacketListener) {
        packetListener = listener
    }

    override fun start() {
        when (videoConfiguration.codec) {
            VideoConfiguration.VideoCodec.H264 -> {
                mAnnexbHelper.setAnnexbNaluListener(this)
            }
            VideoConfiguration.VideoCodec.H265 -> {
                mVideoCodecHelper.setVideoNaluListener(this)
            }
        }
    }

    override fun onVideoData(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
        when (videoConfiguration.codec) {
            VideoConfiguration.VideoCodec.H264 -> {
                mAnnexbHelper.analyseVideoData(bb!!, bi!!)
            }
            VideoConfiguration.VideoCodec.H265 -> {
                mVideoCodecHelper.analyseVideoData(bb!!, bi!!)
            }
        }
    }

    override fun onAudioData(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        if (packetListener == null || !isHeaderWrite || !isKeyFrameWrite) {
            return
        }
        bb.position(bi.offset)
        bb.limit(bi.offset + bi.size)

        val audio = ByteArray(bi.size)
        bb.get(audio)
        val size = FlvPackerHelper.AUDIO_HEADER_SIZE + audio.size
        val buffer = ByteBuffer.allocate(size)
        FlvPackerHelper.writeAudioTag(buffer, audio, false, mAudioSampleSize)
        packetListener!!.onPacket(buffer.array(), PacketType.AUDIO)
    }

    override fun stop() {
        isHeaderWrite = false
        isKeyFrameWrite = false
        mAnnexbHelper.stop()
        mVideoCodecHelper.stop()
    }

    override fun onVideo(video: ByteArray, isKeyFrame: Boolean) {
        if (packetListener == null || !isHeaderWrite) {
            return
        }
        var packetType = PacketType.VIDEO
        if (isKeyFrame) {
            isKeyFrameWrite = true
            packetType = PacketType.KEY_FRAME
        }
        //确保第一帧是关键帧，避免一开始出现灰色模糊界面
        if (!isKeyFrameWrite) {
            return
        }
        val size = FlvPackerHelper.VIDEO_HEADER_SIZE + video.size
        val buffer = ByteBuffer.allocate(size)

        when (videoConfiguration.codec) {
            VideoConfiguration.VideoCodec.H264 -> {
                FlvPackerHelper.writeH264Packet(buffer, video, isKeyFrame)
            }
            VideoConfiguration.VideoCodec.H265 -> {
                FlvPackerHelper.writeH265Packet(buffer, video, isKeyFrame)
            }
        }
        packetListener!!.onPacket(buffer.array(), packetType)
    }


    // H.264 parameter callback
    override fun onSpsPps(sps: ByteArray?, pps: ByteArray?) {
        if (packetListener == null) {
            return
        }
        //写入第一个视频信息
        writeFirstH264VideoTag(sps, pps)
        //写入第一个音频信息
        writeFirstAudioTag()
        isHeaderWrite = true
    }

    // H.265 parameter callback
    override fun onH264SpsPps(sps: ByteArray?, pps: ByteArray?) {
        onSpsPps(sps, pps)  // Delegate to existing method for H.264
    }

    override fun onH265Parameters(vps: ByteArray?, sps: ByteArray?, pps: ByteArray?) {
        if (packetListener == null) {
            return
        }
        //写入第一个H.265视频信息
        writeFirstH265VideoTag(vps, sps, pps)
        //写入第一个音频信息
        writeFirstAudioTag()
        isHeaderWrite = true
    }

    private fun writeFirstH264VideoTag(sps: ByteArray?, pps: ByteArray?) {
        val size = FlvPackerHelper.VIDEO_HEADER_SIZE + FlvPackerHelper.VIDEO_SPECIFIC_CONFIG_EXTEND_SIZE + sps!!.size + pps!!.size
        val buffer = ByteBuffer.allocate(size)
        FlvPackerHelper.writeFirstVideoTag(buffer, sps, pps)
        packetListener!!.onPacket(buffer.array(), PacketType.FIRST_VIDEO)
    }

    private fun writeFirstH265VideoTag(vps: ByteArray?, sps: ByteArray?, pps: ByteArray?) {
        if (vps == null || sps == null || pps == null) {
            return
        }
        val hevcConfig = FlvPackerHelper.buildHevcDecoderConfigurationRecord(vps, sps, pps)
        val size = FlvPackerHelper.VIDEO_HEADER_SIZE + hevcConfig.size
        val buffer = ByteBuffer.allocate(size)
        FlvPackerHelper.writeFirstH265VideoTag(buffer, hevcConfig)
        packetListener!!.onPacket(buffer.array(), PacketType.FIRST_VIDEO)
    }

    private fun writeFirstAudioTag() {
        val size = FlvPackerHelper.AUDIO_SPECIFIC_CONFIG_SIZE + FlvPackerHelper.AUDIO_HEADER_SIZE
        val buffer = ByteBuffer.allocate(size)
        FlvPackerHelper.writeFirstAudioTag(buffer, mAudioSampleRate, mIsStereo, mAudioSampleSize)
        packetListener!!.onPacket(buffer.array(), PacketType.FIRST_AUDIO)
    }
}
