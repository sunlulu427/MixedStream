#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <cstddef>
#include <jni.h>
#include <vector>

#include "PushProxy.h"

namespace {
JavaVM* gJavaVM = nullptr;
}

jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    gJavaVM = vm;
    __android_log_print(ANDROID_LOG_DEBUG, "native_rtmp_push", "JNI_OnLoad: %p", vm);
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_nativeConnect(
        JNIEnv* env, jobject thiz, jstring url) {
    const char* rtmpUrl = env->GetStringUTFChars(url, nullptr);
    auto* callback = new JavaCallback(gJavaVM, env, thiz);
    PushProxy::getInstance()->init(rtmpUrl, &callback);
    PushProxy::getInstance()->start();
    env->ReleaseStringUTFChars(url, rtmpUrl);
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_nativeClose(
        JNIEnv*, jobject) {
    PushProxy::getInstance()->stop();
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_nativeConfigureVideo(
        JNIEnv*, jobject, jint width, jint height, jint fps, jint codecOrdinal) {
    astra::VideoConfig config;
    config.width = static_cast<uint32_t>(std::max(0, width));
    config.height = static_cast<uint32_t>(std::max(0, height));
    config.fps = static_cast<uint32_t>(std::max(0, fps));
    config.codec = codecOrdinal == 0 ? astra::VideoCodecId::kH264 : astra::VideoCodecId::kH265;
    PushProxy::getInstance()->configureVideo(config);
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_nativeConfigureAudio(
        JNIEnv* env, jobject, jint sampleRate, jint channels, jint sampleSizeBits, jbyteArray asc) {
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
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_nativePushVideoFrame(
        JNIEnv* env, jobject, jobject buffer, jint offset, jint size, jlong pts) {
    if (buffer == nullptr || size <= 0) {
        return;
    }
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (!base) {
        return;
    }
    PushProxy::getInstance()->pushVideoFrame(base + offset, static_cast<size_t>(size), static_cast<int64_t>(pts));
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_nativePushAudioFrame(
        JNIEnv* env, jobject, jobject buffer, jint offset, jint size, jlong pts) {
    if (buffer == nullptr || size <= 0) {
        return;
    }
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (!base) {
        return;
    }
    PushProxy::getInstance()->pushAudioFrame(base + offset, static_cast<size_t>(size), static_cast<int64_t>(pts));
}

}  // extern "C"
