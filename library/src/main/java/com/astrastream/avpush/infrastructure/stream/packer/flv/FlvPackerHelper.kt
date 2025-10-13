package com.astrastream.avpush.infrastructure.stream.packer.flv

import com.astrastream.avpush.infrastructure.stream.amf.AmfMap
import com.astrastream.avpush.infrastructure.stream.amf.AmfString
import java.io.ByteArrayOutputStream
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
    fun writeFirstH265VideoTag(buffer: ByteBuffer, hevcConfig: ByteArray) {
        writeVideoHeader(buffer, FlvVideoFrameType.KEY_FRAME, FlvVideoCodecID.HEVC, FlvVideoHEVCPacketType.SEQUENCE_HEADER)
        buffer.put(hevcConfig)
    }

    /**
     * Build HEVC decoder configuration record (HEVCDecoderConfigurationRecord)
     */
    fun buildHevcDecoderConfigurationRecord(vps: ByteArray, sps: ByteArray, pps: ByteArray): ByteArray {
        val config = parseHevcConfiguration(sps) ?: HevcDecoderConfiguration()
        val totalSize = 38 + vps.size + sps.size + pps.size
        val buffer = ByteBuffer.allocate(totalSize)

        buffer.put(0x01.toByte())
        val profileByte = ((config.generalProfileSpace and 0x03) shl 6) or
            ((config.generalTierFlag and 0x01) shl 5) or
            (config.generalProfileIdc and 0x1F)
        buffer.put(profileByte.toByte())
        buffer.putInt(config.generalProfileCompatibilityFlags)
        for (shift in 40 downTo 0 step 8) {
            buffer.put(((config.generalConstraintIndicatorFlags shr shift) and 0xFF).toByte())
        }
        buffer.put((config.generalLevelIdc and 0xFF).toByte())

        val minSeg = config.minSpatialSegmentationIdc and 0x0FFF
        buffer.put(((0xF shl 4) or (minSeg shr 8)).toByte())
        buffer.put((minSeg and 0xFF).toByte())

        buffer.put(((0x3F shl 2) or (config.parallelismType and 0x03)).toByte())
        buffer.put(((0x3F shl 2) or (config.chromaFormatIdc and 0x03)).toByte())
        buffer.put(((0x1F shl 3) or (config.bitDepthLumaMinus8 and 0x07)).toByte())
        buffer.put(((0x1F shl 3) or (config.bitDepthChromaMinus8 and 0x07)).toByte())

        buffer.putShort((config.avgFrameRate and 0xFFFF).toShort())

        val temporalLayers = (config.numTemporalLayers - 1).coerceIn(0, 7)
        val flagsByte = ((config.constantFrameRate and 0x03) shl 6) or
            ((temporalLayers and 0x07) shl 3) or
            ((if (config.temporalIdNested) 1 else 0) shl 2) or
            (config.lengthSizeMinusOne and 0x03)
        buffer.put(flagsByte.toByte())

        buffer.put(0x03.toByte())

        putNalArray(buffer, HevcNalType.VPS, vps)
        putNalArray(buffer, HevcNalType.SPS, sps)
        putNalArray(buffer, HevcNalType.PPS, pps)

        return buffer.array()
    }

    /**
     * Write H.265 packet
     */
    fun writeH265Packet(buffer: ByteBuffer, data: ByteArray, isKeyFrame: Boolean) {
        val frameType = if (isKeyFrame) FlvVideoFrameType.KEY_FRAME else FlvVideoFrameType.INTER_FRAME
        writeVideoHeader(buffer, frameType, FlvVideoCodecID.HEVC, FlvVideoHEVCPacketType.NALU)
        buffer.put(data)
    }

    private fun putNalArray(buffer: ByteBuffer, nalType: Int, data: ByteArray) {
        val header = ((1 shl 7) or (0 shl 6) or (nalType and 0x3F)).toByte()
        buffer.put(header)
        buffer.putShort(1)
        buffer.putShort(data.size.toShort())
        buffer.put(data)
    }

    private fun parseHevcConfiguration(sps: ByteArray): HevcDecoderConfiguration? {
        val rbsp = toRbsp(sps) ?: return null
        if (rbsp.isEmpty()) return null
        val reader = BitReader(rbsp)

        reader.readBits(4) // sps_video_parameter_set_id
        val maxSubLayersMinus1 = reader.readBits(3)
        val temporalIdNestedFlag = reader.readBit() == 1

        val profileTierLevel = parseProfileTierLevel(reader, maxSubLayersMinus1) ?: return null

        reader.readUE() // sps_seq_parameter_set_id
        val chromaFormatIdc = reader.readUE()
        if (chromaFormatIdc == 3) {
            reader.readBit() // separate_colour_plane_flag
        }

        reader.readUE() // pic_width_in_luma_samples
        reader.readUE() // pic_height_in_luma_samples

        val conformanceWindowFlag = reader.readBit()
        if (conformanceWindowFlag == 1) {
            reader.readUE()
            reader.readUE()
            reader.readUE()
            reader.readUE()
        }

        val bitDepthLumaMinus8 = reader.readUE()
        val bitDepthChromaMinus8 = reader.readUE()

        return HevcDecoderConfiguration(
            generalProfileSpace = profileTierLevel.generalProfileSpace,
            generalTierFlag = profileTierLevel.generalTierFlag,
            generalProfileIdc = profileTierLevel.generalProfileIdc,
            generalProfileCompatibilityFlags = profileTierLevel.generalProfileCompatibilityFlags,
            generalConstraintIndicatorFlags = profileTierLevel.generalConstraintIndicatorFlags,
            generalLevelIdc = profileTierLevel.generalLevelIdc,
            chromaFormatIdc = chromaFormatIdc.coerceIn(0, 3),
            bitDepthLumaMinus8 = bitDepthLumaMinus8.coerceIn(0, 7),
            bitDepthChromaMinus8 = bitDepthChromaMinus8.coerceIn(0, 7),
            numTemporalLayers = (maxSubLayersMinus1 + 1).coerceIn(1, 8),
            temporalIdNested = temporalIdNestedFlag
        )
    }

    private fun parseProfileTierLevel(reader: BitReader, maxSubLayersMinus1: Int): ProfileTierLevel? {
        val generalProfileSpace = reader.readBits(2)
        val generalTierFlag = reader.readBit()
        val generalProfileIdc = reader.readBits(5)

        var compatibilityFlags = 0
        repeat(32) {
            compatibilityFlags = (compatibilityFlags shl 1) or reader.readBit()
        }

        var constraintFlags = 0L
        repeat(48) {
            constraintFlags = (constraintFlags shl 1) or reader.readBit().toLong()
        }

        val generalLevelIdc = reader.readBits(8)

        val subLayerProfilePresentFlags = IntArray(maxSubLayersMinus1)
        val subLayerLevelPresentFlags = IntArray(maxSubLayersMinus1)
        for (i in 0 until maxSubLayersMinus1) {
            subLayerProfilePresentFlags[i] = reader.readBit()
            subLayerLevelPresentFlags[i] = reader.readBit()
        }

        if (maxSubLayersMinus1 > 0) {
            repeat(8 - maxSubLayersMinus1) { reader.readBits(2) }
        }

        for (i in 0 until maxSubLayersMinus1) {
            if (subLayerProfilePresentFlags[i] == 1) {
                reader.readBits(2) // sub_layer_profile_space
                reader.readBits(1) // sub_layer_tier_flag
                reader.readBits(5) // sub_layer_profile_idc
                repeat(32) { reader.readBit() }
                repeat(48) { reader.readBit() }
            }
            if (subLayerLevelPresentFlags[i] == 1) {
                reader.readBits(8) // sub_layer_level_idc
            }
        }

        return ProfileTierLevel(
            generalProfileSpace = generalProfileSpace,
            generalTierFlag = generalTierFlag,
            generalProfileIdc = generalProfileIdc,
            generalProfileCompatibilityFlags = compatibilityFlags,
            generalConstraintIndicatorFlags = constraintFlags,
            generalLevelIdc = generalLevelIdc
        )
    }

    private fun toRbsp(nal: ByteArray): ByteArray? {
        if (nal.size <= 2) return null
        val output = ByteArrayOutputStream()
        var zeroCount = 0
        for (i in 2 until nal.size) {
            val value = nal[i]
            if (zeroCount >= 2 && value == 0x03.toByte()) {
                zeroCount = 0
                continue
            }
            output.write(value.toInt())
            zeroCount = if (value == 0.toByte()) zeroCount + 1 else 0
        }
        return output.toByteArray()
    }

    private class BitReader(private val data: ByteArray) {
        private var byteOffset = 0
        private var bitOffset = 0

        fun readBits(numBits: Int): Int {
            var bits = 0
            for (i in 0 until numBits) {
                bits = bits shl 1
                if (byteOffset >= data.size) {
                    continue
                }
                val current = data[byteOffset].toInt()
                val bit = (current shr (7 - bitOffset)) and 0x01
                bits = bits or bit
                bitOffset++
                if (bitOffset == 8) {
                    bitOffset = 0
                    byteOffset++
                }
            }
            return bits
        }

        fun readBit(): Int = readBits(1)

        fun readUE(): Int {
            var leadingZeroBits = 0
            while (true) {
                val bit = readBit()
                if (bit == 0 && leadingZeroBits < 32) {
                    leadingZeroBits++
                } else {
                    if (leadingZeroBits >= 32) {
                        return 0
                    }
                    val prefix = (1 shl leadingZeroBits) - 1
                    val suffix = if (leadingZeroBits > 0) readBits(leadingZeroBits) else 0
                    return prefix + suffix
                }
            }
        }
    }

    private data class ProfileTierLevel(
        val generalProfileSpace: Int,
        val generalTierFlag: Int,
        val generalProfileIdc: Int,
        val generalProfileCompatibilityFlags: Int,
        val generalConstraintIndicatorFlags: Long,
        val generalLevelIdc: Int
    )

    private data class HevcDecoderConfiguration(
        val generalProfileSpace: Int = 0,
        val generalTierFlag: Int = 0,
        val generalProfileIdc: Int = 1,
        val generalProfileCompatibilityFlags: Int = 0,
        val generalConstraintIndicatorFlags: Long = 0,
        val generalLevelIdc: Int = 120,
        val minSpatialSegmentationIdc: Int = 0,
        val parallelismType: Int = 0,
        val chromaFormatIdc: Int = 1,
        val bitDepthLumaMinus8: Int = 0,
        val bitDepthChromaMinus8: Int = 0,
        val avgFrameRate: Int = 0,
        val constantFrameRate: Int = 0,
        val numTemporalLayers: Int = 1,
        val temporalIdNested: Boolean = true,
        val lengthSizeMinusOne: Int = 3
    )

    private object HevcNalType {
        const val VPS = 32
        const val SPS = 33
        const val PPS = 34
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
