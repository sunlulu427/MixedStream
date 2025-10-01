package com.astrastream.avpush.infrastructure.stream.sender

import com.astrastream.avpush.infrastructure.stream.PacketType

interface Sender {
    fun onData(data: ByteArray, type: PacketType)
    fun onData(sps: ByteArray, pps: ByteArray, type: PacketType) {}
}
