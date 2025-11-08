#ifndef ASTRASTREAM_NATIVE_CAMERA_ENGINE_H
#define ASTRASTREAM_NATIVE_CAMERA_ENGINE_H

#include <android/native_window.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCaptureRequest.h>

#include <cstdint>
#include <mutex>
#include <string>

#include "CaptureTypes.h"

class NativeCameraEngine {
public:
    static NativeCameraEngine& Instance();

    void configure(const astra::CameraConfig& config);
    bool open();
    void close();
    bool startPreview();
    void stopPreview();
    bool switchCamera();
    bool setPreviewWindow(ANativeWindow* window);
    astra::CameraDescriptor descriptor() const;

private:
    NativeCameraEngine();
    ~NativeCameraEngine();

    NativeCameraEngine(const NativeCameraEngine&) = delete;
    NativeCameraEngine& operator=(const NativeCameraEngine&) = delete;

    void releaseSessionLocked();
    void releaseCameraLocked();
    std::string pickCameraIdLocked(astra::CameraFacing facing);

    static void OnCameraDisconnected(void* context, ACameraDevice* device);
    static void OnCameraError(void* context, ACameraDevice* device, int error);
    static void OnSessionClosed(void* context, ACameraCaptureSession* session);
    static void OnSessionReady(void* context, ACameraCaptureSession* session);
    static void OnSessionActive(void* context, ACameraCaptureSession* session);

    astra::CameraConfig config_{};
    astra::CameraDescriptor descriptor_{};

    ACameraManager* manager_ = nullptr;
    ACameraDevice* camera_ = nullptr;
    ACameraCaptureSession* captureSession_ = nullptr;
    ACaptureRequest* captureRequest_ = nullptr;
    ACaptureSessionOutputContainer* outputContainer_ = nullptr;
    ACaptureSessionOutput* previewOutput_ = nullptr;
    ACameraOutputTarget* previewTarget_ = nullptr;
    ANativeWindow* previewWindow_ = nullptr;

    ACameraDevice_StateCallbacks deviceCallbacks_{};
    ACameraCaptureSession_stateCallbacks sessionCallbacks_{};

    mutable std::mutex mutex_;
    std::string currentCameraId_;
    int32_t nextCameraId_ = 0;
};

#endif  // ASTRASTREAM_NATIVE_CAMERA_ENGINE_H
