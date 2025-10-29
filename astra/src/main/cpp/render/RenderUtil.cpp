#include <algorithm>
#include <array>

#include "RenderUtil.h"

namespace astra {

std::array<float, 8> ComputeWatermarkQuad(int surfaceWidth,
                                         int surfaceHeight,
                                         int bitmapWidth,
                                         int bitmapHeight,
                                         float scale,
                                         float minHeight,
                                         float maxHeight,
                                         float maxWidth,
                                         float horizontalMargin,
                                         float verticalMargin,
                                         bool& valid) {
    valid = false;
    std::array<float, 8> result{};
    if (surfaceWidth <= 0 || surfaceHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return result;
    }
    const float safeScale = scale > 0.f ? scale : 1.f;
    const float ratio = static_cast<float>(bitmapWidth) / static_cast<float>(std::max(bitmapHeight, 1));
    float targetHeight = (2.f * static_cast<float>(bitmapHeight) / static_cast<float>(surfaceHeight)) * safeScale;
    targetHeight = std::clamp(targetHeight, minHeight, maxHeight);
    float targetWidth = targetHeight * ratio;
    if (targetWidth > maxWidth) {
        const float factor = maxWidth / targetWidth;
        targetWidth = maxWidth;
        targetHeight = std::max(minHeight, targetHeight * factor);
    }

    float right = 1.f - horizontalMargin;
    float left = right - targetWidth;
    const float minLeft = -1.f + horizontalMargin;
    if (left < minLeft) {
        const float shift = minLeft - left;
        left += shift;
        right += shift;
    }

    float bottom = -1.f + verticalMargin;
    float top = bottom + targetHeight;
    const float maxTop = 1.f - verticalMargin;
    if (top > maxTop) {
        const float shift = top - maxTop;
        top -= shift;
        bottom -= shift;
    }

    result = {left, bottom, right, bottom, left, top, right, top};
    valid = true;
    return result;
}

}  // namespace astra
