#ifndef ASTRASTREAM_RTMPPUSH_H
#define ASTRASTREAM_RTMPPUSH_H

#include <cstdint>

#include "AVQueue.h"
#include "IPush.h"
#include "JavaCallback.h"
#include "../stream/FlvMuxer.h"

#include <android/log.h>

#define TAG "astra"

#define LOG_SHOW true

#define LOGD(FORMAT, ...) __android_log_print(ANDROID_LOG_DEBUG, TAG, FORMAT, ##__VA_ARGS__);
#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, TAG, FORMAT, ##__VA_ARGS__);

class RTMPPush : public IPush {
public:
    RTMPPush(const char* url, JavaCallback** javaCallback);
    ~RTMPPush() override;

    void start() override;
    void stop() override;
    void main() override;
    void configureVideo(const astra::VideoConfig& config) override;
    void configureAudio(const astra::AudioConfig& config) override;
    void pushVideoFrame(const uint8_t* data, size_t length, int64_t pts) override;
    void pushAudioFrame(const uint8_t* data, size_t length, int64_t pts) override;

    void onConnecting();
    void release();

private:
    void enqueuePacket(const uint8_t* data, size_t length, uint8_t packetType, uint32_t timestamp, uint8_t channel);
    void ensureHeaders();

    astra::FlvMuxer muxer_;
    RTMP* mRtmp = nullptr;
    char* mRtmpUrl = nullptr;
    AVQueue* mQueue = nullptr;
    JavaCallback* mCallback = nullptr;
    int isPusher = 0;
    long mStartTime = 0;
    uint32_t lastVideoTimestamp_ = 0;
    uint32_t lastAudioTimestamp_ = 0;
    bool headersRequested_ = false;
};

#endif  // ASTRASTREAM_RTMPPUSH_H
