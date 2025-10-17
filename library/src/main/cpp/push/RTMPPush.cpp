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
    LOGE("RTMPPush destroyed");
}

void RTMPPush::start() {
    IThread::start();
}

void RTMPPush::stop() {
    isPusher = 0;
    if (!mQueue) {
        IThread::stop();
        return;
    }
    mQueue->notifyQueue();
    IThread::stop();
    mQueue->clearQueue();
    delete mQueue;
    mQueue = nullptr;
    delete[] mRtmpUrl;
    mRtmpUrl = nullptr;
}

void RTMPPush::main() {
    onConnecting();
}

void RTMPPush::pushSpsPps(uint8_t* sps, int sps_len, uint8_t* pps, int pps_len) {
    const int bodySize = sps_len + pps_len + 16;
    auto* packet = static_cast<RTMPPacket*>(std::malloc(sizeof(RTMPPacket)));
    if (packet == nullptr) {
        return;
    }
    RTMPPacket_Alloc(packet, bodySize);
    RTMPPacket_Reset(packet);

    char* body = packet->m_body;
    int index = 0;

    body[index++] = 0x17;
    body[index++] = 0x00;
    body[index++] = 0x00;
    body[index++] = 0x00;
    body[index++] = 0x00;

    body[index++] = 0x01;
    body[index++] = sps[1];
    body[index++] = sps[2];
    body[index++] = sps[3];

    body[index++] = 0xFF;
    body[index++] = 0xE1;
    body[index++] = (sps_len >> 8) & 0xff;
    body[index++] = sps_len & 0xff;
    std::memcpy(&body[index], sps, sps_len);
    index += sps_len;

    body[index++] = 0x01;
    body[index++] = (pps_len >> 8) & 0xff;
    body[index++] = pps_len & 0xff;
    std::memcpy(&body[index], pps, pps_len);

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = bodySize;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_nInfoField2 = mRtmp->m_stream_id;

    mQueue->putRtmpPacket(packet);
}

void RTMPPush::pushAudioData(uint8_t* data, int data_len, int /*type*/) {
    auto* packet = static_cast<RTMPPacket*>(std::malloc(sizeof(RTMPPacket)));
    if (packet == nullptr) {
        return;
    }
    RTMPPacket_Alloc(packet, data_len);
    RTMPPacket_Reset(packet);
    packet->m_nChannel = 0x05;
    std::memcpy(packet->m_body, data, data_len);
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = data_len;
    packet->m_nTimeStamp = RTMP_GetTime() - mStartTime;
    mQueue->putRtmpPacket(packet);
}

void RTMPPush::pushVideoData(uint8_t* data, int data_len, int /*type*/) {
    auto* packet = static_cast<RTMPPacket*>(std::malloc(sizeof(RTMPPacket)));
    if (packet == nullptr) {
        return;
    }
    RTMPPacket_Alloc(packet, data_len);
    RTMPPacket_Reset(packet);
    packet->m_nChannel = 0x04;
    std::memcpy(packet->m_body, data, data_len);
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = data_len;
    packet->m_nTimeStamp = RTMP_GetTime() - mStartTime;
    mQueue->putRtmpPacket(packet);
}

void RTMPPush::onConnecting() {
    if (mCallback) {
        mCallback->onConnecting(THREAD_CHILD);
    }

    if (mRtmp) {
        release();
    }

    mRtmp = RTMP_Alloc();
    if (!mRtmp) {
        if (mCallback) {
            mCallback->onConnectFail(RTMP_INIT_ERROR);
        }
        release();
        return;
    }

    RTMP_Init(mRtmp);
    const int setupResult = RTMP_SetupURL(mRtmp, mRtmpUrl);
    if (!setupResult) {
        if (mCallback) {
            mCallback->onConnectFail(RTMP_SET_URL_ERROR);
        }
        release();
        return;
    }

    mRtmp->Link.timeout = 5;
    RTMP_EnableWrite(mRtmp);
    const int connectResult = RTMP_Connect(mRtmp, nullptr);
    if (!connectResult) {
        if (mCallback) {
            mCallback->onConnectFail(RTMP_CONNECT_ERROR);
        }
        release();
        return;
    }

    const int streamResult = RTMP_ConnectStream(mRtmp, 0);
    if (!streamResult) {
        if (mCallback) {
            mCallback->onConnectFail(RTMP_CONNECT_ERROR);
        }
        release();
        return;
    }

    mStartTime = RTMP_GetTime();

    if (mCallback) {
        mCallback->onConnectSuccess();
    }

    isPusher = 1;

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
