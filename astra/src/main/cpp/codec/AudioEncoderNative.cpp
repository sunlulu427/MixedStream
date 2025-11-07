#include "AudioEncoderNative.h"

#include <android/log.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#include <algorithm>
#include <cstring>
#include <vector>

#include "../callback/JavaCallback.h"
#include "../common/PushProxy.h"

namespace {
constexpr const char* kTag = "AudioEncoderNative";
constexpr const char* kAacMime = "audio/mp4a-latm";
constexpr const char* kCsd0Key = "csd-0";
constexpr int32_t kAacProfileLc = 2;

inline int32_t ClampBitrate(int32_t bitrateKbps) {
    return bitrateKbps > 0 ? bitrateKbps : 64;
}
}

AudioEncoderNative::AudioEncoderNative() = default;

AudioEncoderNative::~AudioEncoderNative() {
    stop();
    releaseCodec();
}

bool AudioEncoderNative::configure(const Config& config) {
    std::lock_guard<std::mutex> lock(mutex_);
    stop();
    releaseCodec();

    config_ = config;
    formatConfigured_ = false;
    totalSamples_ = 0;

    codec_ = AMediaCodec_createEncoderByType(kAacMime);
    if (!codec_) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to create AAC encoder");
        return false;
    }

    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, kAacMime);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, std::max(config.sampleRate, 8000));
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, std::max(config.channels, 1));
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, ClampBitrate(config.bitrateKbps) * 1024);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_AAC_PROFILE, kAacProfileLc);
    const int32_t maxInputSize = std::max(
            config.sampleRate * config.channels * config.bytesPerSample / 5,
            2048);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, maxInputSize);

    media_status_t status = AMediaCodec_configure(
            codec_,
            format,
            nullptr,
            nullptr,
            AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaFormat_delete(format);
    if (status != AMEDIA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "AMediaCodec_configure failed: %d", status);
        releaseCodec();
        return false;
    }

    return true;
}

void AudioEncoderNative::start() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!codec_ || running_.load()) {
        return;
    }
    if (AMediaCodec_start(codec_) != AMEDIA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to start AAC encoder");
        return;
    }
    running_.store(true);
    drainThread_ = std::thread(&AudioEncoderNative::drainLoop, this);
}

void AudioEncoderNative::stop() {
    running_.store(false);
    if (drainThread_.joinable()) {
        drainThread_.join();
    }
    if (codec_) {
        AMediaCodec_stop(codec_);
    }
    formatConfigured_ = false;
}

void AudioEncoderNative::queuePcm(const uint8_t* data, std::size_t size) {
    if (!codec_ || !running_.load() || data == nullptr || size == 0) {
        return;
    }

    std::size_t offset = 0;
    while (offset < size) {
        AMediaCodecBufferInfo info{};
        const ssize_t index = AMediaCodec_dequeueInputBuffer(codec_, 1000);
        if (index >= 0) {
            size_t bufferSize = 0;
            uint8_t* buffer = AMediaCodec_getInputBuffer(codec_, index, &bufferSize);
            if (!buffer || bufferSize == 0) {
                AMediaCodec_queueInputBuffer(codec_, index, 0, 0, 0, 0);
                continue;
            }
            const size_t remaining = size - offset;
            const size_t copyBytes = std::min(remaining, bufferSize);
            std::memcpy(buffer, data + offset, copyBytes);
            const int64_t pts = computePtsUs(copyBytes);
            AMediaCodec_queueInputBuffer(codec_,
                                         index,
                                         0,
                                         static_cast<int32_t>(copyBytes),
                                         pts,
                                         0);
            offset += copyBytes;
        } else if (index == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            break;
        } else {
            __android_log_print(ANDROID_LOG_WARN, kTag, "dequeueInputBuffer status=%zd", index);
            break;
        }
    }
}

void AudioEncoderNative::setCallback(JavaCallback* callback) {
    std::lock_guard<std::mutex> lock(mutex_);
    callback_ = callback;
}

void AudioEncoderNative::drainLoop() {
    while (true) {
        if (!codec_) {
            break;
        }
        AMediaCodecBufferInfo info{};
        const ssize_t index = AMediaCodec_dequeueOutputBuffer(codec_, &info, 10000);
        if (index >= 0) {
            size_t bufferSize = 0;
            uint8_t* buffer = AMediaCodec_getOutputBuffer(codec_, index, &bufferSize);
            if (buffer && info.size > 0 && static_cast<size_t>(info.offset + info.size) <= bufferSize) {
                PushProxy::getInstance()->pushAudioFrame(buffer + info.offset,
                                                         static_cast<size_t>(info.size),
                                                         info.presentationTimeUs);
            }
            const bool endOfStream = (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
            AMediaCodec_releaseOutputBuffer(codec_, index, false);
            if (endOfStream) {
                break;
            }
        } else if (index == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            handleFormatChange();
        } else if (index == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            if (!running_.load()) {
                break;
            }
        } else {
            __android_log_print(ANDROID_LOG_ERROR, kTag, "Unexpected dequeue status=%zd", index);
            if (!running_.load()) {
                break;
            }
        }
    }
}

void AudioEncoderNative::handleFormatChange() {
    if (formatConfigured_) {
        return;
    }
    AMediaFormat* format = AMediaCodec_getOutputFormat(codec_);
    if (!format) {
        return;
    }
    void* ascData = nullptr;
    size_t ascSize = 0;
    const bool hasAsc = AMediaFormat_getBuffer(
            format,
            kCsd0Key,
            &ascData,
            &ascSize);
    if (hasAsc && ascData != nullptr && ascSize > 0) {
        astra::AudioConfig audioConfig;
        audioConfig.sampleRate = static_cast<uint32_t>(std::max(config_.sampleRate, 8000));
        audioConfig.channels = static_cast<uint8_t>(std::max(config_.channels, 1));
        audioConfig.sampleSizeBits = static_cast<uint8_t>(config_.bytesPerSample * 8);
        audioConfig.asc.resize(ascSize);
        std::memcpy(audioConfig.asc.data(), ascData, ascSize);
        PushProxy::getInstance()->configureAudio(audioConfig);
        formatConfigured_ = true;
    }
}

void AudioEncoderNative::releaseCodec() {
    if (codec_) {
        AMediaCodec_delete(codec_);
        codec_ = nullptr;
    }
}

int64_t AudioEncoderNative::computePtsUs(std::size_t bytes) {
    const std::size_t samples = bytes / static_cast<std::size_t>(config_.bytesPerSample * std::max(config_.channels, 1));
    totalSamples_ += static_cast<int64_t>(samples);
    const int64_t sampleRate = std::max(config_.sampleRate, 1);
    return totalSamples_ * 1000000LL / sampleRate;
}
