package com.astra.avpush.stream.pipeline.core

fun interface PipelinePad<T> {
    fun push(frame: T)
}
