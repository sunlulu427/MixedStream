#ifndef ASTRASTREAM_NATIVE_AUDIO_CAPTURER_H
#define ASTRASTREAM_NATIVE_AUDIO_CAPTURER_H

#include <aaudio/AAudio.h>

#include <atomic>
#include <cstdint>
#include <mutex>

class NativeAudioCapturer {
public:
    static NativeAudioCapturer& Instance();

    bool configure(int32_t sampleRate, int32_t channels, int32_t bytesPerSample);
    bool start();
    void stop();
    void release();
    void setMute(bool muted);

private:
    NativeAudioCapturer() = default;
    ~NativeAudioCapturer() = default;

    NativeAudioCapturer(const NativeAudioCapturer&) = delete;
    NativeAudioCapturer& operator=(const NativeAudioCapturer&) = delete;

    static aaudio_data_callback_result_t DataCallback(
            AAudioStream* stream,
            void* userData,
            void* audioData,
            int32_t numFrames);
    static void ErrorCallback(
            AAudioStream* stream,
            void* userData,
            aaudio_result_t error);

    bool configureStreamLocked(int32_t sampleRate, int32_t channels, int32_t bytesPerSample);
    size_t frameSizeBytes() const;

    std::mutex mutex_;
    AAudioStream* stream_ = nullptr;
    int32_t sampleRate_ = 48000;
    int32_t channelCount_ = 1;
    int32_t bytesPerSample_ = 2;
    std::atomic<bool> capturing_{false};
    std::atomic<bool> muted_{false};
};

#endif  // ASTRASTREAM_NATIVE_AUDIO_CAPTURER_H
