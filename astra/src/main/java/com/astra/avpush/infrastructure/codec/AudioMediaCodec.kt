package com.astra.avpush.infrastructure.codec

import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.astra.avpush.domain.config.AudioConfiguration
import java.nio.ByteBuffer

class AudioMediaCodec {
    companion object {

        fun selectCodec(mimeType: String): MediaCodecInfo? {
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) {
                    continue
                }
                val types = codecInfo.supportedTypes
                for (j in types.indices) {
                    if (types[j].equals(mimeType, ignoreCase = true)) {
                        return codecInfo
                    }
                }
            }
            return null
        }


        fun getAudioMediaCodec(configuration: AudioConfiguration): MediaCodec? {
            //数据类型  "audio/mp4a-latm"
            val format =
                MediaFormat.createAudioFormat(configuration.mime, configuration.sampleRate, configuration.channelCount)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, configuration.aacProfile)
            //比特率
            format.setInteger(MediaFormat.KEY_BIT_RATE, configuration.maxBps * 1024)
            //采样率
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, configuration.sampleRate)
            //缓冲区大小
            val maxInputSize = AudioRecord.getMinBufferSize(
                configuration.sampleRate,
                configuration.channelCount,
                configuration.encoding
            )
            //最大的缓冲区
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize)
            //通道数量
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, configuration.channelCount)
            var mediaCodec: MediaCodec? = null
            try {
                if (configuration.codeType == AudioConfiguration.CodeType.ENCODE) {
                    mediaCodec = MediaCodec.createEncoderByType(configuration.mime)
                    mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                } else if (configuration.codeType == AudioConfiguration.CodeType.DECODE) {
                    //用来标记AAC是否有adts头，1->有
                    format.setInteger(MediaFormat.KEY_IS_ADTS, configuration.adts);
                    //ByteBuffer key（暂时不了解该参数的含义，但必须设置）
                    val data = byteArrayOf(0x11.toByte(), 0x90.toByte())
                    val csd_0 = ByteBuffer.wrap(data)
                    //配置解码器 csd-0 信息
                    //参考：https://developer.android.com/reference/android/media/MediaCodec
                    format.setByteBuffer("csd-0", csd_0)
                    mediaCodec = MediaCodec.createDecoderByType(configuration.mime)
                    mediaCodec.configure(format, null, null, 0)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (mediaCodec != null) {
                    mediaCodec.stop()
                    mediaCodec.release()
                    mediaCodec = null
                }
            }
            return mediaCodec
        }
    }
}
