package com.astrastream.avpush.infrastructure.stream.packer.flv

import com.astrastream.avpush.infrastructure.stream.amf.AmfMap
import com.astrastream.avpush.infrastructure.stream.amf.AmfString
import java.nio.ByteBuffer

/**
 * FLV (Flash Video) Packer Helper
 *
 * Provides utilities for creating FLV format headers, tags, and metadata
 * following Clean Architecture principles with immutable data structures
 * and clear separation of concerns.
 */
object FlvPackerHelper {

    // FLV format constants
    const val FLV_HEAD_SIZE = 9
    const val VIDEO_HEADER_SIZE = 5
    const val AUDIO_HEADER_SIZE = 2
    const val FLV_TAG_HEADER_SIZE = 11
    const val PRE_SIZE = 4
    const val AUDIO_SPECIFIC_CONFIG_SIZE = 2
    const val VIDEO_SPECIFIC_CONFIG_EXTEND_SIZE = 11

    /**
     * Write FLV header information
     *
     * @param buffer The ByteBuffer to write to
     * @param hasVideo Whether video track exists
     * @param hasAudio Whether audio track exists
     */
    fun writeFlvHeader(buffer: ByteBuffer, hasVideo: Boolean, hasAudio: Boolean) {
        // FLV Header format:
        // Bytes 1-3: File signature "FLV" (0x46 0x4C 0x56)
        // Byte 4: Version (0x01)
        // Byte 5: Flags (bit 0: video, bit 2: audio)
        // Bytes 6-9: Data offset (always 9 for version 1)

        val signature = byteArrayOf('F'.code.toByte(), 'L'.code.toByte(), 'V'.code.toByte())
        val version = 0x01.toByte()
        val videoFlag = if (hasVideo) 0x01.toByte() else 0x00.toByte()
        val audioFlag = if (hasAudio) 0x04.toByte() else 0x00.toByte()
        val flags = (videoFlag.toInt() or audioFlag.toInt()).toByte()
        val offset = byteArrayOf(0x00, 0x00, 0x00, 0x09)

        buffer.put(signature)
        buffer.put(version)
        buffer.put(flags)
        buffer.put(offset)
    }

