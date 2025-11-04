#include "RTMPPush.h"

#include <cstdlib>
#include <cstring>
#include <string>
#include <cstdarg>

extern "C" {
#include "../librtmp/include/log.h"
}

namespace {
std::string AValToString(const AVal& v) {
    if (!v.av_val || v.av_len <= 0) return "";
    return std::string(v.av_val, v.av_len);
}

const char* ProtocolName(int protocol) {
    switch (protocol) {
        case RTMP_PROTOCOL_RTMP: return "rtmp";
        case RTMP_PROTOCOL_RTMPE: return "rtmpe";
        case RTMP_PROTOCOL_RTMPT: return "rtmpt";
        case RTMP_PROTOCOL_RTMPS: return "rtmps";
        case RTMP_PROTOCOL_RTMPTE: return "rtmpte";
        case RTMP_PROTOCOL_RTMPTS: return "rtmpts";
        case RTMP_PROTOCOL_RTMFP: return "rtmfp";
        default: return "unknown";
    }
}

void RtmpAndroidLogCallback(int level, const char* fmt, va_list args) {
    int prio = ANDROID_LOG_DEBUG;
    switch (level) {
        case RTMP_LOGCRIT:   prio = ANDROID_LOG_FATAL; break;
        case RTMP_LOGERROR:  prio = ANDROID_LOG_ERROR; break;
        case RTMP_LOGWARNING:prio = ANDROID_LOG_WARN;  break;
        case RTMP_LOGINFO:   prio = ANDROID_LOG_INFO;  break;
        case RTMP_LOGDEBUG:
        case RTMP_LOGDEBUG2:
        case RTMP_LOGALL:    prio = ANDROID_LOG_DEBUG; break;
        default:             prio = ANDROID_LOG_DEBUG; break;
    }
    char buffer[1024];
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    __android_log_print(prio, "librtmp", "%s", buffer);
}
std::string MaskUrl(const char* url) {
    if (url == nullptr) {
        return "null";
    }
    std::string value(url);
    const auto separator = value.find_last_of('/');
    if (separator == std::string::npos || separator >= value.size() - 1) {
        return value;
    }
    std::string suffix = value.substr(separator + 1);
    std::string masked;
    if (suffix.size() <= 2) {
        masked = std::string(suffix.size(), '*');
    } else if (suffix.size() <= 4) {
        masked = suffix.substr(0, 1) + std::string("***");
    } else {
        masked = suffix.substr(0, 2) + std::string("***") + suffix.substr(suffix.size() - 2);
    }
    return value.substr(0, separator + 1) + masked;
}
}  // namespace

RTMPPush::RTMPPush(const char* url, JavaCallback** javaCallback)
    : mQueue(new AVQueue()), mCallback(javaCallback ? *javaCallback : nullptr) {
    LOGD("RTMPPush ctor url=%s callback=%p", MaskUrl(url).c_str(), javaCallback ? *javaCallback : nullptr);
    if (url != nullptr) {
        const auto length = std::strlen(url);
        mRtmpUrl = new char[length + 1];
        std::memcpy(mRtmpUrl, url, length + 1);
    }
}

RTMPPush::~RTMPPush() {
    LOGD("RTMPPush dtor start queue=%p", mQueue);
    stop();
    release();
    delete[] mRtmpUrl;
    mRtmpUrl = nullptr;
    LOGE("RTMPPush destroyed");
}

void RTMPPush::configureVideo(const astra::VideoConfig& config) {
    LOGD("configureVideo width=%u height=%u fps=%u codec=%d",
         config.width,
         config.height,
         config.fps,
         static_cast<int>(config.codec));
    muxer_.setVideoConfig(config);
    headersRequested_ = false;
}

void RTMPPush::configureAudio(const astra::AudioConfig& config) {
    LOGD("configureAudio sampleRate=%u channels=%u bits=%u asc=%zu",
         config.sampleRate,
         config.channels,
         config.sampleSizeBits,
         config.asc.size());
    muxer_.setAudioConfig(config);
    headersRequested_ = false;
}

void RTMPPush::start() {
    LOGD("start queue=%p", mQueue);
    if (!mQueue) {
        mQueue = new AVQueue();
    }
    startWorker();
}

void RTMPPush::stop() {
    LOGD("stop queue=%p", mQueue);
    isPusher = 0;
    if (mQueue) {
        mQueue->notifyQueue();
    }
    joinWorker();
    if (mQueue) {
        mQueue->clearQueue();
        delete mQueue;
        mQueue = nullptr;
    }
    muxer_.reset();
    headersRequested_ = false;
    lastVideoTimestamp_ = 0;
    lastAudioTimestamp_ = 0;
}

