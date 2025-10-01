package com.astrastream.avpush.infrastructure.stream

enum class PacketType(val type: Int) {
    FIRST_AUDIO(1),
    FIRST_VIDEO(2),
    SPS_PPS(3),
    AUDIO(4),
    KEY_FRAME(5),
    VIDEO(6);
}
