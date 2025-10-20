package com.astrastream.avpush.stream.controller

import com.astrastream.avpush.domain.callback.IController
import com.astrastream.avpush.infrastructure.camera.Watermark

interface VideoSourceController : IController {
    fun setWatermark(watermark: Watermark)
}
