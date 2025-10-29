#ifndef ASTRASTREAM_JAVACALLBACK_H
#define ASTRASTREAM_JAVACALLBACK_H

#include <jni.h>

enum class ThreadContext : jint {
    Main = 1,
    Worker = 2,
};

enum class RtmpErrorCode : jint {
    InitFailure = -9,
    UrlSetupFailure = -10,
    ConnectFailure = -11,
    Closed = -12,
};

class JavaCallback {
public:
    JavaCallback(JavaVM* vm, JNIEnv* env, jobject obj);
    ~JavaCallback();

    void onConnecting(ThreadContext threadContext);
    void onConnectSuccess();
    void onConnectFail(RtmpErrorCode errorCode);
    void onClose(ThreadContext threadContext);

private:
    JNIEnv* jniEnv = nullptr;
    JavaVM* javaVM = nullptr;
    jobject jobject1 = nullptr;
    jmethodID jmid_connecting = nullptr;
    jmethodID jmid_success = nullptr;
    jmethodID jmid_close = nullptr;
    jmethodID jmid_fail = nullptr;
};

#endif  // ASTRASTREAM_JAVACALLBACK_H
