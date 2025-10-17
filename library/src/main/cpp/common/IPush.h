#ifndef RTMPPUSH_IPUSH_H
#define RTMPPUSH_IPUSH_H

#include <cstdint>

#include "IThread.h"

class IPush : public IThread {
public:
public:
    ~IPush() override = default;

    void start() override = 0;
    void stop() override = 0;
    void main() override = 0;
    virtual void pushSpsPps(uint8_t* sps, int sps_len, uint8_t* pps, int pps_len) = 0;
    virtual void pushAudioData(uint8_t* audio, int len, int type) = 0;
    virtual void pushVideoData(uint8_t* video, int len, int type) = 0;
};

#endif  // RTMPPUSH_IPUSH_H
