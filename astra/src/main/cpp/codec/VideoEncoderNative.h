#ifndef ASTRASTREAM_VIDEOENCODERNATIVE_H
#define ASTRASTREAM_VIDEOENCODERNATIVE_H

#include <jni.h>
#include <android/native_window_jni.h>
#include <media/NdkMediaCodec.h>

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <thread>

#include "../stream/FlvMuxer.h"
#include "../stream/FrameStats.h"

class JavaCallback;

class VideoEncoderNative {
public:
    struct Config {
        astra::VideoConfig streamConfig;
        int32_t bitrateKbps = 0;
        int32_t iframeInterval = 2;
    };

    VideoEncoderNative();
    ~VideoEncoderNative();

    bool configure(const Config& config);
    jobject createInputSurface(JNIEnv* env);
    void releaseSurface();
    void start();
    void stop();
    void updateBitrate(int32_t bitrateKbps);
    void setCallback(JavaCallback* callback);

private:
    void drainLoop();
    void handleFormatChange();
    void releaseCodec();
    void signalStats(std::size_t bytes);

    Config config_{};
    AMediaCodec* codec_ = nullptr;
    ANativeWindow* inputSurface_ = nullptr;
    std::thread drainThread_;
    std::atomic<bool> running_{false};
    std::mutex mutex_;
    bool formatConfigured_ = false;
    JavaCallback* callback_ = nullptr;
    astra::FrameStats stats_;
};

#endif  // ASTRASTREAM_VIDEOENCODERNATIVE_H
