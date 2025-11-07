#ifndef ASTRASTREAM_AUDIOENCODERNATIVE_H
#define ASTRASTREAM_AUDIOENCODERNATIVE_H

#include <jni.h>
#include <media/NdkMediaCodec.h>

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <thread>

class JavaCallback;

class AudioEncoderNative {
public:
    struct Config {
        int32_t sampleRate = 44100;
        int32_t channels = 1;
        int32_t bitrateKbps = 64;
        int32_t bytesPerSample = 2;
    };

    AudioEncoderNative();
    ~AudioEncoderNative();

    bool configure(const Config& config);
    void start();
    void stop();
    void queuePcm(const uint8_t* data, std::size_t size);
    void setCallback(JavaCallback* callback);

private:
    void drainLoop();
    void handleFormatChange();
    void releaseCodec();
    int64_t computePtsUs(std::size_t bytes);

    Config config_{};
    AMediaCodec* codec_ = nullptr;
    std::thread drainThread_;
    std::atomic<bool> running_{false};
    std::mutex mutex_;
    bool formatConfigured_ = false;
    int64_t totalSamples_ = 0;
    JavaCallback* callback_ = nullptr;
};

#endif  // ASTRASTREAM_AUDIOENCODERNATIVE_H