void RTMPPush::pushVideoFrame(const uint8_t* data, size_t length, int64_t /*pts*/) {
    astra::ParsedVideoFrame frame = muxer_.parseVideoFrame(data, length);
    if (!frame.hasData()) {
        LOGD("pushVideoFrame skipped: encoder headers pending or frame empty");
        return;
    }

    ensureHeaders();

    std::vector<uint8_t> payload = muxer_.buildVideoTag(frame);
    if (payload.empty()) {
        LOGE("pushVideoFrame buildVideoTag returned empty payload");
        return;
    }

    uint32_t timestamp = 0;
    if (mStartTime > 0) {
        timestamp = RTMP_GetTime() - mStartTime;
        lastVideoTimestamp_ = timestamp;
    } else if (lastVideoTimestamp_ != 0) {
        timestamp = lastVideoTimestamp_;
    }

    enqueuePacket(payload.data(), payload.size(), RTMP_PACKET_TYPE_VIDEO, timestamp, 0x04);
}

void RTMPPush::pushAudioFrame(const uint8_t* data, size_t length, int64_t /*pts*/) {
    if (!muxer_.audioSequenceReady()) {
        LOGD("pushAudioFrame skipped: audio sequence header not ready");
        return;
    }

    ensureHeaders();
    std::vector<uint8_t> payload = muxer_.buildAudioTag(data, length);
    if (payload.empty()) {
        LOGE("pushAudioFrame buildAudioTag returned empty payload");
        return;
    }

    uint32_t timestamp = 0;
    if (mStartTime > 0) {
        timestamp = RTMP_GetTime() - mStartTime;
        lastAudioTimestamp_ = timestamp;
    } else if (lastAudioTimestamp_ != 0) {
        timestamp = lastAudioTimestamp_;
    }

    enqueuePacket(payload.data(), payload.size(), RTMP_PACKET_TYPE_AUDIO, timestamp, 0x05);
}

void RTMPPush::main() {
    LOGD("worker main start");
    onConnecting();
}

void RTMPPush::enqueuePacket(const uint8_t* data,
                             size_t length,
                             uint8_t packetType,
                             uint32_t timestamp,
                             uint8_t channel) {
    if (!mQueue || !data || length == 0) {
        LOGE("enqueuePacket invalid input queue=%p data=%p length=%zu", mQueue, data, length);
        return;
    }

    auto* packet = static_cast<RTMPPacket*>(std::malloc(sizeof(RTMPPacket)));
    if (!packet) {
        LOGE("enqueuePacket malloc failed for RTMPPacket");
        return;
    }

    RTMPPacket_Alloc(packet, static_cast<int>(length));
    RTMPPacket_Reset(packet);
    std::memcpy(packet->m_body, data, length);

    packet->m_packetType = packetType;
    packet->m_nBodySize = static_cast<int>(length);
    packet->m_nTimeStamp = timestamp;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_nChannel = channel;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;

    mQueue->putRtmpPacket(packet);
    if (packetType != RTMP_PACKET_TYPE_VIDEO || timestamp == 0) {
        LOGD("enqueuePacket type=%u timestamp=%u channel=%u size=%zu", packetType, timestamp, channel, length);
    }
}

void RTMPPush::ensureHeaders() {
    if (!headersRequested_) {
        if (!muxer_.hasSentMetadata()) {
            auto metadata = muxer_.buildMetadataTag();
            if (metadata.has_value()) {
                enqueuePacket(metadata->data(), metadata->size(), RTMP_PACKET_TYPE_INFO, 0, 0x03);
                muxer_.markMetadataSent();
                LOGD("ensureHeaders metadata sent size=%zu", metadata->size());
            }
        }

        if (!muxer_.hasSentVideoSequence()) {
            auto videoHeader = muxer_.buildVideoSequenceHeader();
            if (videoHeader.has_value()) {
                enqueuePacket(videoHeader->data(), videoHeader->size(), RTMP_PACKET_TYPE_VIDEO, 0, 0x04);
                LOGD("ensureHeaders video sequence sent size=%zu", videoHeader->size());
            }
        }

        if (!muxer_.hasSentAudioSequence()) {
            auto audioHeader = muxer_.buildAudioSequenceHeader();
            if (audioHeader.has_value()) {
                enqueuePacket(audioHeader->data(), audioHeader->size(), RTMP_PACKET_TYPE_AUDIO, 0, 0x05);
                muxer_.markAudioSequenceSent();
                LOGD("ensureHeaders audio sequence sent size=%zu", audioHeader->size());
            }
        }

        headersRequested_ = muxer_.hasSentMetadata() && muxer_.hasSentVideoSequence() && muxer_.hasSentAudioSequence();
        LOGD("ensureHeaders complete=%d", headersRequested_ ? 1 : 0);
    }
}

