#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <cstddef>
#include <jni.h>
#include <string>
#include <vector>

#include "PushProxy.h"
#include "../codec/NativeStreamEngine.h"

namespace {
JavaVM* gJavaVM = nullptr;
constexpr const char* kTag = "native_rtmp_push";

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
}

jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    gJavaVM = vm;
    __android_log_print(ANDROID_LOG_DEBUG, kTag, "JNI_OnLoad: %p", vm);
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeCreateSender(
        JNIEnv*, jclass, jlong handle, jint protocolOrdinal) {
    __android_log_print(ANDROID_LOG_DEBUG,
                        kTag,
                        "nativeCreateSender handle=%lld protocol=%d",
                        static_cast<long long>(handle),
                        protocolOrdinal);
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeDestroySender(
        JNIEnv*, jclass, jlong handle) {
    __android_log_print(ANDROID_LOG_DEBUG,
                        kTag,
                        "nativeDestroySender handle=%lld",
                        static_cast<long long>(handle));
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeConnect(
        JNIEnv* env, jclass, jlong handle, jobject callback_proxy, jstring url) {
    const char* rtmpUrl = env->GetStringUTFChars(url, nullptr);
    __android_log_print(ANDROID_LOG_INFO,
                        kTag,
                        "nativeConnect handle=%lld url=%s",
                        static_cast<long long>(handle),
                        MaskUrl(rtmpUrl).c_str());
    auto* callback = new JavaCallback(gJavaVM, env, callback_proxy);
    PushProxy::getInstance()->init(rtmpUrl, &callback);
    NativeStreamEngine::Instance().setCallback(callback);
    PushProxy::getInstance()->start();
    env->ReleaseStringUTFChars(url, rtmpUrl);
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeClose(
        JNIEnv*, jclass, jlong handle) {
    __android_log_print(ANDROID_LOG_INFO,
                        kTag,
                        "nativeClose invoked handle=%lld",
                        static_cast<long long>(handle));
    NativeStreamEngine::Instance().shutdown();
    NativeStreamEngine::Instance().setCallback(nullptr);
    PushProxy::getInstance()->stop();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeConfigureVideo(
        JNIEnv*, jclass, jlong handle, jint width, jint height, jint fps, jint codecOrdinal) {
    __android_log_print(ANDROID_LOG_DEBUG,
                        kTag,
                        "nativeConfigureVideo handle=%lld width=%d height=%d fps=%d codec=%d",
                        static_cast<long long>(handle),
                        width,
                        height,
                        fps,
                        codecOrdinal);
    astra::VideoConfig config;
    config.width = static_cast<uint32_t>(std::max(0, width));
    config.height = static_cast<uint32_t>(std::max(0, height));
    config.fps = static_cast<uint32_t>(std::max(0, fps));
    config.codec = codecOrdinal == 0 ? astra::VideoCodecId::kH264 : astra::VideoCodecId::kH265;
    PushProxy::getInstance()->configureVideo(config);
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeConfigureAudio(
        JNIEnv* env,
        jclass,
        jlong handle,
        jint sampleRate,
        jint channels,
        jint sampleSizeBits,
        jbyteArray asc) {
    __android_log_print(ANDROID_LOG_DEBUG,
                        kTag,
                        "nativeConfigureAudio handle=%lld sampleRate=%d channels=%d sampleSizeBits=%d ascBytes=%d",
                        static_cast<long long>(handle),
                        sampleRate,
                        channels,
                        sampleSizeBits,
                        asc ? env->GetArrayLength(asc) : 0);
    astra::AudioConfig config;
    config.sampleRate = static_cast<uint32_t>(std::max(0, sampleRate));
    config.channels = static_cast<uint8_t>(std::max(0, channels));
    config.sampleSizeBits = static_cast<uint8_t>(std::max(0, sampleSizeBits));
    if (asc != nullptr) {
        const jsize length = env->GetArrayLength(asc);
        config.asc.resize(length);
        if (length > 0) {
            env->GetByteArrayRegion(asc, 0, length, reinterpret_cast<jbyte*>(config.asc.data()));
        }
    }
    PushProxy::getInstance()->configureAudio(config);
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativePushVideoFrame(
        JNIEnv* env, jclass, jlong /*handle*/, jobject buffer, jint offset, jint size, jlong pts) {
    if (buffer == nullptr || size <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "nativePushVideoFrame skipped buffer=%p size=%d", buffer, size);
        return;
    }
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (!base) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "nativePushVideoFrame GetDirectBufferAddress failed");
        return;
    }
    PushProxy::getInstance()->pushVideoFrame(base + offset, static_cast<size_t>(size), static_cast<int64_t>(pts));
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativePushAudioFrame(
        JNIEnv* env, jclass, jlong /*handle*/, jobject buffer, jint offset, jint size, jlong pts) {
    if (buffer == nullptr || size <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "nativePushAudioFrame skipped buffer=%p size=%d", buffer, size);
        return;
    }
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (!base) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "nativePushAudioFrame GetDirectBufferAddress failed");
        return;
    }
    PushProxy::getInstance()->pushAudioFrame(base + offset, static_cast<size_t>(size), static_cast<int64_t>(pts));
}

}  // extern "C"
