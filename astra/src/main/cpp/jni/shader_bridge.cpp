#include <jni.h>

#include "ShaderLibrary.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_astra_avpush_runtime_NativeShaders_nativeGetScript(
        JNIEnv* env, jobject /*thiz*/, jint id) {
    const char* script = astra::GetShaderScript(id);
    if (script == nullptr) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(script);
}
