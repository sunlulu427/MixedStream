package com.astrastream.avpush.runtime

internal object NativeStats {

    init {
        System.loadLibrary("astra")
    }

    fun create(): Long = nativeCreate()

    fun release(handle: Long) {
        if (handle != 0L) nativeRelease(handle)
    }

    fun reset(handle: Long, timestampMs: Long) {
        if (handle != 0L) nativeReset(handle, timestampMs)
    }

    fun onVideoSample(handle: Long, sizeBytes: Int, timestampMs: Long): IntArray? {
        if (handle == 0L) return null
        return nativeOnVideoSample(handle, sizeBytes, timestampMs)
    }

    private external fun nativeCreate(): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeReset(handle: Long, timestampMs: Long)
    private external fun nativeOnVideoSample(handle: Long, bytes: Int, timestampMs: Long): IntArray?
}
