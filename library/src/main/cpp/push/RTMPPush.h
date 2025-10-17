#ifndef RTMPPUSH_RTMPPUSH_H
#define RTMPPUSH_RTMPPUSH_H

#include <cstdint>

#include "AVQueue.h"
#include "IPush.h"
#include "JavaCallback.h"

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
    void pushSpsPps(uint8_t* sps, int sps_len, uint8_t* pps, int pps_len) override;
    void pushAudioData(uint8_t* audio, int len, int type) override;
    void pushVideoData(uint8_t* video, int len, int type) override;

    void onConnecting();
    void release();

private:
    RTMP* mRtmp = nullptr;
    char* mRtmpUrl = nullptr;
    AVQueue* mQueue = nullptr;
    JavaCallback* mCallback = nullptr;
    int isPusher = 0;
    long mStartTime = 0;
};

#endif  // RTMPPUSH_RTMPPUSH_H
