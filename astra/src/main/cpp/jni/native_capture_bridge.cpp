#include <jni.h>

#include <android/log.h>
#include <android/native_window_jni.h>

#include "../capture/CaptureTypes.h"
#include "../capture/NativeAudioCapturer.h"
#include "../capture/NativeCameraEngine.h"

namespace {
constexpr const char* kTag = "native_capture";

astra::CameraFacing FacingFromOrdinal(int facingOrdinal) {
    return facingOrdinal == 0 ? astra::CameraFacing::kFront : astra::CameraFacing::kBack;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_NativeCameraDevice_nativeConfigure(
        JNIEnv*,
        jobject,
        jint width,
        jint height,
        jint fps,
        jint facingOrdinal,
        jint orientation,
        jint rotation,
        jint focusMode) {
    astra::CameraConfig config;
    config.width = width > 0 ? width : 720;
    config.height = height > 0 ? height : 1280;
    config.fps = fps > 0 ? fps : 30;
    config.facing = FacingFromOrdinal(facingOrdinal);
    config.orientation = orientation;
    config.rotation = rotation;
    config.focusMode = focusMode;
    NativeCameraEngine::Instance().configure(config);
}

JNIEXPORT jboolean JNICALL
Java_com_astra_avpush_infrastructure_camera_NativeCameraDevice_nativeOpenCamera(
        JNIEnv*, jobject) {
    return NativeCameraEngine::Instance().open() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_astra_avpush_infrastructure_camera_NativeCameraDevice_nativeSetSurface(
        JNIEnv* env,
        jobject,
        jobject surface) {
    ANativeWindow* window = nullptr;
    if (surface) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    const bool ok = NativeCameraEngine::Instance().setPreviewWindow(window);
    if (window) {
        ANativeWindow_release(window);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_astra_avpush_infrastructure_camera_NativeCameraDevice_nativeStartPreview(
        JNIEnv*, jobject) {
    return NativeCameraEngine::Instance().startPreview() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_NativeCameraDevice_nativeStopPreview(
        JNIEnv*, jobject) {
    NativeCameraEngine::Instance().stopPreview();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_NativeCameraDevice_nativeReleaseCamera(
        JNIEnv*, jobject) {
    NativeCameraEngine::Instance().close();
}

JNIEXPORT jboolean JNICALL
Java_com_astra_avpush_infrastructure_camera_NativeCameraDevice_nativeSwitchCamera(
        JNIEnv*, jobject) {
    return NativeCameraEngine::Instance().switchCamera() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_com_astra_avpush_infrastructure_camera_NativeCameraDevice_nativeCurrentDescriptor(
        JNIEnv* env,
        jobject) {
    const auto descriptor = NativeCameraEngine::Instance().descriptor();
    const jint values[8] = {
            descriptor.id,
            descriptor.facing == astra::CameraFacing::kFront ? 0 : 1,
            descriptor.previewWidth,
            descriptor.previewHeight,
            descriptor.orientation,
            descriptor.hasFlash ? 1 : 0,
            descriptor.supportsTouchFocus ? 1 : 0,
            descriptor.touchFocusEnabled ? 1 : 0,
    };
    jintArray array = env->NewIntArray(8);
    if (!array) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to allocate descriptor array");
        return nullptr;
    }
    env->SetIntArrayRegion(array, 0, 8, values);
    return array;
}

JNIEXPORT jboolean JNICALL
Java_com_astra_avpush_infrastructure_audio_NativeAudioCapturer_nativeConfigure(
        JNIEnv*,
        jobject,
        jint sampleRate,
        jint channels,
        jint bytesPerSample) {
    return NativeAudioCapturer::Instance().configure(sampleRate, channels, bytesPerSample)
            ? JNI_TRUE
            : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_astra_avpush_infrastructure_audio_NativeAudioCapturer_nativeStart(
        JNIEnv*, jobject) {
    return NativeAudioCapturer::Instance().start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_audio_NativeAudioCapturer_nativeStop(
        JNIEnv*, jobject) {
    NativeAudioCapturer::Instance().stop();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_audio_NativeAudioCapturer_nativeRelease(
        JNIEnv*, jobject) {
    NativeAudioCapturer::Instance().release();
}

JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_audio_NativeAudioCapturer_nativeSetMute(
        JNIEnv*, jobject, jboolean muted) {
    NativeAudioCapturer::Instance().setMute(muted == JNI_TRUE);
}

}  // extern "C"
