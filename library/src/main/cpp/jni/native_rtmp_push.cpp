//
// Created by 阳坤 on 2020-07-03.
//
#include <jni.h>
#include <PushProxy.h>
#include <android/log.h>

JavaVM *javaVM = 0;

/**
 * native
 * @param javaVM
 * @param pVoid
 * @return
 */
int JNI_OnLoad(JavaVM *vm, void *pVoid) {
    JNIEnv *jniEnv;
    if (vm->GetEnv(reinterpret_cast<void **>(&jniEnv), JNI_VERSION_1_6)) {
        return JNI_ERR;
    }
    javaVM = vm;
    __android_log_print(ANDROID_LOG_DEBUG, "native_rtmp_push", "JNI_OnLoad: %p", vm);
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_NativeRtmpConnect(
        JNIEnv *jniEnv, jobject jobject1, jstring url) {
    const char *rtmpUrl = jniEnv->GetStringUTFChars(url, nullptr);
    JavaCallback *javaCallback = new JavaCallback(javaVM, jniEnv, jobject1);
    PushProxy::getInstance()->init(rtmpUrl, &javaCallback);
    PushProxy::getInstance()->start();
    jniEnv->ReleaseStringUTFChars(url, rtmpUrl);
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_NativeRtmpClose(
        JNIEnv *jniEnv, jobject jobject1) {
    PushProxy::getInstance()->stop();
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_pushAudio(
        JNIEnv *jniEnv, jobject jobject1, jbyteArray audio, jint size, jint type) {
    jbyte *audioData = jniEnv->GetByteArrayElements(audio, nullptr);
    PushProxy::getInstance()->pushAudioData(reinterpret_cast<uint8_t *>(audioData), size, type);
    jniEnv->ReleaseByteArrayElements(audio, audioData, 0);
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_pushVideo(
        JNIEnv *jniEnv, jobject jobject1, jbyteArray video, jint size, jint type) {
    jbyte *videoData = jniEnv->GetByteArrayElements(video, nullptr);
    PushProxy::getInstance()->pushVideoData(reinterpret_cast<uint8_t *>(videoData), size, type);
    jniEnv->ReleaseByteArrayElements(video, videoData, 0);
}

JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_stream_sender_rtmp_RtmpSender_pushSpsPps(
        JNIEnv *jniEnv, jobject jobject1, jbyteArray sps, jint spsSize,
        jbyteArray pps, jint ppsSize) {
    jbyte *spsData = jniEnv->GetByteArrayElements(sps, nullptr);
    jbyte *ppsData = jniEnv->GetByteArrayElements(pps, nullptr);
    PushProxy::getInstance()->pushSpsPps(reinterpret_cast<uint8_t *>(spsData), spsSize,
                                         reinterpret_cast<uint8_t *>(ppsData), ppsSize);
    jniEnv->ReleaseByteArrayElements(sps, spsData, 0);
    jniEnv->ReleaseByteArrayElements(pps, ppsData, 0);
}

}
