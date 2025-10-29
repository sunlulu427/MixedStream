#ifndef ASTRASTREAM_IPUSH_H
#define ASTRASTREAM_IPUSH_H

#include <cstdint>
#include <cstddef>

#include "IThread.h"
#include "../stream/FlvMuxer.h"

class IPush : public IThread {
public:
    ~IPush() override = default;

    void start() override = 0;
    void stop() override = 0;
    void main() override = 0;
    virtual void configureVideo(const astra::VideoConfig& config) = 0;
    virtual void configureAudio(const astra::AudioConfig& config) = 0;
    virtual void pushVideoFrame(const uint8_t* data, size_t length, int64_t pts) = 0;
    virtual void pushAudioFrame(const uint8_t* data, size_t length, int64_t pts) = 0;
};

#endif  // ASTRASTREAM_IPUSH_H
