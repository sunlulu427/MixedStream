#include <jni.h>

#include "RenderUtil.h"

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_astra_avpush_runtime_NativeRenderUtil_nativeComputeWatermarkQuad(
        JNIEnv* env,
        jobject /*thiz*/,
        jint surfaceWidth,
        jint surfaceHeight,
        jint bitmapWidth,
        jint bitmapHeight,
        jfloat scale,
        jfloat minHeight,
        jfloat maxHeight,
        jfloat maxWidth,
        jfloat horizontalMargin,
        jfloat verticalMargin) {
    bool valid = false;
    const auto quad = astra::ComputeWatermarkQuad(surfaceWidth,
                                                  surfaceHeight,
                                                  bitmapWidth,
                                                  bitmapHeight,
                                                  scale,
                                                  minHeight,
                                                  maxHeight,
                                                  maxWidth,
                                                  horizontalMargin,
                                                  verticalMargin,
                                                  valid);
    if (!valid) {
        return nullptr;
    }
    jfloatArray array = env->NewFloatArray(8);
    if (array == nullptr) {
        return nullptr;
    }
    env->SetFloatArrayRegion(array, 0, 8, quad.data());
    return array;
}
