#include "NativeStreamEngine.h"

#include <android/log.h>

#include "../callback/JavaCallback.h"

namespace {
constexpr const char* kTag = "NativeStreamEngine";
}

NativeStreamEngine& NativeStreamEngine::Instance() {
    static NativeStreamEngine engine;
    return engine;
}

void NativeStreamEngine::setCallback(JavaCallback* callback) {
    std::lock_guard<std::mutex> lock(mutex_);
    callback_ = callback;
    if (video_) {
        video_->setCallback(callback_);
    }
    if (audio_) {
        audio_->setCallback(callback_);
    }
}

jobject NativeStreamEngine::prepareVideoSurface(JNIEnv* env,
                                               const astra::VideoConfig& config,
                                               int32_t bitrateKbps,
                                               int32_t iframeInterval) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!video_) {
        video_ = std::make_unique<VideoEncoderNative>();
        video_->setCallback(callback_);
    }
    VideoEncoderNative::Config encoderConfig{};
    encoderConfig.streamConfig = config;
    encoderConfig.bitrateKbps = bitrateKbps;
    encoderConfig.iframeInterval = iframeInterval;
    if (!video_->configure(encoderConfig)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Video encoder configure failed");
        video_.reset();
        return nullptr;
    }
    return video_->createInputSurface(env);
}

void NativeStreamEngine::releaseVideoSurface() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (video_) {
        video_->stop();
        video_->releaseSurface();
        video_.reset();
    }
}

void NativeStreamEngine::startVideo() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (video_) {
        video_->start();
    }
}

void NativeStreamEngine::stopVideo() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (video_) {
        video_->stop();
    }
}

void NativeStreamEngine::updateVideoBitrate(int32_t bitrateKbps) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (video_) {
        video_->updateBitrate(bitrateKbps);
    }
}

void NativeStreamEngine::configureAudioEncoder(int32_t sampleRate,
                                               int32_t channels,
                                               int32_t bitrateKbps,
                                               int32_t bytesPerSample) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!audio_) {
        audio_ = std::make_unique<AudioEncoderNative>();
        audio_->setCallback(callback_);
    }
    AudioEncoderNative::Config config{};
    config.sampleRate = sampleRate;
    config.channels = channels;
    config.bitrateKbps = bitrateKbps;
    config.bytesPerSample = bytesPerSample <= 0 ? 2 : bytesPerSample;
    if (!audio_->configure(config)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Audio encoder configure failed");
        audio_.reset();
    }
}

void NativeStreamEngine::startAudio() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (audio_) {
        audio_->start();
    }
}

void NativeStreamEngine::stopAudio() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (audio_) {
        audio_->stop();
    }
}

void NativeStreamEngine::pushAudioPcm(const uint8_t* data, std::size_t size) {
    AudioEncoderNative* audio = nullptr;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        audio = audio_.get();
    }
    if (audio) {
        audio->queuePcm(data, size);
    }
}

void NativeStreamEngine::shutdown() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (video_) {
        video_->stop();
        video_->releaseSurface();
        video_.reset();
    }
    if (audio_) {
        audio_->stop();
        audio_.reset();
    }
    callback_ = nullptr;
}
