#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "EncodeRendererNative.h"

namespace {

template <typename T>
T* fromHandle(jlong handle) {
    return reinterpret_cast<T*>(handle);
}

std::vector<float> toVector(JNIEnv* env, jfloatArray array) {
    if (array == nullptr) {
        return {};
    }
    const jsize length = env->GetArrayLength(array);
    std::vector<float> result(static_cast<size_t>(length));
    env->GetFloatArrayRegion(array, 0, length, result.data());
    return result;
}

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_astrastream_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeCreate(
        JNIEnv* env, jobject /*thiz*/, jint texture_id, jfloatArray vertex_array,
        jfloatArray fragment_array) {
    auto vertexData = toVector(env, vertex_array);
    auto fragmentData = toVector(env, fragment_array);
    auto* renderer = new EncodeRendererNative(static_cast<GLuint>(texture_id), std::move(vertexData),
                                              std::move(fragmentData));
    return reinterpret_cast<jlong>(renderer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    delete fromHandle<EncodeRendererNative>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeOnSurfaceCreate(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring vertex_source,
        jstring fragment_source) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->initialize(toString(env, vertex_source), toString(env, fragment_source));
}

extern "C" JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeOnSurfaceChanged(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint width, jint height) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->surfaceChanged(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeOnDraw(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->draw();
}

extern "C" JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeUpdateWatermarkCoords(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jfloatArray coords_array) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->updateWatermarkCoords(toVector(env, coords_array));
}

extern "C" JNIEXPORT void JNICALL
Java_com_astrastream_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeUpdateWatermarkTexture(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jobject bitmap) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->updateWatermarkTexture(env, bitmap);
}
