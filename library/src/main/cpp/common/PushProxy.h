#ifndef RTMPPUSH_PUSHPROXY_H
#define RTMPPUSH_PUSHPROXY_H

#include <cstdint>

#include "../push/RTMPPush.h"
#include "IPush.h"

class PushProxy {
public:
    static PushProxy* getInstance();

    void init(const char* url, JavaCallback** javaCallback);
    void start();
    void stop();
    void pushSpsPps(uint8_t* sps, int sps_len, uint8_t* pps, int pps_len);
    void pushAudioData(uint8_t* audio, int len, int type);
    void pushVideoData(uint8_t* video, int len, int keyframe);

private:
    PushProxy();

    IPush* getPushEngine();

    RTMPPush* rtmpPush = nullptr;
    JavaCallback* javaCallback = nullptr;
};

#endif  // RTMPPUSH_PUSHPROXY_H
