package com.astrastream.avpush.stream.pipeline.core

interface PipelineStage {
    val name: String
    val role: PipelineRole

    fun start()

    fun pause()

    fun resume()

    fun stop()

    fun release()
}
