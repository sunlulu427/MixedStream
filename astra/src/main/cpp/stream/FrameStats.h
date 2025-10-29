#ifndef ASTRASTREAM_FRAMESTATS_H
#define ASTRASTREAM_FRAMESTATS_H

#include <cstdint>
#include <cstddef>

namespace astra {

struct FrameStatsResult {
    int bitrateKbps = 0;
    int fps = 0;
    bool valid = false;
};

class FrameStats {
public:
    FrameStats();
    FrameStatsResult onSample(std::size_t bytes, int64_t timestampMs);
    void reset(int64_t timestampMs);

private:
    std::size_t windowBytes_ = 0;
    std::size_t windowFrames_ = 0;
    int64_t windowStartMs_ = 0;
};

}  // namespace astra

#endif  // ASTRASTREAM_FRAMESTATS_H
