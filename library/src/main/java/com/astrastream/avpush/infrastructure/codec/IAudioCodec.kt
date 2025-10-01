package com.astrastream.avpush.infrastructure.codec

interface IAudioCodec{
    /**
     * 准备编码
     */
    fun start()

    /**
     * 将数据送入编解码器
     */
    fun enqueueCodec(input: ByteArray?);

    /**
     * 停止编码
     */
    fun stop();
}
