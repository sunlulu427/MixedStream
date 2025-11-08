package com.astra.avpush.infrastructure.camera

import android.graphics.SurfaceTexture
import android.view.Surface
import com.astra.avpush.domain.camera.CameraDescriptor
import com.astra.avpush.domain.camera.CameraDevice
import com.astra.avpush.domain.config.CameraConfiguration
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.unified.MediaError

class NativeCameraDevice : CameraDevice {

    companion object {
        init {
            System.loadLibrary("astra")
        }
    }

    override val currentDescriptor: CameraDescriptor?
        get() = cachedDescriptor

    private var cachedDescriptor: CameraDescriptor? = null
    private var texture: SurfaceTexture? = null
    private var previewSurface: Surface? = null

    override fun configure(configuration: CameraConfiguration) {
        nativeConfigure(
            configuration.width,
            configuration.height,
            configuration.fps,
            configuration.facing.ordinal,
            configuration.orientation.ordinal,
            configuration.rotation,
            configuration.focusMode.ordinal
        )
    }

    override fun open() {
        if (!nativeOpenCamera()) {
            throw MediaError.CameraUnavailable(detail = "Camera open failed")
        }
        cachedDescriptor = buildDescriptor(nativeCurrentDescriptor())
            ?: throw MediaError.CameraUnavailable(detail = "Camera descriptor unavailable")
    }

    override fun bind(surfaceTexture: SurfaceTexture, listener: SurfaceTexture.OnFrameAvailableListener?) {
        texture?.setOnFrameAvailableListener(null)
        texture = surfaceTexture
        texture?.setOnFrameAvailableListener(listener)
        previewSurface?.release()
        previewSurface = Surface(surfaceTexture)
        if (!nativeSetSurface(previewSurface)) {
            throw MediaError.CameraHardwareFailure(detail = "Attach preview surface failed")
        }
    }

    override fun bind(textureId: Int, listener: SurfaceTexture.OnFrameAvailableListener?) {
        bind(SurfaceTexture(textureId), listener)
    }

    override fun startPreview() {
        if (!nativeStartPreview()) {
            throw MediaError.CameraHardwareFailure(detail = "Start preview failed")
        }
    }

    override fun stopPreview() {
        nativeStopPreview()
    }

    override fun releaseCamera() {
        nativeReleaseCamera()
    }

    override fun releaseResources() {
        nativeSetSurface(null)
        previewSurface?.release()
        previewSurface = null
        texture?.setOnFrameAvailableListener(null)
        texture?.release()
        texture = null
        cachedDescriptor = null
    }

    override fun switchCamera(): Boolean {
        val switched = nativeSwitchCamera()
        if (switched) {
            cachedDescriptor = buildDescriptor(nativeCurrentDescriptor())
        }
        return switched
    }

    override fun updateTexImage() {
        try {
            texture?.updateTexImage()
        } catch (error: Throwable) {
            AstraLog.e("NativeCameraDevice", "updateTexImage failed: ${error.message}")
        }
    }

    private fun buildDescriptor(values: IntArray?): CameraDescriptor? {
        if (values == null || values.size < 8) return null
        val facing = if (values[1] == 0) {
            CameraDescriptor.Facing.FRONT
        } else {
            CameraDescriptor.Facing.BACK
        }
        return CameraDescriptor(
            id = values[0],
            facing = facing,
            previewWidth = values[2],
            previewHeight = values[3],
            orientation = values[4],
            hasFlash = values[5] != 0,
            supportsTouchFocus = values[6] != 0,
            touchFocusEnabled = values[7] != 0
        )
    }

    private external fun nativeConfigure(
        width: Int,
        height: Int,
        fps: Int,
        facingOrdinal: Int,
        orientationOrdinal: Int,
        rotation: Int,
        focusModeOrdinal: Int
    )

    private external fun nativeOpenCamera(): Boolean
    private external fun nativeSetSurface(surface: Surface?): Boolean
    private external fun nativeStartPreview(): Boolean
    private external fun nativeStopPreview()
    private external fun nativeReleaseCamera()
    private external fun nativeSwitchCamera(): Boolean
    private external fun nativeCurrentDescriptor(): IntArray?
}
