#ifndef ASTRASTREAM_PUSHPROXY_H
#define ASTRASTREAM_PUSHPROXY_H

#include <cstdint>

#include "../push/RTMPPush.h"
#include "IPush.h"

class PushProxy {
public:
    static PushProxy* getInstance();

    void init(const char* url, JavaCallback** javaCallback);
    void configureVideo(const astra::VideoConfig& config);
    void configureAudio(const astra::AudioConfig& config);
    void start();
    void stop();
    void pushVideoFrame(const uint8_t* data, size_t length, int64_t pts);
    void pushAudioFrame(const uint8_t* data, size_t length, int64_t pts);

private:
    PushProxy();

    IPush* getPushEngine();

    RTMPPush* rtmpPush = nullptr;
    JavaCallback* javaCallback = nullptr;
};

#endif  // ASTRASTREAM_PUSHPROXY_H
