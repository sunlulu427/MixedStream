#include "NativeCameraEngine.h"

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window.h>
#include <camera/NdkCameraMetadata.h>

#include <array>

namespace {
constexpr const char* kTag = "NativeCameraEngine";

uint8_t ToLensFacing(astra::CameraFacing facing) {
    return facing == astra::CameraFacing::kFront ? ACAMERA_LENS_FACING_FRONT
                                                 : ACAMERA_LENS_FACING_BACK;
}

astra::CameraFacing FromLensFacing(uint8_t facing) {
    return facing == ACAMERA_LENS_FACING_FRONT ? astra::CameraFacing::kFront
                                               : astra::CameraFacing::kBack;
}

}  // namespace

NativeCameraEngine& NativeCameraEngine::Instance() {
    static NativeCameraEngine engine;
    return engine;
}

NativeCameraEngine::NativeCameraEngine() {
    manager_ = ACameraManager_create();
    deviceCallbacks_.context = this;
    deviceCallbacks_.onDisconnected = &NativeCameraEngine::OnCameraDisconnected;
    deviceCallbacks_.onError = &NativeCameraEngine::OnCameraError;

    sessionCallbacks_.context = this;
    sessionCallbacks_.onClosed = &NativeCameraEngine::OnSessionClosed;
    sessionCallbacks_.onReady = &NativeCameraEngine::OnSessionReady;
    sessionCallbacks_.onActive = &NativeCameraEngine::OnSessionActive;
}

NativeCameraEngine::~NativeCameraEngine() {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseSessionLocked();
    releaseCameraLocked();
    if (manager_) {
        ACameraManager_delete(manager_);
        manager_ = nullptr;
    }
    if (previewWindow_) {
        ANativeWindow_release(previewWindow_);
        previewWindow_ = nullptr;
    }
}

void NativeCameraEngine::configure(const astra::CameraConfig& config) {
    std::lock_guard<std::mutex> lock(mutex_);
    config_ = config;
}

bool NativeCameraEngine::open() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (camera_) {
        return true;
    }
    if (!manager_) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Camera manager not initialized");
        return false;
    }
    std::string cameraId = pickCameraIdLocked(config_.facing);
    if (cameraId.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "No camera matched facing=%d",
                            static_cast<int>(config_.facing));
        return false;
    }
    currentCameraId_ = cameraId;
    camera_status_t status = ACameraManager_openCamera(
            manager_,
            cameraId.c_str(),
            &deviceCallbacks_,
            &camera_);
    if (status != ACAMERA_OK || camera_ == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "ACameraManager_openCamera failed %d", status);
        camera_ = nullptr;
        return false;
    }
    descriptor_.id = nextCameraId_++;
    descriptor_.facing = config_.facing;
    descriptor_.previewWidth = config_.width;
    descriptor_.previewHeight = config_.height;
    descriptor_.orientation = config_.orientation;
    descriptor_.supportsTouchFocus = config_.focusMode != 0;
    descriptor_.touchFocusEnabled = descriptor_.supportsTouchFocus;
    return true;
}

void NativeCameraEngine::close() {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseSessionLocked();
    releaseCameraLocked();
}

bool NativeCameraEngine::startPreview() {
    std::unique_lock<std::mutex> lock(mutex_);
    if (!camera_ || !previewWindow_) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "startPreview skipped camera=%p window=%p",
                            camera_, previewWindow_);
        return false;
    }

    releaseSessionLocked();

    if (ACaptureSessionOutputContainer_create(&outputContainer_) != ACAMERA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to create output container");
        return false;
    }
    if (ACaptureSessionOutput_create(previewWindow_, &previewOutput_) != ACAMERA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Create preview output failed");
        releaseSessionLocked();
        return false;
    }
    ACaptureSessionOutputContainer_add(outputContainer_, previewOutput_);
    if (ACameraOutputTarget_create(previewWindow_, &previewTarget_) != ACAMERA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Create output target failed");
        releaseSessionLocked();
        return false;
    }
    if (ACameraDevice_createCaptureRequest(
                camera_,
                TEMPLATE_PREVIEW,
                &captureRequest_) != ACAMERA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Create capture request failed");
        releaseSessionLocked();
        return false;
    }
    ACaptureRequest_addTarget(captureRequest_, previewTarget_);

    const int32_t fps = config_.fps > 0 ? config_.fps : 30;
    std::array<int32_t, 2> fpsRange{fps, fps};
    ACaptureRequest_setEntry_i32(
            captureRequest_,
            ACAMERA_CONTROL_AE_TARGET_FPS_RANGE,
            static_cast<int32_t>(fpsRange.size()),
            fpsRange.data());
    int32_t aeMode = ACAMERA_CONTROL_AE_MODE_ON;
    ACaptureRequest_setEntry_i32(captureRequest_, ACAMERA_CONTROL_AE_MODE, 1, &aeMode);
    int32_t afMode = ACAMERA_CONTROL_AF_MODE_CONTINUOUS_VIDEO;
    ACaptureRequest_setEntry_i32(captureRequest_, ACAMERA_CONTROL_AF_MODE, 1, &afMode);

    camera_status_t status = ACameraDevice_createCaptureSession(
            camera_,
            outputContainer_,
            &sessionCallbacks_,
            &captureSession_);
    if (status != ACAMERA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "createCaptureSession failed %d", status);
        releaseSessionLocked();
        return false;
    }

    status = ACameraCaptureSession_setRepeatingRequest(
            captureSession_,
            nullptr,
            1,
            &captureRequest_,
            nullptr);
    if (status != ACAMERA_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "setRepeatingRequest failed %d", status);
        releaseSessionLocked();
        return false;
    }
    return true;
}

