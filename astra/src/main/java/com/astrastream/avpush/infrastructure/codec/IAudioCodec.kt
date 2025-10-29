package com.astrastream.avpush.infrastructure.codec

interface IAudioCodec{
    fun start()
    fun enqueueCodec(input: ByteArray)
    fun stop()
}
