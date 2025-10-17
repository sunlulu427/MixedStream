#ifndef RTMPPUSH_JAVACALLBACK_H
#define RTMPPUSH_JAVACALLBACK_H

#include <jni.h>

#define THREAD_MAIN 1
#define THREAD_CHILD 2

#define RTMP_INIT_ERROR -9
#define RTMP_SET_URL_ERROR -10
#define RTMP_CONNECT_ERROR -11
#define RTMP_CLOSE -12

class JavaCallback {
public:
    JavaCallback(JavaVM* vm, JNIEnv* env, jobject obj);
    ~JavaCallback();

    void onConnecting(int threadType);
    void onConnectSuccess();
    void onConnectFail(int errorCode);
    void onClose(int threadType);

private:
    JNIEnv* jniEnv = nullptr;
    JavaVM* javaVM = nullptr;
    jobject jobject1 = nullptr;
    jmethodID jmid_connecting = nullptr;
    jmethodID jmid_success = nullptr;
    jmethodID jmid_close = nullptr;
    jmethodID jmid_fail = nullptr;
};

#endif  // RTMPPUSH_JAVACALLBACK_H
