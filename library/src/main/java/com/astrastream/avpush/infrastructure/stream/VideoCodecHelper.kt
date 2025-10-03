package com.astrastream.avpush.infrastructure.stream

import android.media.MediaCodec
import com.astrastream.avpush.core.Contacts
import com.astrastream.avpush.core.utils.LogHelper
import com.astrastream.avpush.domain.config.VideoConfiguration
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Video Codec Helper supporting both H.264 and H.265
 *
 * Handles NAL unit parsing and extraction for both AVC (H.264) and HEVC (H.265)
 * following Clean Architecture principles with codec-agnostic interface.
 */
class VideoCodecHelper(private val videoCodec: VideoConfiguration.VideoCodec) {

    private var mListener: VideoNaluListener? = null

    // H.264 parameters
    private var mPps: ByteArray? = null
    private var mSps: ByteArray? = null

    // H.265 parameters
    private var mVps: ByteArray? = null
    private var mH265Sps: ByteArray? = null
    private var mH265Pps: ByteArray? = null

    private var mUploadParameters = true

    /**
     * The search result for annexb.
     */
    internal inner class AnnexbSearch {
        var startCode = 0
        var match = false
    }

    interface VideoNaluListener {
        fun onH264SpsPps(sps: ByteArray?, pps: ByteArray?)
        fun onH265Parameters(vps: ByteArray?, sps: ByteArray?, pps: ByteArray?)
        fun onVideo(data: ByteArray, isKeyFrame: Boolean)
    }

    fun setVideoNaluListener(listener: VideoNaluListener) {
        mListener = listener
    }

    fun stop() {
        mListener = null
        mPps = null
        mSps = null
        mVps = null
        mH265Sps = null
        mH265Pps = null
        mUploadParameters = true
    }

    /**
     * Process encoded video data and generate frame data for FLV packer
     * @param bb Encoded data buffer
     * @param bi Buffer info from MediaCodec
     */
    fun analyseVideoData(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        bb.position(bi.offset)
        bb.limit(bi.offset + bi.size)
        val frames = ArrayList<ByteArray>()
        var isKeyFrame = false

        while (bb.position() < bi.offset + bi.size) {
            val frame = annexbDemux(bb, bi)
            if (frame == null) {
                LogHelper.e(Contacts.TAG, "annexb not match.")
                break
            }

            when (videoCodec) {
                VideoConfiguration.VideoCodec.H264 -> {
                    if (processH264Frame(frame)) continue
                    isKeyFrame = isH264KeyFrame(frame)
                }
                VideoConfiguration.VideoCodec.H265 -> {
                    if (processH265Frame(frame)) continue
                    isKeyFrame = isH265KeyFrame(frame)
                }
            }

            val naluHeader = buildNaluHeader(frame.size)
            frames.add(naluHeader)
            frames.add(frame)
        }

        // Notify parameter sets when ready
        if (mListener != null && mUploadParameters) {
            when (videoCodec) {
                VideoConfiguration.VideoCodec.H264 -> {
                    if (mPps != null && mSps != null) {
                        mListener!!.onH264SpsPps(mSps, mPps)
                        mUploadParameters = false
                    }
                }
                VideoConfiguration.VideoCodec.H265 -> {
                    if (mVps != null && mH265Sps != null && mH265Pps != null) {
                        mListener!!.onH265Parameters(mVps, mH265Sps, mH265Pps)
                        mUploadParameters = false
                    }
                }
            }
        }

        if (frames.size == 0 || mListener == null) {
            return
        }

        var size = 0
        for (i in frames.indices) {
            val frame = frames[i]
            size += frame.size
        }
        val data = ByteArray(size)
        var currentSize = 0
        for (i in frames.indices) {
            val frame = frames[i]
            System.arraycopy(frame, 0, data, currentSize, frame.size)
            currentSize += frame.size
        }
        if (mListener != null) {
            mListener!!.onVideo(data, isKeyFrame)
        }
    }

    private fun processH264Frame(frame: ByteArray): Boolean {
        // ignore the nalu type aud(9)
        if (isAccessUnitDelimiter(frame)) {
            return true
        }
        // for pps
        if (isPps(frame)) {
            mPps = frame
            return true
        }
        // for sps
        if (isSps(frame)) {
            mSps = frame
            return true
        }
        return false
    }

