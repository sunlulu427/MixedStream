package com.astrastream.avpush.stream.pipeline

import com.astrastream.avpush.stream.pipeline.core.PipelineStage

class StreamingPipeline {

    private val stages = ArrayList<PipelineStage>()

    fun add(stage: PipelineStage): StreamingPipeline {
        require(stages.none { it.name == stage.name }) { "Stage ${stage.name} already exists" }
        stages += stage
        return this
    }

    fun addAll(vararg stage: PipelineStage): StreamingPipeline {
        stage.forEach { add(it) }
        return this
    }

    fun start() {
        stages.forEach { it.start() }
    }

    fun pause() {
        stages.asReversed().forEach { it.pause() }
    }

    fun resume() {
        stages.forEach { it.resume() }
    }

    fun stop() {
        stages.asReversed().forEach { it.stop() }
    }

    fun shutdown() {
        stop()
        release()
    }

    fun release() {
        stages.asReversed().forEach { it.release() }
        stages.clear()
    }

    fun isEmpty(): Boolean = stages.isEmpty()
}
