package com.astrastream.avpush.core.pipeline

fun interface PipelinePad<T> {
    fun push(frame: T)
}
