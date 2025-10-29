package com.astra.avpush.runtime

internal object NativeShaders {

    init {
        System.loadLibrary("astra")
    }

    fun script(id: Int): String = nativeGetScript(id) ?: ""

    private external fun nativeGetScript(id: Int): String?
}
