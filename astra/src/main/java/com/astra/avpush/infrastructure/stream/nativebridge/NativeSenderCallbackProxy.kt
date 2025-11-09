package com.astra.avpush.infrastructure.stream.nativebridge

internal class NativeSenderCallbackProxy(private val handle: Long) {

    fun onConnecting() {
        NativeSenderRegistry.onConnecting(handle)
    }

    fun onConnected() {
        NativeSenderRegistry.onConnected(handle)
    }

    fun onClose() {
        NativeSenderRegistry.onClosed(handle)
    }

    fun onError(errorCode: Int) {
        NativeSenderRegistry.onError(handle, errorCode)
    }

    fun onStreamStats(bitrateKbps: Int, fps: Int) {
        NativeSenderRegistry.onStats(handle, bitrateKbps, fps)
    }
}
