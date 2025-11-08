#include <jni.h>

#include <memory>
#include <vector>

#include "CameraRendererNative.h"
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

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeCreate(
        JNIEnv* /*env*/, jobject /*thiz*/, jint texture_id) {
    auto* renderer = new EncodeRendererNative(static_cast<GLuint>(texture_id));
    return reinterpret_cast<jlong>(renderer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    delete fromHandle<EncodeRendererNative>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeOnSurfaceCreate(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint width, jint height) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->initialize(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeOnSurfaceChanged(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint width, jint height) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->surfaceChanged(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeOnDraw(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->draw();
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeUpdateWatermarkCoords(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jfloatArray coords_array) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->updateWatermarkCoords(toVector(env, coords_array));
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_EncodeRenderer_nativeUpdateWatermarkTexture(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jobject bitmap) {
    auto* renderer = fromHandle<EncodeRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->updateWatermarkTexture(env, bitmap);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_CameraRenderer_nativeCreate(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    auto* renderer = new CameraRendererNative();
    return reinterpret_cast<jlong>(renderer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_CameraRenderer_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    delete fromHandle<CameraRendererNative>(handle);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_CameraRenderer_nativeOnSurfaceCreate(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint width, jint height) {
    auto* renderer = fromHandle<CameraRendererNative>(handle);
    if (renderer == nullptr) {
        return nullptr;
    }
    auto result = renderer->initialize(width, height);
    jintArray textures = env->NewIntArray(2);
    if (textures == nullptr) {
        return nullptr;
    }
    jint values[2] = {static_cast<jint>(result.cameraTextureId),
                      static_cast<jint>(result.outputTextureId)};
    env->SetIntArrayRegion(textures, 0, 2, values);
    return textures;
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_CameraRenderer_nativeOnSurfaceChanged(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint width, jint height) {
    auto* renderer = fromHandle<CameraRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->surfaceChanged(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_CameraRenderer_nativeOnDraw(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* renderer = fromHandle<CameraRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->draw();
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_CameraRenderer_nativeUpdateMatrix(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jfloatArray matrix_array) {
    auto* renderer = fromHandle<CameraRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->updateMatrix(toVector(env, matrix_array));
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_CameraRenderer_nativeUpdateWatermarkCoords(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jfloatArray coords_array) {
    auto* renderer = fromHandle<CameraRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->updateWatermarkCoords(toVector(env, coords_array));
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_infrastructure_camera_renderer_CameraRenderer_nativeUpdateWatermarkTexture(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jobject bitmap) {
    auto* renderer = fromHandle<CameraRendererNative>(handle);
    if (renderer == nullptr) {
        return;
    }
    renderer->updateWatermarkTexture(env, bitmap);
}
