package com.astra.avpush.infrastructure.stream.nativebridge

import com.astra.avpush.domain.callback.OnConnectListener
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.runtime.RtmpErrorCode
import java.util.concurrent.ConcurrentHashMap

internal object NativeSenderRegistry {

    private data class SenderCallbacks(
        @Volatile var connectListener: OnConnectListener? = null,
        @Volatile var statsListener: ((Int, Int) -> Unit)? = null
    )

    private val callbacks = ConcurrentHashMap<Long, SenderCallbacks>()

    fun register(handle: Long) {
        callbacks.putIfAbsent(handle, SenderCallbacks())
    }

    fun unregister(handle: Long) {
        callbacks.remove(handle)
    }

    fun updateConnectListener(handle: Long, listener: OnConnectListener?) {
        callbacks.compute(handle) { _, existing ->
            (existing ?: SenderCallbacks()).apply { connectListener = listener }
        }
    }

    fun updateStatsListener(handle: Long, listener: ((Int, Int) -> Unit)?) {
        callbacks.compute(handle) { _, existing ->
            (existing ?: SenderCallbacks()).apply { statsListener = listener }
        }
    }

    fun onConnecting(handle: Long) {
        callbacks[handle]?.connectListener?.onConnecting()
    }

    fun onConnected(handle: Long) {
        callbacks[handle]?.connectListener?.onConnected()
    }

    fun onClosed(handle: Long) {
        callbacks[handle]?.connectListener?.onClose()
    }

    fun onError(handle: Long, errorCode: Int) {
        val readable = RtmpErrorCode.fromCode(errorCode)?.let { code ->
            when (code) {
                RtmpErrorCode.CONNECT_FAILURE -> "RTMP server connection failed"
                RtmpErrorCode.INIT_FAILURE -> "RTMP native initialization failed"
                RtmpErrorCode.URL_SETUP_FAILURE -> "RTMP URL setup failed"
            }
        } ?: "Unknown streaming error"
        AstraLog.e("NativeSenderRegistry") { "native error=$errorCode $readable" }
        callbacks[handle]?.connectListener?.onFail(readable)
    }

    fun onStats(handle: Long, bitrateKbps: Int, fps: Int) {
        callbacks[handle]?.statsListener?.invoke(bitrateKbps, fps)
    }
}
