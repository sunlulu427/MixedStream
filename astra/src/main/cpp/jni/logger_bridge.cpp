#include <jni.h>

#include <string>

#include "NativeLogger.h"

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_runtime_NativeLogger_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jstring path) {
    if (path == nullptr) {
        return;
    }
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) {
        return;
    }
    astra::initLogger(std::string(chars));
    env->ReleaseStringUTFChars(path, chars);
}

extern "C" JNIEXPORT void JNICALL
Java_com_astra_avpush_runtime_NativeLogger_nativeWrite(
        JNIEnv* env, jobject /*thiz*/, jint level, jstring tag, jstring message) {
    const char* tagChars = tag != nullptr ? env->GetStringUTFChars(tag, nullptr) : nullptr;
    const char* msgChars = message != nullptr ? env->GetStringUTFChars(message, nullptr) : nullptr;
    const std::string tagString = tagChars != nullptr ? std::string(tagChars) : std::string();
    const std::string messageString = msgChars != nullptr ? std::string(msgChars) : std::string();
    astra::logLine(level, tagString, messageString);
    if (tagChars != nullptr) {
        env->ReleaseStringUTFChars(tag, tagChars);
    }
    if (msgChars != nullptr) {
        env->ReleaseStringUTFChars(message, msgChars);
    }
}
