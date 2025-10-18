#include "RTMPPush.h"

#include <cstdlib>
#include <cstring>

RTMPPush::RTMPPush(const char* url, JavaCallback** javaCallback)
    : mQueue(new AVQueue()), mCallback(javaCallback ? *javaCallback : nullptr) {
    if (url != nullptr) {
        const auto length = std::strlen(url);
        mRtmpUrl = new char[length + 1];
        std::memcpy(mRtmpUrl, url, length + 1);
    }
}

RTMPPush::~RTMPPush() {
    stop();
    release();
    delete[] mRtmpUrl;
    mRtmpUrl = nullptr;
    LOGE("RTMPPush destroyed");
}

void RTMPPush::configureVideo(const astra::VideoConfig& config) {
    muxer_.setVideoConfig(config);
    headersRequested_ = false;
}

void RTMPPush::configureAudio(const astra::AudioConfig& config) {
    muxer_.setAudioConfig(config);
    headersRequested_ = false;
}

void RTMPPush::start() {
    if (!mQueue) {
        mQueue = new AVQueue();
    }
    startWorker();
}

void RTMPPush::stop() {
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
        return;
    }

    ensureHeaders();

    std::vector<uint8_t> payload = muxer_.buildVideoTag(frame);
    if (payload.empty()) {
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
        return;
    }

    ensureHeaders();
    std::vector<uint8_t> payload = muxer_.buildAudioTag(data, length);
    if (payload.empty()) {
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
    onConnecting();
}

void RTMPPush::enqueuePacket(const uint8_t* data,
                             size_t length,
                             uint8_t packetType,
                             uint32_t timestamp,
                             uint8_t channel) {
    if (!mQueue || !data || length == 0) {
        return;
    }

    auto* packet = static_cast<RTMPPacket*>(std::malloc(sizeof(RTMPPacket)));
    if (!packet) {
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
}

void RTMPPush::ensureHeaders() {
    if (!headersRequested_) {
        if (!muxer_.hasSentMetadata()) {
            auto metadata = muxer_.buildMetadataTag();
            if (metadata.has_value()) {
                enqueuePacket(metadata->data(), metadata->size(), RTMP_PACKET_TYPE_INFO, 0, 0x03);
                muxer_.markMetadataSent();
            }
        }

        if (!muxer_.hasSentVideoSequence()) {
            auto videoHeader = muxer_.buildVideoSequenceHeader();
            if (videoHeader.has_value()) {
                enqueuePacket(videoHeader->data(), videoHeader->size(), RTMP_PACKET_TYPE_VIDEO, 0, 0x04);
            }
        }

        if (!muxer_.hasSentAudioSequence()) {
            auto audioHeader = muxer_.buildAudioSequenceHeader();
            if (audioHeader.has_value()) {
                enqueuePacket(audioHeader->data(), audioHeader->size(), RTMP_PACKET_TYPE_AUDIO, 0, 0x05);
                muxer_.markAudioSequenceSent();
            }
        }

        headersRequested_ = muxer_.hasSentMetadata() && muxer_.hasSentVideoSequence() && muxer_.hasSentAudioSequence();
    }
}

void RTMPPush::onConnecting() {
    if (mCallback) {
        mCallback->onConnecting(ThreadContext::Worker);
    }

    if (mRtmp) {
        release();
    }

    mRtmp = RTMP_Alloc();
    if (!mRtmp) {
        if (mCallback) {
            mCallback->onConnectFail(RtmpErrorCode::InitFailure);
        }
        release();
        return;
    }

    RTMP_Init(mRtmp);
    const int setupResult = RTMP_SetupURL(mRtmp, mRtmpUrl);
    if (!setupResult) {
        if (mCallback) {
            mCallback->onConnectFail(RtmpErrorCode::UrlSetupFailure);
        }
        release();
        return;
    }

    mRtmp->Link.timeout = 5;
    RTMP_EnableWrite(mRtmp);
    const int connectResult = RTMP_Connect(mRtmp, nullptr);
    if (!connectResult) {
        if (mCallback) {
            mCallback->onConnectFail(RtmpErrorCode::ConnectFailure);
        }
        release();
        return;
    }

    const int streamResult = RTMP_ConnectStream(mRtmp, 0);
    if (!streamResult) {
        if (mCallback) {
            mCallback->onConnectFail(RtmpErrorCode::ConnectFailure);
        }
        release();
        return;
    }

    mStartTime = RTMP_GetTime();

    if (mCallback) {
        mCallback->onConnectSuccess();
    }

    isPusher = 1;
    headersRequested_ = false;

    while (true) {
        if (!isPusher || !mQueue) {
            release();
            break;
        }
        RTMPPacket* packet = mQueue->getRtmpPacket();
        if (packet != nullptr) {
            packet->m_nInfoField2 = mRtmp->m_stream_id;
            const int result = RTMP_SendPacket(mRtmp, packet, 1);
            if (!result) {
                LOGE("RTMP_SendPacket result is %d", result);
            }
            RTMPPacket_Free(packet);
            std::free(packet);
        }
    }

    LOGE("RTMP connection closed");
}

void RTMPPush::release() {
    if (!mRtmp) {
        return;
    }
    RTMP_Close(mRtmp);
    RTMP_Free(mRtmp);
    mRtmp = nullptr;
}