    private fun processH265Frame(frame: ByteArray): Boolean {
        // ignore access unit delimiter
        if (isH265AccessUnitDelimiter(frame)) {
            return true
        }
        // for VPS
        if (isVps(frame)) {
            mVps = frame
            return true
        }
        // for SPS
        if (isH265Sps(frame)) {
            mH265Sps = frame
            return true
        }
        // for PPS
        if (isH265Pps(frame)) {
            mH265Pps = frame
            return true
        }
        return false
    }

    /**
     * Extract one NAL frame from encoded data
     */
    private fun annexbDemux(bb: ByteBuffer, bi: MediaCodec.BufferInfo): ByteArray? {
        val annexbSearch = AnnexbSearch()
        avcStartWithAnnexb(annexbSearch, bb, bi)

        if (!annexbSearch.match || annexbSearch.startCode < 3) {
            return null
        }
        for (i in 0 until annexbSearch.startCode) {
            bb.get()
        }

        val frameBuffer = bb.slice()
        val pos = bb.position()
        while (bb.position() < bi.offset + bi.size) {
            avcStartWithAnnexb(annexbSearch, bb, bi)
            if (annexbSearch.match) {
                break
            }
            bb.get()
        }

        val size = bb.position() - pos
        val frameBytes = ByteArray(size)
        frameBuffer.get(frameBytes)
        return frameBytes
    }

    /**
     * Search for NAL start code in ByteBuffer
     */
    private fun avcStartWithAnnexb(`as`: AnnexbSearch, bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        `as`.match = false
        `as`.startCode = 0
        var pos = bb.position()
        while (pos < bi.offset + bi.size - 3) {
            // not match.
            if (bb.get(pos).toInt() != 0x00 || bb.get(pos + 1).toInt() != 0x00) {
                break
            }

            // match N[00] 00 00 01, where N>=0
            if (bb.get(pos + 2).toInt() == 0x01) {
                `as`.match = true
                `as`.startCode = pos + 3 - bb.position()
                break
            }
            pos++
        }
    }

    private fun buildNaluHeader(length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.putInt(length)
        return buffer.array()
    }

    // H.264 NAL unit type detection
    private fun isSps(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = frame[0] and 0x1f
        return nal_unit_type.toInt() == H264_SPS
    }

    private fun isPps(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = frame[0] and 0x1f
        return nal_unit_type.toInt() == H264_PPS
    }

    private fun isH264KeyFrame(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = frame[0] and 0x1f
        return nal_unit_type.toInt() == H264_IDR
    }

    private fun isAccessUnitDelimiter(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = frame[0] and 0x1f
        return nal_unit_type.toInt() == H264_AUD
    }

    // H.265 NAL unit type detection
    private fun isVps(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = (frame[0].toInt() and 0x7E) shr 1
        return nal_unit_type == H265_VPS
    }

    private fun isH265Sps(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = (frame[0].toInt() and 0x7E) shr 1
        return nal_unit_type == H265_SPS
    }

    private fun isH265Pps(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = (frame[0].toInt() and 0x7E) shr 1
        return nal_unit_type == H265_PPS
    }

    private fun isH265KeyFrame(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = (frame[0].toInt() and 0x7E) shr 1
        return nal_unit_type in H265_IRAP_VCL_MIN..H265_IRAP_VCL_MAX
    }

    private fun isH265AccessUnitDelimiter(frame: ByteArray): Boolean {
        if (frame.size < 1) {
            return false
        }
        val nal_unit_type = (frame[0].toInt() and 0x7E) shr 1
        return nal_unit_type == H265_AUD
    }

    companion object {
        // H.264 NAL unit types
        const val H264_NonIDR = 1
        const val H264_IDR = 5
        const val H264_SEI = 6
        const val H264_SPS = 7
        const val H264_PPS = 8
        const val H264_AUD = 9

        // H.265 NAL unit types
        const val H265_VPS = 32
        const val H265_SPS = 33
        const val H265_PPS = 34
        const val H265_AUD = 35
        const val H265_IRAP_VCL_MIN = 16  // BLA_W_LP
        const val H265_IRAP_VCL_MAX = 23  // RSV_IRAP_VCL23
    }
}