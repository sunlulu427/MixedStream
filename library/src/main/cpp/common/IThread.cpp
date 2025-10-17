#include "IThread.h"

namespace {
void* threadMain(void* context) {
    auto* thread = static_cast<IThread*>(context);
    thread->main();
    return nullptr;
}
}  // namespace

bool IThread::startWorker() {
    if (running) {
        return false;
    }
    if (pthread_create(&threadId, nullptr, threadMain, this) != 0) {
        return false;
    }
    running = true;
    return true;
}

void IThread::joinWorker() {
    if (running) {
        pthread_join(threadId, nullptr);
        running = false;
        threadId = pthread_t{};
    }
}

bool IThread::isWorkerRunning() const {
    return running;
}
