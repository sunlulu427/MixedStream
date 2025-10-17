#ifndef AVLIVEPUSHER_QUEUE_H
#define AVLIVEPUSHER_QUEUE_H

#include <pthread.h>
#include <queue>

extern "C" {
#include "../librtmp/include/rtmp.h"
}

class AVQueue {
public:
    AVQueue();
    ~AVQueue();

    int putRtmpPacket(RTMPPacket* packet);
    RTMPPacket* getRtmpPacket();
    void clearQueue();
    void notifyQueue();

private:
    std::queue<RTMPPacket*> queuePacket;
    pthread_mutex_t mutexPacket{};
    pthread_cond_t condPacket{};
};

#endif  // AVLIVEPUSHER_QUEUE_H