void RTMPPush::onConnecting() {
    LOGD("onConnecting start url=%s", MaskUrl(mRtmpUrl).c_str());
    if (mCallback) {
        mCallback->onConnecting(ThreadContext::Worker);
    }

    if (mRtmp) {
        LOGD("onConnecting release previous RTMP instance=%p", mRtmp);
        release();
    }

    mRtmp = RTMP_Alloc();
    if (!mRtmp) {
        LOGE("RTMP_Alloc failed");
        if (mCallback) {
            mCallback->onConnectFail(RtmpErrorCode::InitFailure);
        }
        release();
        return;
    }

    // Enable verbose librtmp logs to Android logcat for diagnosis
    RTMP_LogSetCallback(RtmpAndroidLogCallback);
    RTMP_LogSetLevel(RTMP_LOGDEBUG);

    RTMP_Init(mRtmp);
    const int setupResult = RTMP_SetupURL(mRtmp, mRtmpUrl);
    if (!setupResult) {
        LOGE("RTMP_SetupURL failed result=%d", setupResult);
        if (mCallback) {
            mCallback->onConnectFail(RtmpErrorCode::UrlSetupFailure);
        }
        release();
        return;
    }

    // Dump parsed URL details for clarity before connecting
    {
        const char* proto = ProtocolName(mRtmp->Link.protocol);
        std::string host = AValToString(mRtmp->Link.hostname);
        std::string app = AValToString(mRtmp->Link.app);
        std::string play = AValToString(mRtmp->Link.playpath);
        std::string tc   = AValToString(mRtmp->Link.tcUrl);
        LOGD("rtmp parsed protocol=%s host=%s port=%u app=%s playpath=%s tcUrl=%s",
             proto, host.c_str(), mRtmp->Link.port, app.c_str(), play.c_str(), tc.c_str());
        if (mRtmp->Link.protocol == RTMP_PROTOCOL_RTMPS || mRtmp->Link.protocol == RTMP_PROTOCOL_RTMPTS) {
            LOGE("RTMPS detected; ensure librtmp built with TLS support (OpenSSL/mbedTLS)");
        }
    }

    mRtmp->Link.timeout = 10;
    RTMP_EnableWrite(mRtmp);
    const int connectResult = RTMP_Connect(mRtmp, nullptr);
    if (!connectResult) {
        LOGE("RTMP_Connect failed result=%d", connectResult);
        if (mCallback) {
            mCallback->onConnectFail(RtmpErrorCode::ConnectFailure);
        }
        release();
        return;
    }

    const int streamResult = RTMP_ConnectStream(mRtmp, 0);
    if (!streamResult) {
        LOGE("RTMP_ConnectStream failed result=%d", streamResult);
        if (mCallback) {
            mCallback->onConnectFail(RtmpErrorCode::ConnectFailure);
        }
        release();
        return;
    }

    mStartTime = RTMP_GetTime();
    LOGD("onConnecting success startTime=%ld", mStartTime);

    if (mCallback) {
        mCallback->onConnectSuccess();
    }

    isPusher = 1;
    headersRequested_ = false;
    LOGD("onConnecting entering send loop");

    while (true) {
        if (!isPusher || !mQueue) {
            LOGD("send loop exiting isPusher=%d queue=%p", isPusher, mQueue);
            release();
            break;
        }
        RTMPPacket* packet = mQueue->getRtmpPacket();
        if (packet != nullptr) {
            packet->m_nInfoField2 = mRtmp->m_stream_id;
            const int result = RTMP_SendPacket(mRtmp, packet, 1);
            if (!result) {
                LOGE("RTMP_SendPacket failed result=%d type=%d size=%d", result, packet->m_packetType, packet->m_nBodySize);
            }
            RTMPPacket_Free(packet);
            std::free(packet);
        }
    }

    LOGE("RTMP connection closed");
}

void RTMPPush::release() {
    LOGD("release rtmp=%p", mRtmp);
    if (!mRtmp) {
        return;
    }
    RTMP_Close(mRtmp);
    RTMP_Free(mRtmp);
    mRtmp = nullptr;
}