    /**
     * Write FLV tag header
     *
     * @param buffer The ByteBuffer to write to
     * @param type Tag type (0x8: audio, 0x9: video, 0x12: script)
     * @param dataSize Data payload size
     * @param timestamp Timestamp in milliseconds
     */
    fun writeFlvTagHeader(buffer: ByteBuffer, type: Int, dataSize: Int, timestamp: Int) {
        // FLV Tag Header format (11 bytes):
        // Byte 1: Tag type
        // Bytes 2-4: Data size (UI24)
        // Bytes 5-7: Timestamp (UI24)
        // Byte 8: Timestamp extended (UI8)
        // Bytes 9-11: Stream ID (always 0)

        val sizeAndType = (dataSize and 0x00FFFFFF) or ((type and 0x1F) shl 24)
        buffer.putInt(sizeAndType)

        val time = ((timestamp shl 8) and 0xFFFFFF00.toInt()) or ((timestamp ushr 24) and 0x000000FF)
        buffer.putInt(time)

        // Stream ID (always 0)
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())
    }

    /**
     * Generate FLV metadata
     *
     * @param width Video width
     * @param height Video height
     * @param fps Frame rate
     * @param audioRate Audio sample rate
     * @param audioSize Audio sample size
     * @param isStereo Whether audio is stereo
     * @param videoCodecId Video codec ID (AVC=7, HEVC=12)
     * @return Metadata byte array
     */
    fun writeFlvMetaData(
        width: Int,
        height: Int,
        fps: Int,
        audioRate: Int,
        audioSize: Int,
        isStereo: Boolean,
        videoCodecId: Int = FlvVideoCodecID.AVC
    ): ByteArray {
        val metaDataHeader = AmfString("onMetaData", false)
        val amfMap = AmfMap().apply {
            setProperty("width", width)
            setProperty("height", height)
            setProperty("framerate", fps)
            setProperty("videocodecid", videoCodecId)
            setProperty("audiosamplerate", audioRate)
            setProperty("audiosamplesize", audioSize)
            setProperty("stereo", isStereo)
            setProperty("audiocodecid", FlvAudio.AAC)
        }

        val size = amfMap.size + metaDataHeader.size
        val amfBuffer = ByteBuffer.allocate(size)
        amfBuffer.put(metaDataHeader.bytes)
        amfBuffer.put(amfMap.bytes)
        return amfBuffer.array()
    }

    /**
     * Write first video tag with AVC configuration
     */
    fun writeFirstVideoTag(buffer: ByteBuffer, sps: ByteArray, pps: ByteArray) {
        // Write FLV Video Header
        writeVideoHeader(buffer, FlvVideoFrameType.KEY_FRAME, FlvVideoCodecID.AVC, FlvVideoAVCPacketType.SEQUENCE_HEADER)

        // AVC Configuration Record
        buffer.put(0x01.toByte()) // Configuration version
        buffer.put(sps[1]) // Profile indication
        buffer.put(sps[2]) // Profile compatibility
        buffer.put(sps[3]) // Level indication
        buffer.put(0xFF.toByte()) // Length size minus one

        // SPS
        buffer.put(0xE1.toByte()) // Number of SPS
        buffer.putShort(sps.size.toShort())
        buffer.put(sps)

        // PPS
        buffer.put(0x01.toByte()) // Number of PPS
        buffer.putShort(pps.size.toShort())
        buffer.put(pps)
    }

    /**
     * Write video header
     */
    fun writeVideoHeader(buffer: ByteBuffer, frameType: Int, codecId: Int, avcPacketType: Int) {
        val first = ((frameType and 0x0F) shl 4) or (codecId and 0x0F)
        buffer.put(first.toByte())
        buffer.put(avcPacketType.toByte())
        buffer.put(0x00.toByte()) // Composition time
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())
    }

    /**
     * Write H.264 packet
     */
    fun writeH264Packet(buffer: ByteBuffer, data: ByteArray, isKeyFrame: Boolean) {
        val frameType = if (isKeyFrame) FlvVideoFrameType.KEY_FRAME else FlvVideoFrameType.INTER_FRAME
        writeVideoHeader(buffer, frameType, FlvVideoCodecID.AVC, FlvVideoAVCPacketType.NALU)
        buffer.put(data)
    }

    /**
     * Write first H.265 video tag with HEVC configuration
     */
    fun writeFirstH265VideoTag(buffer: ByteBuffer, vps: ByteArray, sps: ByteArray, pps: ByteArray) {
        // Write FLV Video Header for H.265
        writeVideoHeader(buffer, FlvVideoFrameType.KEY_FRAME, FlvVideoCodecID.HEVC, FlvVideoHEVCPacketType.SEQUENCE_HEADER)

        // HEVC Configuration Record (HEVCDecoderConfigurationRecord)
        writeHEVCDecoderConfigurationRecord(buffer, vps, sps, pps)
    }

    /**
     * Write H.265 packet
     */
    fun writeH265Packet(buffer: ByteBuffer, data: ByteArray, isKeyFrame: Boolean) {
        val frameType = if (isKeyFrame) FlvVideoFrameType.KEY_FRAME else FlvVideoFrameType.INTER_FRAME
        writeVideoHeader(buffer, frameType, FlvVideoCodecID.HEVC, FlvVideoHEVCPacketType.NALU)
        buffer.put(data)
    }

    /**
     * Write HEVC Decoder Configuration Record
     */
    private fun writeHEVCDecoderConfigurationRecord(buffer: ByteBuffer, vps: ByteArray, sps: ByteArray, pps: ByteArray) {
        // HEVCDecoderConfigurationRecord format
        buffer.put(0x01.toByte()) // Configuration version

        // Extract profile space, tier flag, and profile idc from SPS
        val profileSpace = (sps[1].toInt() and 0xC0) shr 6
        val tierFlag = (sps[1].toInt() and 0x20) shr 5
        val profileIdc = sps[1].toInt() and 0x1F
        buffer.put(((profileSpace shl 6) or (tierFlag shl 5) or profileIdc).toByte())

        // Profile compatibility indicators (4 bytes)
        buffer.put(sps[2])
        buffer.put(sps[3])
        buffer.put(sps[4])
        buffer.put(sps[5])

        // Constraint indicator flags (6 bytes)
        for (i in 6..11) {
            if (i < sps.size) buffer.put(sps[i]) else buffer.put(0x00.toByte())
        }

        // Level idc
        buffer.put(sps[12])

        // Min spatial segmentation idc (2 bytes)
        buffer.put(0xF0.toByte())
        buffer.put(0x00.toByte())

        // Parallelism type (2 bits) + reserved (6 bits)
        buffer.put(0xFC.toByte())

        // Chroma format idc (2 bits) + reserved (6 bits)
        buffer.put(0xFD.toByte())

        // Bit depth luma minus8 (3 bits) + reserved (5 bits)
        buffer.put(0xFE.toByte())

        // Bit depth chroma minus8 (3 bits) + reserved (5 bits)
        buffer.put(0xFE.toByte())

        // Avg frame rate (2 bytes)
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())

        // Constant frame rate (2 bits) + num temporal layers (3 bits) + temporal id nested (1 bit) + length size minus one (2 bits)
        buffer.put(0x03.toByte()) // Length size minus one = 3 (4 bytes)

        // Number of arrays
        buffer.put(0x03.toByte()) // VPS, SPS, PPS

        // VPS array
        buffer.put(0x20.toByte()) // Array completeness (1) + reserved (1) + NAL unit type (6) = VPS (32)
        buffer.putShort(1) // Number of NAL units
        buffer.putShort(vps.size.toShort())
        buffer.put(vps)

        // SPS array
        buffer.put(0x21.toByte()) // NAL unit type = SPS (33)
        buffer.putShort(1) // Number of NAL units
        buffer.putShort(sps.size.toShort())
        buffer.put(sps)

        // PPS array
        buffer.put(0x22.toByte()) // NAL unit type = PPS (34)
        buffer.putShort(1) // Number of NAL units
        buffer.putShort(pps.size.toShort())
        buffer.put(pps)
    }

    /**
     * Write first audio tag
     */
    fun writeFirstAudioTag(buffer: ByteBuffer, audioRate: Int, isStereo: Boolean, audioSize: Int) {
        val soundRateIndex = getAudioSampleRateIndex(audioRate)
        val channelCount = if (isStereo) 2 else 1

        val audioInfo = byteArrayOf(
            (0x10 or ((soundRateIndex shr 1) and 0x7)).toByte(),
            (((soundRateIndex and 0x1) shl 7) or ((channelCount and 0xF) shl 3)).toByte()
        )

        writeAudioTag(buffer, audioInfo, true, audioSize)
    }

    /**
     * Write audio tag
     */
    fun writeAudioTag(buffer: ByteBuffer, audioInfo: ByteArray, isFirst: Boolean, audioSize: Int) {
        writeAudioHeader(buffer, isFirst, audioSize)
        buffer.put(audioInfo)
    }

    /**
     * Write audio header
     */
    fun writeAudioHeader(buffer: ByteBuffer, isFirst: Boolean, audioSize: Int) {
        val soundFormat = FlvAudio.AAC
        val soundRateIndex = 3 // AAC always 3
        val soundSize = if (audioSize == 8) FlvAudioSampleSize.PCM_8 else FlvAudioSampleSize.PCM_16
        val soundType = FlvAudioSampleType.STEREO // AAC always stereo
        val aacPacketType = if (isFirst) FlvAudioAACPacketType.SEQUENCE_HEADER else FlvAudioAACPacketType.RAW

        val header = byteArrayOf(
            (((soundFormat and 0x0F) shl 4) or ((soundRateIndex and 0x03) shl 2) or ((soundSize and 0x01) shl 1) or (soundType and 0x01)).toByte(),
            aacPacketType.toByte()
        )

        buffer.put(header)
    }

    /**
     * Get audio sample rate index for AMF encoding
     */
    fun getAudioSampleRateIndex(audioSampleRate: Int): Int = when (audioSampleRate) {
        96000 -> 0
        88200 -> 1
        64000 -> 2
        48000 -> 3
        44100 -> 4
        32000 -> 5
        24000 -> 6
        22050 -> 7
        16000 -> 8
        12000 -> 9
        11025 -> 10
        8000 -> 11
        7350 -> 12
        else -> 15
    }

    // FLV Constants - organized as nested objects for better namespace management

    object FlvVideoFrameType {
        const val RESERVED = 0
        const val KEY_FRAME = 1
        const val INTER_FRAME = 2
        const val DISPOSABLE_INTER_FRAME = 3
        const val GENERATED_KEY_FRAME = 4
        const val VIDEO_INFO_FRAME = 5
        const val RESERVED1 = 6
    }

    object FlvVideoAVCPacketType {
        const val SEQUENCE_HEADER = 0
        const val NALU = 1
        const val SEQUENCE_HEADER_EOF = 2
        const val RESERVED = 3
    }

    object FlvVideoHEVCPacketType {
        const val SEQUENCE_HEADER = 0
        const val NALU = 1
        const val SEQUENCE_HEADER_EOF = 2
        const val RESERVED = 3
    }

    object FlvAudioAACPacketType {
        const val SEQUENCE_HEADER = 0
        const val RAW = 1
    }

    object FlvTag {
        const val RESERVED = 0
        const val AUDIO = 8
        const val VIDEO = 9
        const val SCRIPT = 18
    }

    object FlvVideoCodecID {
        const val RESERVED = 0
        const val RESERVED1 = 1
        const val SORENSON_H263 = 2
        const val SCREEN_VIDEO = 3
        const val ON2_VP6 = 4
        const val ON2_VP6_WITH_ALPHA_CHANNEL = 5
        const val SCREEN_VIDEO_VERSION2 = 6
        const val AVC = 7
        const val DISABLED = 8
        const val RESERVED2 = 9
        const val HEVC = 12  // H.265/HEVC support
    }

    object FlvAudio {
        const val LINEAR_PCM = 0
        const val AD_PCM = 1
        const val MP3 = 2
        const val LINEAR_PCM_LE = 3
        const val NELLYMOSER_16_MONO = 4
        const val NELLYMOSER_8_MONO = 5
        const val NELLYMOSER = 6
        const val G711_A = 7
        const val G711_MU = 8
        const val RESERVED = 9
        const val AAC = 10
        const val SPEEX = 11
        const val MP3_8 = 14
        const val DEVICE_SPECIFIC = 15
    }

    object FlvAudioSampleRate {
        const val R96000 = 0
        const val R88200 = 1
        const val R64000 = 2
        const val R48000 = 3
        const val R44100 = 4
        const val R32000 = 5
        const val R24000 = 6
        const val R22050 = 7
        const val R16000 = 8
        const val R12000 = 9
        const val R11025 = 10
        const val R8000 = 11
        const val R7350 = 12
        const val RESERVED = 15
    }

    object FlvAudioSampleSize {
        const val PCM_8 = 0
        const val PCM_16 = 1
    }

    object FlvAudioSampleType {
        const val MONO = 0
        const val STEREO = 1
    }
}