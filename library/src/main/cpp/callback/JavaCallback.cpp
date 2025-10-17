#include "JavaCallback.h"

namespace {

class LocalEnv {
public:
    LocalEnv(JavaVM* vm, JNIEnv* cached, bool preferCached, bool forceAttach)
        : vm_(vm) {
        if (!vm_) {
            return;
        }

        if (preferCached && cached != nullptr) {
            env_ = cached;
            return;
        }

        if (vm_->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6) != JNI_OK || forceAttach) {
            if (vm_->AttachCurrentThread(&env_, nullptr) == JNI_OK) {
                attached_ = true;
            } else {
                env_ = nullptr;
            }
        }
    }

    ~LocalEnv() {
        if (attached_ && vm_) {
            vm_->DetachCurrentThread();
        }
    }

    JNIEnv* get() const { return env_; }
    explicit operator bool() const { return env_ != nullptr; }

private:
    JavaVM* vm_ = nullptr;
    JNIEnv* env_ = nullptr;
    bool attached_ = false;
};

}  // namespace

JavaCallback::JavaCallback(JavaVM* vm, JNIEnv* env, jobject obj)
    : jniEnv(env), javaVM(vm) {
    if (!env || !vm || !obj) {
        return;
    }

    jobject1 = env->NewGlobalRef(obj);
    if (!jobject1) {
        return;
    }

    jclass clazz = env->GetObjectClass(jobject1);
    jmid_connecting = env->GetMethodID(clazz, "onConnecting", "()V");
    jmid_success = env->GetMethodID(clazz, "onConnected", "()V");
    jmid_close = env->GetMethodID(clazz, "onClose", "()V");
    jmid_fail = env->GetMethodID(clazz, "onError", "(I)V");
    env->DeleteLocalRef(clazz);
}

JavaCallback::~JavaCallback() {
    if (!javaVM || !jobject1) {
        return;
    }

    LocalEnv env(javaVM, jniEnv, false, false);
    if (JNIEnv* scopedEnv = env.get()) {
        scopedEnv->DeleteGlobalRef(jobject1);
    }

    jobject1 = nullptr;
    jniEnv = nullptr;
    javaVM = nullptr;
    jmid_connecting = nullptr;
    jmid_success = nullptr;
    jmid_close = nullptr;
    jmid_fail = nullptr;
}

void JavaCallback::onConnecting(ThreadContext threadContext) {
    if (!javaVM || !jobject1 || !jmid_connecting) {
        return;
    }

    LocalEnv env(
        javaVM,
        jniEnv,
        threadContext == ThreadContext::Main,
        threadContext == ThreadContext::Worker
    );

    JNIEnv* scopedEnv = env.get();
    if (!scopedEnv) {
        return;
    }
    scopedEnv->CallVoidMethod(jobject1, jmid_connecting);
}

void JavaCallback::onClose(ThreadContext threadContext) {
    if (!javaVM || !jobject1 || !jmid_close) {
        return;
    }

    LocalEnv env(
        javaVM,
        jniEnv,
        threadContext == ThreadContext::Main,
        threadContext == ThreadContext::Worker
    );

    JNIEnv* scopedEnv = env.get();
    if (!scopedEnv) {
        return;
    }
    scopedEnv->CallVoidMethod(jobject1, jmid_close);
}

void JavaCallback::onConnectSuccess() {
    if (!javaVM || !jobject1 || !jmid_success) {
        return;
    }
    LocalEnv env(javaVM, jniEnv, false, true);
    JNIEnv* scopedEnv = env.get();
    if (!scopedEnv) {
        return;
    }
    scopedEnv->CallVoidMethod(jobject1, jmid_success);
}

void JavaCallback::onConnectFail(RtmpErrorCode errorCode) {
    if (!javaVM || !jobject1 || !jmid_fail) {
        return;
    }
    LocalEnv env(javaVM, jniEnv, false, true);
    JNIEnv* scopedEnv = env.get();
    if (!scopedEnv) {
        return;
    }
    scopedEnv->CallVoidMethod(jobject1, jmid_fail, static_cast<jint>(errorCode));
}
