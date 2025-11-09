package com.astra.avpush.domain

import android.graphics.SurfaceTexture

interface CameraDevice {
    val currentDescriptor: CameraDescriptor?

    fun configure(configuration: CameraConfiguration)

    @Throws(Throwable::class)
    fun open()

    fun bind(surfaceTexture: SurfaceTexture, listener: SurfaceTexture.OnFrameAvailableListener?)

    fun bind(textureId: Int, listener: SurfaceTexture.OnFrameAvailableListener?)

    fun startPreview()

    fun stopPreview()

    fun releaseCamera()

    fun releaseResources()

    fun switchCamera(): Boolean

    fun updateTexImage()
}
