#ifndef ASTRASTREAM_ITHREAD_H
#define ASTRASTREAM_ITHREAD_H

#include <pthread.h>

class IThread {
public:
    virtual ~IThread() = default;

    virtual void start() = 0;
    virtual void stop() = 0;
    virtual void main() = 0;

protected:
    bool startWorker();
    void joinWorker();
    [[nodiscard]] bool isWorkerRunning() const;

private:
    pthread_t threadId{};
    bool running = false;
};
#endif  // ASTRASTREAM_ITHREAD_H
