package com.astra.avpush.stream.controller

import com.astra.avpush.domain.callback.IController
import com.astra.avpush.infrastructure.camera.Watermark

interface VideoSourceController : IController {
    fun setWatermark(watermark: Watermark)
}
