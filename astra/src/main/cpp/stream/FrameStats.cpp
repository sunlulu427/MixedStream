#include "FrameStats.h"

#include <algorithm>
#include <cmath>

namespace astra {

namespace {
constexpr int64_t kWindowDurationMs = 1000;
}

FrameStats::FrameStats() = default;

FrameStatsResult FrameStats::onSample(std::size_t bytes, int64_t timestampMs) {
    if (windowStartMs_ == 0) {
        windowStartMs_ = timestampMs;
    }
    windowBytes_ += bytes;
    windowFrames_ += 1;

    const auto elapsed = timestampMs - windowStartMs_;
    if (elapsed < kWindowDurationMs) {
        return {};
    }

    FrameStatsResult result;
    if (elapsed > 0) {
        const auto bitrateBps = static_cast<double>(windowBytes_) * 8.0 * 1000.0 /
                static_cast<double>(elapsed);
        const auto bitrateKbps = static_cast<int>(std::lround(bitrateBps / 1000.0));
        const auto fps = static_cast<int>(std::lround(
                static_cast<double>(windowFrames_) * 1000.0 / static_cast<double>(elapsed)));
        result.bitrateKbps = std::max(bitrateKbps, 0);
        result.fps = std::max(fps, 0);
        result.valid = true;
    }
    reset(timestampMs);
    return result;
}

void FrameStats::reset(int64_t timestampMs) {
    windowBytes_ = 0;
    windowFrames_ = 0;
    windowStartMs_ = timestampMs;
}

}  // namespace astra
