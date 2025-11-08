#include "NativeAudioCapturer.h"

#include <android/log.h>

#include <cstring>

#include "../codec/NativeStreamEngine.h"

namespace {
constexpr const char* kTag = "NativeAudioCapturer";
}

NativeAudioCapturer& NativeAudioCapturer::Instance() {
    static NativeAudioCapturer capturer;
    return capturer;
}

bool NativeAudioCapturer::configure(int32_t sampleRate,
                                    int32_t channels,
                                    int32_t bytesPerSample) {
    if (sampleRate < 8000) sampleRate = 8000;
    if (channels <= 0) channels = 1;
    if (bytesPerSample != 1 && bytesPerSample != 2 && bytesPerSample != 4) {
        bytesPerSample = 2;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    return configureStreamLocked(sampleRate, channels, bytesPerSample);
}

bool NativeAudioCapturer::configureStreamLocked(int32_t sampleRate,
                                                int32_t channels,
                                                int32_t bytesPerSample) {
    if (stream_) {
        AAudioStream_requestStop(stream_);
        AAudioStream_close(stream_);
        stream_ = nullptr;
    }

    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK || !builder) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to create stream builder");
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, channels);
    if (bytesPerSample == 4) {
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    } else if (bytesPerSample == 1) {
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        bytesPerSample = 2;
    } else {
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    }
    AAudioStreamBuilder_setDataCallback(builder, &NativeAudioCapturer::DataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder, &NativeAudioCapturer::ErrorCallback, this);

    const aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream_);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK || !stream_) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "AAudio open stream failed %d", result);
        stream_ = nullptr;
        return false;
    }

    sampleRate_ = sampleRate;
    channelCount_ = channels;
    bytesPerSample_ = bytesPerSample;
    capturing_.store(false);
    return true;
}

bool NativeAudioCapturer::start() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!stream_) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "start called before configure");
        return false;
    }
    if (capturing_.load()) {
        return true;
    }
    const aaudio_result_t result = AAudioStream_requestStart(stream_);
    if (result != AAUDIO_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "AAudioStream_requestStart failed %d", result);
        return false;
    }
    capturing_.store(true);
    return true;
}

void NativeAudioCapturer::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (stream_) {
        AAudioStream_requestStop(stream_);
    }
    capturing_.store(false);
}

void NativeAudioCapturer::release() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (stream_) {
        AAudioStream_close(stream_);
        stream_ = nullptr;
    }
    capturing_.store(false);
}

void NativeAudioCapturer::setMute(bool muted) {
    muted_.store(muted);
}

size_t NativeAudioCapturer::frameSizeBytes() const {
    return static_cast<size_t>(channelCount_) * static_cast<size_t>(bytesPerSample_);
}

aaudio_data_callback_result_t NativeAudioCapturer::DataCallback(
        AAudioStream*,
        void* userData,
        void* audioData,
        int32_t numFrames) {
    auto* self = static_cast<NativeAudioCapturer*>(userData);
    if (!self || !audioData || numFrames <= 0) {
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    const size_t frameBytes = self->frameSizeBytes();
    const size_t totalBytes = frameBytes * static_cast<size_t>(numFrames);
    if (self->muted_.load()) {
        std::memset(audioData, 0, totalBytes);
    }
    NativeStreamEngine::Instance().pushAudioPcm(
            static_cast<uint8_t*>(audioData),
            totalBytes);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void NativeAudioCapturer::ErrorCallback(
        AAudioStream*,
        void* userData,
        aaudio_result_t error) {
    auto* self = static_cast<NativeAudioCapturer*>(userData);
    __android_log_print(ANDROID_LOG_ERROR, kTag, "AAudio error callback %d", error);
    if (self) {
        self->capturing_.store(false);
    }
}
