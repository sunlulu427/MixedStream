package com.astra.avpush.infrastructure.stream.sender

import com.astra.avpush.infrastructure.stream.sender.rtmp.RtmpSender
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.unified.ProtocolDetector
import com.astra.avpush.unified.config.TransportProtocol

/**
 * Factory responsible for creating protocol-specific [Sender] implementations.
 *
 * The business layer only interacts with the common [Sender] abstraction while
 * the factory resolves the concrete implementation based on the publish URL.
 */
object SenderFactory {

    private const val TAG = "SenderFactory"

    /**
     * Create a [Sender] for the provided streaming url.
     *
     * @throws IllegalArgumentException when the protocol cannot be determined.
     * @throws UnsupportedOperationException when the protocol is not yet supported.
     */
    fun create(streamUrl: String): Sender {
        val protocol = ProtocolDetector.detectProtocol(streamUrl)
        return createForProtocol(protocol)
    }

    /**
     * Create a [Sender] for the provided [TransportProtocol].
     */
    fun createForProtocol(protocol: TransportProtocol): Sender {
        AstraLog.d(TAG, "Resolving sender for protocol=${protocol.displayName}")
        return when (protocol) {
            TransportProtocol.RTMP -> RtmpSender()
            else -> throw UnsupportedOperationException(
                "Protocol ${protocol.displayName} is not supported yet"
            )
        }
    }
}
