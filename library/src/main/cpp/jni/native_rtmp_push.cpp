#include <android/log.h>
#include <cstdint>
#include <jni.h>

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
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_NativeRtmpConnect(
        JNIEnv* env, jobject thiz, jstring url) {
    const char* rtmpUrl = env->GetStringUTFChars(url, nullptr);
    auto* callback = new JavaCallback(gJavaVM, env, thiz);
    PushProxy::getInstance()->init(rtmpUrl, &callback);
    PushProxy::getInstance()->start();
    env->ReleaseStringUTFChars(url, rtmpUrl);
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_NativeRtmpClose(
        JNIEnv*, jobject) {
    PushProxy::getInstance()->stop();
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_pushAudio(
        JNIEnv* env, jobject /*thiz*/, jbyteArray audio, jint size, jint type) {
    jbyte* audioData = env->GetByteArrayElements(audio, nullptr);
    PushProxy::getInstance()->pushAudioData(reinterpret_cast<uint8_t*>(audioData), size, type);
    env->ReleaseByteArrayElements(audio, audioData, 0);
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_pushVideo(
        JNIEnv* env, jobject /*thiz*/, jbyteArray video, jint size, jint type) {
    jbyte* videoData = env->GetByteArrayElements(video, nullptr);
    PushProxy::getInstance()->pushVideoData(reinterpret_cast<uint8_t*>(videoData), size, type);
    env->ReleaseByteArrayElements(video, videoData, 0);
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_pushSpsPps(
        JNIEnv* env, jobject /*thiz*/, jbyteArray sps, jint spsSize, jbyteArray pps, jint ppsSize) {
    jbyte* spsData = env->GetByteArrayElements(sps, nullptr);
    jbyte* ppsData = env->GetByteArrayElements(pps, nullptr);
    PushProxy::getInstance()->pushSpsPps(reinterpret_cast<uint8_t*>(spsData), spsSize,
                                         reinterpret_cast<uint8_t*>(ppsData), ppsSize);
    env->ReleaseByteArrayElements(sps, spsData, 0);
    env->ReleaseByteArrayElements(pps, ppsData, 0);
}

}  // extern "C"
