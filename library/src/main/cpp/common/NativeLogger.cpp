#include "NativeLogger.h"

#include <android/log.h>

#include <chrono>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <mutex>

namespace astra {

namespace {

constexpr char kFallbackTag[] = "AstraNative";

std::mutex& logMutex() {
    static std::mutex mutex;
    return mutex;
}

std::unique_ptr<std::ofstream> &logStream() {
    static std::unique_ptr<std::ofstream> stream;
    return stream;
}

std::string& logPathStorage() {
    static std::string path;
    return path;
}

bool& loggerConfigured() {
    static bool configured = false;
    return configured;
}

std::string levelToString(int level) {
    switch (level) {
        case 0:
            return "VERBOSE";
        case 1:
            return "DEBUG";
        case 2:
            return "INFO";
        case 3:
            return "WARN";
        case 4:
            return "ERROR";
        default:
            return "TRACE";
    }
}

std::string timestamp() {
    using clock = std::chrono::system_clock;
    const auto now = clock::now();
    const auto time = clock::to_time_t(now);
    std::tm tm;
    localtime_r(&time, &tm);
    std::ostringstream oss;
    oss << std::put_time(&tm, "%Y-%m-%d %H:%M:%S");
    const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) % 1000;
    oss << '.' << std::setfill('0') << std::setw(3) << ms.count();
    return oss.str();
}

void ensureStreamLocked() {
    auto& stream = logStream();
    if (stream && stream->is_open()) {
        return;
    }
    const auto& path = logPathStorage();
    if (path.empty()) {
        return;
    }
    std::filesystem::create_directories(std::filesystem::path(path).parent_path());
    stream = std::make_unique<std::ofstream>(path, std::ios::app);
}

}  // namespace

void initLogger(const std::string& path) {
    std::lock_guard<std::mutex> guard(logMutex());
    logPathStorage() = path;
    ensureStreamLocked();
    loggerConfigured() = logStream() && logStream()->is_open();
    if (!loggerConfigured()) {
        __android_log_print(ANDROID_LOG_ERROR, kFallbackTag, "Unable to open log file: %s", path.c_str());
    }
}

void logLine(int level, const std::string& tag, const std::string& message) {
    std::lock_guard<std::mutex> guard(logMutex());
    if (!loggerConfigured()) {
        return;
    }
    ensureStreamLocked();
    auto& stream = logStream();
    if (!stream || !stream->is_open()) {
        return;
    }
    (*stream) << timestamp() << ' ' << levelToString(level) << '/'
              << (!tag.empty() ? tag : kFallbackTag) << " - " << message << '\n';
    stream->flush();
}

}  // namespace astra
