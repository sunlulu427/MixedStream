#include "VideoEncoderNative.h"

#include <android/log.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#include <algorithm>
#include <chrono>

#include "../callback/JavaCallback.h"
#include "../common/PushProxy.h"

namespace {
constexpr const char* kTag = "VideoEncoderNative";
constexpr int32_t kColorFormatSurface = 0x7F000789;
constexpr int32_t kBitrateModeCbr = 2;
constexpr int32_t kAvcProfileHigh = 0x08;
constexpr int32_t kAvcLevel4 = 0x200;
constexpr int32_t kHevcProfileMain = 1;
constexpr const char* kMimeAvc = "video/avc";
constexpr const char* kMimeHevc = "video/hevc";
constexpr const char* kKeyLevel = "level";
constexpr const char* kKeyVideoBitrate = "video-bitrate";
constexpr const char* kKeyBitrateMode = "bitrate-mode";
constexpr const char* kKeyProfile = "profile";

inline int32_t ClampBitrate(int32_t bitrateKbps) {
    return bitrateKbps > 0 ? bitrateKbps : 600;
}

inline uint32_t SanitizeDimension(uint32_t value) {
    if (value == 0) {
        return 16;
    }
    return ((value + 15) / 16) * 16;
}

const char* MimeForCodec(astra::VideoCodecId codec) {
    return codec == astra::VideoCodecId::kH265 ? kMimeHevc : kMimeAvc;
}

}  // namespace

VideoEncoderNative::VideoEncoderNative() = default;

VideoEncoderNative::~VideoEncoderNative() {
    stop();
    releaseSurface();
    releaseCodec();
}

bool VideoEncoderNative::configure(const Config& config) {
    std::lock_guard<std::mutex> lock(mutex_);
    stop();
    releaseSurface();
    releaseCodec();

    config_ = config;
    formatConfigured_ = false;

    const char* mime = MimeForCodec(config.streamConfig.codec);
    codec_ = AMediaCodec_createEncoderByType(mime);
    if (!codec_) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to create codec for %s", mime);
        return false;
    }

    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, static_cast<int32_t>(SanitizeDimension(config.streamConfig.width)));
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, static_cast<int32_t>(SanitizeDimension(config.streamConfig.height)));
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, kColorFormatSurface);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, ClampBitrate(config.bitrateKbps) * 1024);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, std::max(config.streamConfig.fps, 1u));
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, std::max(config.iframeInterval, 1));
    AMediaFormat_setInt32(format, kKeyBitrateMode, kBitrateModeCbr);

    if (config.streamConfig.codec == astra::VideoCodecId::kH265) {
        AMediaFormat_setInt32(format, kKeyProfile, kHevcProfileMain);
    } else {
        AMediaFormat_setInt32(format, kKeyProfile, kAvcProfileHigh);
        AMediaFormat_setInt32(format, kKeyLevel, kAvcLevel4);
    }

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

    ANativeWindow* createdSurface = nullptr;
    status = AMediaCodec_createInputSurface(codec_, &createdSurface);
    if (status != AMEDIA_OK || createdSurface == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to create input surface status=%d", status);
        releaseCodec();
        return false;
    }
    inputSurface_ = createdSurface;

    PushProxy::getInstance()->configureVideo(config_.streamConfig);
    return true;
}

jobject VideoEncoderNative::createInputSurface(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!inputSurface_) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Input surface requested before configure");
        return nullptr;
    }
    return ANativeWindow_toSurface(env, inputSurface_);
}

void VideoEncoderNative::releaseSurface() {
    if (inputSurface_) {
        ANativeWindow_release(inputSurface_);
        inputSurface_ = nullptr;
    }
}

void VideoEncoderNative::start() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!codec_ || running_.load()) {
        return;
    }
    if (AMediaCodec_start(codec_) != AMEDIA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to start codec");
        return;
    }
    running_.store(true);
    const auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    stats_.reset(nowMs);
    drainThread_ = std::thread(&VideoEncoderNative::drainLoop, this);
}

void VideoEncoderNative::stop() {
    running_.store(false);
    if (codec_) {
        AMediaCodec_signalEndOfInputStream(codec_);
    }
    if (drainThread_.joinable()) {
        drainThread_.join();
    }
    if (codec_) {
        AMediaCodec_stop(codec_);
    }
    formatConfigured_ = false;
}

void VideoEncoderNative::updateBitrate(int32_t bitrateKbps) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!codec_) return;
    AMediaFormat* params = AMediaFormat_new();
    AMediaFormat_setInt32(params, kKeyVideoBitrate, ClampBitrate(bitrateKbps) * 1024);
    AMediaCodec_setParameters(codec_, params);
    AMediaFormat_delete(params);
}

void VideoEncoderNative::setCallback(JavaCallback* callback) {
    std::lock_guard<std::mutex> lock(mutex_);
    callback_ = callback;
}

void VideoEncoderNative::drainLoop() {
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
                PushProxy::getInstance()->pushVideoFrame(buffer + info.offset,
                                                         static_cast<size_t>(info.size),
                                                         info.presentationTimeUs);
                signalStats(static_cast<std::size_t>(info.size));
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

void VideoEncoderNative::handleFormatChange() {
    if (formatConfigured_) {
        return;
    }
    AMediaFormat* format = AMediaCodec_getOutputFormat(codec_);
    if (format) {
        const char* formatString = AMediaFormat_toString(format);
        __android_log_print(ANDROID_LOG_INFO, kTag, "Video output format: %s", formatString ? formatString : "null");
    }
    formatConfigured_ = true;
}

void VideoEncoderNative::signalStats(std::size_t bytes) {
    const auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    const auto result = stats_.onSample(bytes, nowMs);
    if (result.valid && callback_) {
        callback_->onStats(result.bitrateKbps, result.fps);
    }
}

void VideoEncoderNative::releaseCodec() {
    if (codec_) {
        AMediaCodec_delete(codec_);
        codec_ = nullptr;
    }
}
