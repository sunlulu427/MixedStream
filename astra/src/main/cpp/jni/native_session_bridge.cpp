#include <jni.h>

#include <algorithm>

#include "../capture/NativeAudioCapturer.h"
#include "../codec/NativeStreamEngine.h"
#include "../common/PushProxy.h"
#include "../stream/FlvMuxer.h"

namespace {

uint32_t SanitizeDimension(int value) {
    if (value <= 0) {
        return 16;
    }
    return static_cast<uint32_t>(((value + 15) / 16) * 16);
}

int SanitizeSampleRate(int value) {
    return value > 8000 ? value : 8000;
}

int SanitizeChannels(int value) {
    return value > 0 ? value : 1;
}

int SanitizeBytesPerSample(int value) {
    if (value == 1 || value == 2 || value == 4) {
        return value;
    }
    return 2;
}

int SanitizeBitrate(int value, int fallback) {
    return value > 0 ? value : fallback;
}

astra::VideoCodecId ResolveCodec(int codecOrdinal) {
    return codecOrdinal == 1 ? astra::VideoCodecId::kH265 : astra::VideoCodecId::kH264;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeConfigureSession(
        JNIEnv*,
        jclass,
        jlong /*handle*/,
        jint sampleRate,
        jint channels,
        jint bytesPerSample,
        jint audioBitrateKbps,
        jint videoWidth,
        jint videoHeight,
        jint videoFps,
        jint videoBitrateKbps,
        jint iframeInterval,
        jint codecOrdinal) {
    const int sanitizedSampleRate = SanitizeSampleRate(sampleRate);
    const int sanitizedChannels = SanitizeChannels(channels);
    const int sanitizedBytesPerSample = SanitizeBytesPerSample(bytesPerSample);
    const int sanitizedAudioBitrate = SanitizeBitrate(audioBitrateKbps, 64);
    NativeAudioCapturer::Instance().configure(
            sanitizedSampleRate,
            sanitizedChannels,
            sanitizedBytesPerSample);
    NativeStreamEngine::Instance().configureAudioEncoder(
            sanitizedSampleRate,
            sanitizedChannels,
            sanitizedAudioBitrate,
            sanitizedBytesPerSample);

    astra::VideoConfig videoConfig;
    videoConfig.width = SanitizeDimension(videoWidth);
    videoConfig.height = SanitizeDimension(videoHeight);
    videoConfig.fps = static_cast<uint32_t>(std::max(videoFps, 1));
    videoConfig.codec = ResolveCodec(codecOrdinal);
    PushProxy::getInstance()->configureVideo(videoConfig);
    NativeStreamEngine::Instance().updateVideoBitrate(
            SanitizeBitrate(videoBitrateKbps, 1000));
    const int sanitizedIframe = std::max(iframeInterval, 1);
    (void) sanitizedIframe;  // iframe interval applied when preparing video surface
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeStartSession(
        JNIEnv*, jclass, jlong /*handle*/) {
    NativeStreamEngine::Instance().startVideo();
    NativeStreamEngine::Instance().startAudio();
    NativeAudioCapturer::Instance().start();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativePauseSession(
        JNIEnv*, jclass, jlong /*handle*/) {
    NativeAudioCapturer::Instance().stop();
    NativeStreamEngine::Instance().stopAudio();
    NativeStreamEngine::Instance().stopVideo();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeResumeSession(
        JNIEnv*, jclass, jlong /*handle*/) {
    NativeStreamEngine::Instance().startVideo();
    NativeStreamEngine::Instance().startAudio();
    NativeAudioCapturer::Instance().start();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeStopSession(
        JNIEnv*, jclass, jlong /*handle*/) {
    NativeAudioCapturer::Instance().stop();
    NativeStreamEngine::Instance().stopAudio();
    NativeStreamEngine::Instance().stopVideo();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_stream_nativebridge_NativeSenderBridge_nativeSetMute(
        JNIEnv*, jclass, jlong /*handle*/, jboolean muted) {
    NativeAudioCapturer::Instance().setMute(muted == JNI_TRUE);
}

}  // extern "C"
