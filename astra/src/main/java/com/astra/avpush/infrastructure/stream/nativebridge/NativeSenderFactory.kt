package com.astra.avpush.infrastructure.stream.nativebridge

import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.unified.ProtocolDetector
import com.astra.avpush.unified.TransportProtocol

object NativeSenderFactory {

    private const val TAG = "NativeSenderFactory"

    fun create(streamUrl: String): NativeSender {
        val protocol = ProtocolDetector.detectProtocol(streamUrl)
        return createForProtocol(protocol)
    }

    fun createForProtocol(protocol: TransportProtocol): NativeSender {
        AstraLog.d(TAG) { "resolve native sender for protocol=${protocol.displayName}" }
        return when (protocol) {
            TransportProtocol.RTMP -> newSender(protocol)
            else -> throw UnsupportedOperationException(
                "Protocol ${protocol.displayName} is not supported natively yet"
            )
        }
    }

    private fun newSender(protocol: TransportProtocol): NativeSender {
        val handle = NativeSenderHandleGenerator.next()
        NativeSenderBridge.nativeCreateSender(handle, protocol.ordinal)
        return NativeSender(handle = handle, protocol = protocol)
    }
}
