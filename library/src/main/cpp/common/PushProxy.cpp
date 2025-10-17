#include "PushProxy.h"

IPush* PushProxy::getPushEngine() {
    return rtmpPush;
}

PushProxy::PushProxy() = default;

PushProxy* PushProxy::getInstance() {
    static PushProxy proxy;
    return &proxy;
}

void PushProxy::init(const char* url, JavaCallback** callback) {
    if (rtmpPush) {
        rtmpPush->stop();
        delete rtmpPush;
        rtmpPush = nullptr;
    }
    if (javaCallback) {
        delete javaCallback;
        javaCallback = nullptr;
    }

    javaCallback = callback ? *callback : nullptr;
    rtmpPush = new RTMPPush(url, callback);
}

void PushProxy::start() {
    auto* engine = getPushEngine();
    if (engine) {
        engine->start();
    }
}

void PushProxy::stop() {
    auto* engine = getPushEngine();
    if (engine) {
        engine->stop();
        delete engine;
        rtmpPush = nullptr;
    }
    if (javaCallback) {
        delete javaCallback;
        javaCallback = nullptr;
    }
}

void PushProxy::pushSpsPps(uint8_t* sps, int sps_len, uint8_t* pps, int pps_len) {
    auto* engine = getPushEngine();
    if (engine) {
        engine->pushSpsPps(sps, sps_len, pps, pps_len);
    }
}

void PushProxy::pushVideoData(uint8_t* video, int len, int keyframe) {
    auto* engine = getPushEngine();
    if (engine) {
        engine->pushVideoData(video, len, keyframe);
    }
}

void PushProxy::pushAudioData(uint8_t* audio, int len, int type) {
    auto* engine = getPushEngine();
    if (engine) {
        engine->pushAudioData(audio, len, type);
    }
}
