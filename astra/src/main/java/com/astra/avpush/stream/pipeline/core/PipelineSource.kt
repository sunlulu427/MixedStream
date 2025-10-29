package com.astra.avpush.stream.pipeline.core

import java.util.LinkedHashSet


abstract class PipelineSource<T>(
    override val name: String,
    override val role: PipelineRole = PipelineRole.SOURCE
) : PipelineStage {

    private val pads = LinkedHashSet<PipelinePad<T>>()

    fun connect(pad: PipelinePad<T>) {
        pads += pad
    }

    fun disconnect(pad: PipelinePad<T>) {
        pads -= pad
    }

    protected fun emit(frame: T) {
        pads.forEach { it.push(frame) }
    }

    override fun release() {
        pads.clear()
    }
}
