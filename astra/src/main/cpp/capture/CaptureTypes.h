#ifndef ASTRASTREAM_CAPTURETYPES_H
#define ASTRASTREAM_CAPTURETYPES_H

#include <cstdint>

namespace astra {

enum class CameraFacing : uint8_t {
    kBack = 0,
    kFront = 1,
};

struct CameraConfig {
    int32_t width = 720;
    int32_t height = 1280;
    int32_t fps = 30;
    CameraFacing facing = CameraFacing::kBack;
    int32_t orientation = 0;
    int32_t rotation = 0;
    int32_t focusMode = 0;
};

struct CameraDescriptor {
    int32_t id = 0;
    CameraFacing facing = CameraFacing::kBack;
    int32_t previewWidth = 0;
    int32_t previewHeight = 0;
    int32_t orientation = 0;
    bool hasFlash = false;
    bool supportsTouchFocus = false;
    bool touchFocusEnabled = false;
};

}  // namespace astra

#endif  // ASTRASTREAM_CAPTURETYPES_H
