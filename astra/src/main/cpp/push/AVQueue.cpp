#include "AVQueue.h"

#include <cstdlib>

AVQueue::AVQueue() {
    pthread_mutex_init(&mutexPacket, nullptr);
    pthread_cond_init(&condPacket, nullptr);
}

AVQueue::~AVQueue() {
    clearQueue();
    pthread_mutex_destroy(&mutexPacket);
    pthread_cond_destroy(&condPacket);
}

int AVQueue::putRtmpPacket(RTMPPacket* packet) {
    if (packet == nullptr) {
        return -1;
    }
    pthread_mutex_lock(&mutexPacket);
    queuePacket.push(packet);
    pthread_cond_signal(&condPacket);
    pthread_mutex_unlock(&mutexPacket);
    return 0;
}

RTMPPacket* AVQueue::getRtmpPacket() {
    pthread_mutex_lock(&mutexPacket);
    RTMPPacket* packet = nullptr;
    if (!queuePacket.empty()) {
        packet = queuePacket.front();
        queuePacket.pop();
    } else {
        pthread_cond_wait(&condPacket, &mutexPacket);
    }
    pthread_mutex_unlock(&mutexPacket);
    return packet;
}

void AVQueue::clearQueue() {
    pthread_mutex_lock(&mutexPacket);
    while (!queuePacket.empty()) {
        RTMPPacket* packet = queuePacket.front();
        queuePacket.pop();
        RTMPPacket_Free(packet);
        std::free(packet);
    }
    pthread_mutex_unlock(&mutexPacket);
}

void AVQueue::notifyQueue() {
    pthread_mutex_lock(&mutexPacket);
    pthread_cond_signal(&condPacket);
    pthread_mutex_unlock(&mutexPacket);
}
