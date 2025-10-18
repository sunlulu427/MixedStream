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

void PushProxy::configureVideo(const astra::VideoConfig& config) {
    if (auto* engine = getPushEngine()) {
        engine->configureVideo(config);
    }
}

void PushProxy::configureAudio(const astra::AudioConfig& config) {
    if (auto* engine = getPushEngine()) {
        engine->configureAudio(config);
    }
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

void PushProxy::pushVideoFrame(const uint8_t* data, size_t length, int64_t pts) {
    if (auto* engine = getPushEngine()) {
        engine->pushVideoFrame(data, length, pts);
    }
}

void PushProxy::pushAudioFrame(const uint8_t* data, size_t length, int64_t pts) {
    if (auto* engine = getPushEngine()) {
        engine->pushAudioFrame(data, length, pts);
    }
}
