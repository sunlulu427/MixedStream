package com.devyk.av.rtmp.library.stream.sender

import com.devyk.av.rtmp.library.stream.PacketType

interface Sender {
    fun onData(data: ByteArray, type: PacketType)
    fun onData(sps: ByteArray, pps: ByteArray, type: PacketType) {}
}