#include "PushProxy.h"

#include <android/log.h>
#include <string>

namespace {
constexpr const char* kTag = "PushProxy";

std::string MaskUrl(const char* url) {
    if (url == nullptr) {
        return "null";
    }
    std::string value(url);
    const auto separator = value.find_last_of('/');
    if (separator == std::string::npos || separator >= value.size() - 1) {
        return value;
    }
    std::string suffix = value.substr(separator + 1);
    std::string masked;
    if (suffix.size() <= 2) {
        masked = std::string(suffix.size(), '*');
    } else if (suffix.size() <= 4) {
        masked = suffix.substr(0, 1) + std::string("***");
    } else {
        masked = suffix.substr(0, 2) + std::string("***") + suffix.substr(suffix.size() - 2);
    }
    return value.substr(0, separator + 1) + masked;
}
}  // namespace

IPush* PushProxy::getPushEngine() {
    return rtmpPush;
}

PushProxy::PushProxy() = default;

PushProxy* PushProxy::getInstance() {
    static PushProxy proxy;
    return &proxy;
}

void PushProxy::init(const char* url, JavaCallback** callback) {
    __android_log_print(ANDROID_LOG_INFO,
                        kTag,
                        "init url=%s callback=%p",
                        MaskUrl(url).c_str(),
                        callback ? *callback : nullptr);
    if (rtmpPush) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "releasing existing rtmpPush=%p", rtmpPush);
        rtmpPush->stop();
        delete rtmpPush;
        rtmpPush = nullptr;
    }
    if (javaCallback) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "clearing previous javaCallback=%p", javaCallback);
        delete javaCallback;
        javaCallback = nullptr;
    }

    javaCallback = callback ? *callback : nullptr;
    rtmpPush = new RTMPPush(url, callback);
    __android_log_print(ANDROID_LOG_INFO, kTag, "rtmpPush created=%p", rtmpPush);

    if (pendingVideoConfig.has_value()) {
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "applying pending video config after init");
        rtmpPush->configureVideo(pendingVideoConfig.value());
    }
    if (pendingAudioConfig.has_value()) {
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "applying pending audio config after init");
        rtmpPush->configureAudio(pendingAudioConfig.value());
    }
}

void PushProxy::configureVideo(const astra::VideoConfig& config) {
    pendingVideoConfig = config;
    __android_log_print(ANDROID_LOG_INFO,
                        kTag,
                        "configureVideo -> %ux%u@%u codec=%d",
                        config.width,
                        config.height,
                        config.fps,
                        static_cast<int>(config.codec));
    if (auto* engine = getPushEngine()) {
        engine->configureVideo(config);
    }
}

void PushProxy::configureAudio(const astra::AudioConfig& config) {
    pendingAudioConfig = config;
    __android_log_print(ANDROID_LOG_INFO,
                        kTag,
                        "configureAudio -> sampleRate=%u channels=%u sampleBits=%u asc=%zu",
                        config.sampleRate,
                        config.channels,
                        config.sampleSizeBits,
                        config.asc.size());
    if (auto* engine = getPushEngine()) {
        engine->configureAudio(config);
    }
}

void PushProxy::start() {
    auto* engine = getPushEngine();
    if (engine) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "start engine=%p", engine);
        engine->start();
    } else {
        __android_log_print(ANDROID_LOG_WARN, kTag, "start requested but engine unavailable");
    }
}

void PushProxy::stop() {
    auto* engine = getPushEngine();
    if (engine) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "stop engine=%p", engine);
        engine->stop();
        delete engine;
        rtmpPush = nullptr;
    }
    if (javaCallback) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "release javaCallback=%p", javaCallback);
        delete javaCallback;
        javaCallback = nullptr;
    }
}

void PushProxy::pushVideoFrame(const uint8_t* data, size_t length, int64_t pts) {
    if (auto* engine = getPushEngine()) {
        engine->pushVideoFrame(data, length, pts);
    } else {
        __android_log_print(ANDROID_LOG_WARN, kTag, "drop video frame length=%zu pts=%lld: engine missing", length, static_cast<long long>(pts));
    }
}

void PushProxy::pushAudioFrame(const uint8_t* data, size_t length, int64_t pts) {
    if (auto* engine = getPushEngine()) {
        engine->pushAudioFrame(data, length, pts);
    } else {
        __android_log_print(ANDROID_LOG_WARN, kTag, "drop audio frame length=%zu pts=%lld: engine missing", length, static_cast<long long>(pts));
    }
}
