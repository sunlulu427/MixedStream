package com.astra.avpush.infrastructure.codec

interface IAudioCodec{
    fun start()
    fun enqueueCodec(input: ByteArray)
    fun stop()
}