void NativeCameraEngine::stopPreview() {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseSessionLocked();
}

bool NativeCameraEngine::switchCamera() {
    std::lock_guard<std::mutex> lock(mutex_);
    config_.facing = config_.facing == astra::CameraFacing::kBack
            ? astra::CameraFacing::kFront
            : astra::CameraFacing::kBack;
    releaseSessionLocked();
    releaseCameraLocked();
    return open();
}

bool NativeCameraEngine::setPreviewWindow(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseSessionLocked();
    if (previewWindow_) {
        ANativeWindow_release(previewWindow_);
        previewWindow_ = nullptr;
    }
    if (window) {
        previewWindow_ = window;
        ANativeWindow_acquire(previewWindow_);
        if (config_.width > 0 && config_.height > 0) {
            ANativeWindow_setBuffersGeometry(
                    previewWindow_,
                    config_.width,
                    config_.height,
                    AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
        }
    }
    return true;
}

astra::CameraDescriptor NativeCameraEngine::descriptor() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return descriptor_;
}

void NativeCameraEngine::releaseSessionLocked() {
    if (captureSession_) {
        ACameraCaptureSession_stopRepeating(captureSession_);
        ACameraCaptureSession_close(captureSession_);
        captureSession_ = nullptr;
    }
    if (captureRequest_) {
        ACaptureRequest_free(captureRequest_);
        captureRequest_ = nullptr;
    }
    if (previewTarget_) {
        ACameraOutputTarget_free(previewTarget_);
        previewTarget_ = nullptr;
    }
    if (previewOutput_) {
        ACaptureSessionOutput_free(previewOutput_);
        previewOutput_ = nullptr;
    }
    if (outputContainer_) {
        ACaptureSessionOutputContainer_free(outputContainer_);
        outputContainer_ = nullptr;
    }
}

void NativeCameraEngine::releaseCameraLocked() {
    if (camera_) {
        ACameraDevice_close(camera_);
        camera_ = nullptr;
    }
}

std::string NativeCameraEngine::pickCameraIdLocked(astra::CameraFacing facing) {
    if (!manager_) {
        return {};
    }
    ACameraIdList* idList = nullptr;
    if (ACameraManager_getCameraIdList(manager_, &idList) != ACAMERA_OK || !idList) {
        return {};
    }
    const uint8_t desiredFacing = ToLensFacing(facing);
    std::string selected;
    for (int i = 0; i < idList->numCameras; ++i) {
        const char* id = idList->cameraIds[i];
        ACameraMetadata* metadata = nullptr;
        if (ACameraManager_getCameraCharacteristics(manager_, id, &metadata) != ACAMERA_OK) {
            continue;
        }
        ACameraMetadata_const_entry entry{};
        bool facingMatch = false;
        if (ACameraMetadata_getConstEntry(metadata, ACAMERA_LENS_FACING, &entry) == ACAMERA_OK &&
            entry.count > 0) {
            facingMatch = entry.data.u8[0] == desiredFacing;
        }
        if (!facingMatch) {
            ACameraMetadata_free(metadata);
            continue;
        }
        if (ACameraMetadata_getConstEntry(metadata, ACAMERA_SENSOR_ORIENTATION, &entry) == ACAMERA_OK &&
            entry.count > 0) {
            descriptor_.orientation = entry.data.i32[0];
        }
        if (ACameraMetadata_getConstEntry(metadata, ACAMERA_FLASH_INFO_AVAILABLE, &entry) == ACAMERA_OK &&
            entry.count > 0) {
            descriptor_.hasFlash = entry.data.u8[0] != 0;
        }
        selected = id;
        ACameraMetadata_free(metadata);
        break;
    }
    ACameraManager_deleteCameraIdList(idList);
    if (selected.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "No exact facing match; fallback to first camera");
        if (ACameraManager_getCameraIdList(manager_, &idList) == ACAMERA_OK && idList &&
            idList->numCameras > 0) {
            selected = idList->cameraIds[0];
            ACameraManager_deleteCameraIdList(idList);
        }
    }
    return selected;
}

void NativeCameraEngine::OnCameraDisconnected(void* context, ACameraDevice* device) {
    auto* self = static_cast<NativeCameraEngine*>(context);
    __android_log_print(ANDROID_LOG_WARN, kTag, "Camera disconnected %p", device);
    std::lock_guard<std::mutex> lock(self->mutex_);
    self->releaseSessionLocked();
    self->releaseCameraLocked();
}

void NativeCameraEngine::OnCameraError(void* context, ACameraDevice* device, int error) {
    auto* self = static_cast<NativeCameraEngine*>(context);
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Camera error %p code=%d", device, error);
    std::lock_guard<std::mutex> lock(self->mutex_);
    self->releaseSessionLocked();
    self->releaseCameraLocked();
}

void NativeCameraEngine::OnSessionClosed(void* context, ACameraCaptureSession*) {
    auto* self = static_cast<NativeCameraEngine*>(context);
    std::lock_guard<std::mutex> lock(self->mutex_);
    self->captureSession_ = nullptr;
}

void NativeCameraEngine::OnSessionReady(void*, ACameraCaptureSession*) {
    __android_log_print(ANDROID_LOG_DEBUG, kTag, "capture session ready");
}

void NativeCameraEngine::OnSessionActive(void*, ACameraCaptureSession*) {
    __android_log_print(ANDROID_LOG_DEBUG, kTag, "capture session active");
}
