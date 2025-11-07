#ifndef ASTRASTREAM_NATIVESTREAMENGINE_H
#define ASTRASTREAM_NATIVESTREAMENGINE_H

#include <jni.h>

#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>

#include "AudioEncoderNative.h"
#include "VideoEncoderNative.h"

class JavaCallback;

class NativeStreamEngine {
public:
    static NativeStreamEngine& Instance();

    void setCallback(JavaCallback* callback);

    jobject prepareVideoSurface(JNIEnv* env,
                                const astra::VideoConfig& config,
                                int32_t bitrateKbps,
                                int32_t iframeInterval);
    void releaseVideoSurface();
    void startVideo();
    void stopVideo();
    void updateVideoBitrate(int32_t bitrateKbps);

    void configureAudioEncoder(int32_t sampleRate,
                               int32_t channels,
                               int32_t bitrateKbps,
                               int32_t bytesPerSample);
    void startAudio();
    void stopAudio();
    void pushAudioPcm(const uint8_t* data, std::size_t size);

    void shutdown();

private:
    NativeStreamEngine() = default;
    NativeStreamEngine(const NativeStreamEngine&) = delete;
    NativeStreamEngine& operator=(const NativeStreamEngine&) = delete;

    std::mutex mutex_;
    std::unique_ptr<VideoEncoderNative> video_;
    std::unique_ptr<AudioEncoderNative> audio_;
    JavaCallback* callback_ = nullptr;
};

#endif  // ASTRASTREAM_NATIVESTREAMENGINE_H
