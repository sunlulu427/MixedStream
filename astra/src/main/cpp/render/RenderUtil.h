#ifndef ASTRASTREAM_RENDERUTIL_H
#define ASTRASTREAM_RENDERUTIL_H

#include <array>

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
                                         bool& valid);

}

#endif  // ASTRASTREAM_RENDERUTIL_H
