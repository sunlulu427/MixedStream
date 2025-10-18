#include <jni.h>

#include <cstdint>

#include "FrameStats.h"

namespace {

astra::FrameStats* fromHandle(jlong handle) {
    return reinterpret_cast<astra::FrameStats*>(handle);
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_com_astrastream_avpush_core_utils_NativeStats_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new astra::FrameStats());
}

extern "C" JNIEXPORT void JNICALL
Java_com_astrastream_avpush_core_utils_NativeStats_nativeRelease(JNIEnv*, jobject, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astrastream_avpush_core_utils_NativeStats_nativeReset(JNIEnv*, jobject, jlong handle, jlong timestampMs) {
    auto* stats = fromHandle(handle);
    if (stats == nullptr) return;
    stats->reset(timestampMs);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_astrastream_avpush_core_utils_NativeStats_nativeOnVideoSample(JNIEnv* env, jobject, jlong handle,
                                                                      jint bytes, jlong timestampMs) {
    auto* stats = fromHandle(handle);
    if (stats == nullptr) return nullptr;
    const auto result = stats->onSample(static_cast<std::size_t>(bytes), timestampMs);
    if (!result.valid) {
        return nullptr;
    }
    jintArray array = env->NewIntArray(2);
    if (array == nullptr) {
        return nullptr;
    }
    jint values[2] = {result.bitrateKbps, result.fps};
    env->SetIntArrayRegion(array, 0, 2, values);
    return array;
}
