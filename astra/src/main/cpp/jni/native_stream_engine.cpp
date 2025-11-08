#include <jni.h>

#include <algorithm>
#include <android/log.h>

#include "../codec/NativeStreamEngine.h"

namespace {
constexpr const char* kTag = "native_stream";

uint32_t SanitizeDimension(int value) {
    if (value <= 0) {
        return 16;
    }
    return static_cast<uint32_t>(((value + 15) / 16) * 16);
}

astra::VideoCodecId ResolveCodec(int codecOrdinal) {
    return codecOrdinal == 1 ? astra::VideoCodecId::kH265 : astra::VideoCodecId::kH264;
}

}  // namespace

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_astra_avpush_infrastructure_stream_sender_rtmp_RtmpStreamSession_nativePrepareVideoSurface(
        JNIEnv* env,
        jobject,
        jint width,
        jint height,
        jint fps,
        jint bitrateKbps,
        jint iframeInterval,
        jint codecOrdinal) {
    astra::VideoConfig config;
    config.width = SanitizeDimension(width);
    config.height = SanitizeDimension(height);
    config.fps = static_cast<uint32_t>(std::max(fps, 1));
    config.codec = ResolveCodec(codecOrdinal);
    auto surface = NativeStreamEngine::Instance().prepareVideoSurface(
            env,
            config,
            std::max(bitrateKbps, 100),
            std::max(iframeInterval, 1));
    if (!surface) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "prepareVideoSurface failed");
    }
    return surface;
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_sender_rtmp_RtmpStreamSession_nativeReleaseVideoSurface(
        JNIEnv*, jobject) {
    NativeStreamEngine::Instance().releaseVideoSurface();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_sender_rtmp_RtmpStreamSession_nativeStartVideoEncoder(
        JNIEnv*, jobject) {
    NativeStreamEngine::Instance().startVideo();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_sender_rtmp_RtmpStreamSession_nativeStopVideoEncoder(
        JNIEnv*, jobject) {
    NativeStreamEngine::Instance().stopVideo();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_sender_rtmp_RtmpStreamSession_nativeUpdateVideoBitrate(
        JNIEnv*, jobject, jint bitrateKbps) {
    NativeStreamEngine::Instance().updateVideoBitrate(std::max(bitrateKbps, 100));
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_sender_rtmp_RtmpStreamSession_nativeConfigureAudioEncoder(
        JNIEnv*, jobject, jint sampleRate, jint channels, jint bitrateKbps, jint bytesPerSample) {
    NativeStreamEngine::Instance().configureAudioEncoder(
            std::max(sampleRate, 8000),
            std::max(channels, 1),
            std::max(bitrateKbps, 16),
            std::max(bytesPerSample, 1));
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_sender_rtmp_RtmpStreamSession_nativeStartAudioEncoder(
        JNIEnv*, jobject) {
    NativeStreamEngine::Instance().startAudio();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_sender_rtmp_RtmpStreamSession_nativeStopAudioEncoder(
        JNIEnv*, jobject) {
    NativeStreamEngine::Instance().stopAudio();
}

}  // extern "C"
