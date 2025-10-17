#include "IThread.h"

namespace {
void* threadMain(void* context) {
    auto* thread = static_cast<IThread*>(context);
    thread->main();
    return nullptr;
}
}  // namespace

void IThread::start() {
    if (pthread_create(&pId, nullptr, threadMain, this) == 0) {
        running = true;
    }
}

void IThread::stop() {
    if (running) {
        pthread_join(pId, nullptr);
        running = false;
        pId = pthread_t{};
    }
}
