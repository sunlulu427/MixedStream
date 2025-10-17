#ifndef RTMPPUSH_ITHREAD_H
#define RTMPPUSH_ITHREAD_H

#include <pthread.h>

class IThread {
public:
    virtual ~IThread() = default;

    virtual void start();
    virtual void stop();
    virtual void main() = 0;

protected:
    pthread_t pId{};
    bool running = false;
};
#endif  // RTMPPUSH_ITHREAD_H
