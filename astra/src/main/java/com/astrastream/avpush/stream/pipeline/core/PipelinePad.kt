package com.astrastream.avpush.stream.pipeline.core

fun interface PipelinePad<T> {
    fun push(frame: T)
}
