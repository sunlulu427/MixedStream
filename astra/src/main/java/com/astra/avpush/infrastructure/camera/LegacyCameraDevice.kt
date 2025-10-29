package com.astra.avpush.infrastructure.camera

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.SystemClock
import android.util.Log
import com.astra.avpush.domain.camera.CameraDescriptor
import com.astra.avpush.domain.camera.CameraDevice
import com.astra.avpush.domain.config.CameraConfiguration
import com.astra.avpush.runtime.LogHelper
import java.io.IOException

class LegacyCameraDevice : CameraDevice {

    private enum class DeviceState { IDLE, OPENED, PREVIEWING }

    private var state: DeviceState = DeviceState.IDLE
    private var configuration: CameraConfiguration = CameraConfiguration()
    private var enableTouchFocus = false
    private var preferBackCamera = true
    private var camera: Camera? = null
    private var texture: SurfaceTexture? = null
    private var frameCount = 0
    private var lastFrameLogAt = 0L
    private var descriptors: MutableList<CameraData> = mutableListOf()
    private var currentData: CameraData? = null

    override val currentDescriptor: CameraDescriptor?
        get() = currentData?.toDescriptor()

    override fun configure(configuration: CameraConfiguration) {
        this.configuration = configuration
        enableTouchFocus = configuration.focusMode != CameraConfiguration.FocusMode.AUTO
        preferBackCamera = configuration.facing != CameraConfiguration.Facing.FRONT
    }

    @Synchronized
    override fun open() {
        ensureDescriptors()
        if (descriptors.isEmpty()) {
            throw CameraNotSupportException()
        }
        val iterator = descriptors.iterator()
        var lastError: RuntimeException? = null
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            try {
                Log.d(TAG, "open camera ${candidate.cameraId}")
                camera = Camera.open(candidate.cameraId)
                camera?.let { device ->
                    CameraUtils.initCameraParams(device, candidate, enableTouchFocus, configuration)
                    currentData = candidate
                    state = DeviceState.OPENED
                    return
                }
            } catch (error: RuntimeException) {
                lastError = error
                camera?.safeRelease()
                camera = null
            }
        }
        state = DeviceState.IDLE
        currentData = null
        if (lastError != null) {
            throw CameraHardwareException(lastError)
        } else {
            throw CameraNotSupportException()
        }
    }

    override fun bind(surfaceTexture: SurfaceTexture, listener: SurfaceTexture.OnFrameAvailableListener?) {
        texture = surfaceTexture
        applySurface(listener)
    }

    override fun bind(textureId: Int, listener: SurfaceTexture.OnFrameAvailableListener?) {
        texture = SurfaceTexture(textureId)
        Log.d(TAG, "bind texture id=$textureId")
        applySurface(listener)
    }

    @Synchronized
    override fun startPreview() {
        if (state != DeviceState.OPENED) {
            Log.d(TAG, "startPreview ignored: state=$state")
            return
        }
        val device = camera ?: run {
            Log.d(TAG, "startPreview ignored: camera=null")
            return
        }
        val targetTexture = texture ?: run {
            Log.d(TAG, "startPreview ignored: texture=null")
            return
        }
        try {
            device.setPreviewTexture(targetTexture)
            device.startPreview()
            state = DeviceState.PREVIEWING
            frameCount = 0
            lastFrameLogAt = 0L
            device.parameters?.previewSize?.let { size ->
                LogHelper.i(TAG, "startPreview success -> actual ${size.width}x${size.height}")
            }
        } catch (error: Exception) {
            LogHelper.e(TAG, "startPreview failed: ${error.message}")
            releaseCamera()
            throw error
        }
    }

    @Synchronized
    override fun stopPreview() {
        if (state != DeviceState.PREVIEWING) return
        val device = camera ?: return
        try {
            device.setPreviewCallback(null)
            val parameters = device.parameters
            if (parameters?.flashMode != null && parameters.flashMode != Camera.Parameters.FLASH_MODE_OFF) {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
                device.parameters = parameters
            }
            device.stopPreview()
            state = DeviceState.OPENED
        } catch (error: Exception) {
            LogHelper.e(TAG, "stopPreview failed: ${error.message}")
        }
    }

    @Synchronized
    override fun releaseCamera() {
        if (state == DeviceState.PREVIEWING) {
            stopPreview()
        }
        camera.safeRelease()
        camera = null
        currentData = null
        state = DeviceState.IDLE
    }

    @Synchronized
    override fun releaseResources() {
        releaseCamera()
        texture?.setOnFrameAvailableListener(null)
        texture?.release()
        texture = null
        descriptors.clear()
    }

    @Synchronized
    override fun switchCamera(): Boolean {
        if (descriptors.size <= 1) {
            return false
        }
        if (state == DeviceState.PREVIEWING) {
            stopPreview()
        }
        if (descriptors.isNotEmpty()) {
            val cameraProfile = descriptors.removeAt(0)
            descriptors.add(cameraProfile)
        }
        releaseCamera()
        return try {
            open()
            true
        } catch (error: Throwable) {
            LogHelper.e(TAG, "switchCamera failed: ${error.message}")
            false
        }
    }

    override fun updateTexImage() {
        val targetTexture = texture
        if (targetTexture == null) {
            LogHelper.w(TAG, "updateTexImage skipped: texture=null state=$state")
            return
        }
        try {
            targetTexture.updateTexImage()
            frameCount += 1
            val now = SystemClock.elapsedRealtime()
            if (frameCount <= 5 || now - lastFrameLogAt >= FRAME_SAMPLE_WINDOW_MS) {
                LogHelper.d(
                    TAG,
                    "updateTexImage frame=$frameCount timestampNs=${targetTexture.timestamp} state=$state"
                )
                lastFrameLogAt = now
            }
        } catch (error: Throwable) {
            LogHelper.e(TAG, "updateTexImage failed: ${error.message}")
            throw error
        }
    }

    fun setFocusPoint(x: Int, y: Int) {
        if (state != DeviceState.PREVIEWING) return
        val device = camera ?: return
        if (x < -1000 || x > 1000 || y < -1000 || y > 1000) {
            Log.w(TAG, "setFocusPoint out of range x=$x y=$y")
            return
        }
        val params = device.parameters ?: return
        if (params.maxNumFocusAreas <= 0) {
            Log.w(TAG, "Touch focus not supported")
            return
        }
        val area = Camera.Area(Rect(x, y, x + FOCUS_AREA_SIZE, y + FOCUS_AREA_SIZE), 1000)
        params.focusAreas = arrayListOf(area)
        try {
            device.parameters = params
        } catch (_: Exception) {
        }
    }

    fun doAutofocus(callback: Camera.AutoFocusCallback): Boolean {
        if (state != DeviceState.PREVIEWING) return false
        val device = camera ?: return false
        val params = device.parameters ?: return false
        if (params.isAutoExposureLockSupported) params.autoExposureLock = false
        if (params.isAutoWhiteBalanceLockSupported) params.autoWhiteBalanceLock = false
        device.parameters = params
        device.cancelAutoFocus()
        device.autoFocus(callback)
        return true
    }

    fun toggleTouchFocus(enable: Boolean) {
        if (state != DeviceState.PREVIEWING) return
        val device = camera ?: return
        enableTouchFocus = enable
        if (enable) {
            CameraUtils.setTouchFocusMode(device)
        } else {
            CameraUtils.setAutoFocusMode(device)
        }
    }

    fun adjustZoom(enlarge: Boolean): Float {
        if (state != DeviceState.PREVIEWING) return -1f
        val device = camera ?: return -1f
        val params = device.parameters ?: return -1f
        params.zoom = if (enlarge) {
            (params.zoom + 1).coerceAtMost(params.maxZoom)
        } else {
            (params.zoom - 1).coerceAtLeast(0)
        }
        device.parameters = params
        return params.zoom.toFloat() / params.maxZoom
    }

    fun toggleTorch(): Boolean {
        if (state != DeviceState.PREVIEWING) return false
        val device = camera ?: return false
        val info = currentData ?: return false
        if (!info.hasFlash) return false
        val params = device.parameters ?: return false
        params.flashMode = if (params.flashMode == Camera.Parameters.FLASH_MODE_TORCH) {
            Camera.Parameters.FLASH_MODE_OFF
        } else {
            Camera.Parameters.FLASH_MODE_TORCH
        }
        return try {
            device.parameters = params
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun applySurface(listener: SurfaceTexture.OnFrameAvailableListener?) {
        val targetTexture = texture ?: return
        try {
            currentData?.let {
                try {
                    targetTexture.setDefaultBufferSize(it.width, it.height)
                } catch (error: Throwable) {
                    LogHelper.w(TAG, "setDefaultBufferSize failed: ${error.message}")
                }
            }
            camera?.setPreviewTexture(targetTexture)
            targetTexture.setOnFrameAvailableListener(listener)
            LogHelper.d(TAG, "surface bound -> descriptor=${currentDescriptor}")
        } catch (error: IOException) {
            LogHelper.e(TAG, "bind surface failed: ${error.message}")
            releaseCamera()
        }
    }

    private fun ensureDescriptors() {
        if (descriptors.isNotEmpty()) return
        descriptors = CameraUtils.getAllCamerasData(preferBackCamera) ?: mutableListOf()
    }

    private fun Camera?.safeRelease() {
        try {
            this?.setPreviewCallback(null)
            this?.stopPreview()
        } catch (_: Exception) {
        }
        try {
            this?.release()
        } catch (_: Exception) {
        }
    }

    private fun CameraData.toDescriptor(): CameraDescriptor {
        val facing = if (facing == CameraData.FACING_FRONT) {
            CameraDescriptor.Facing.FRONT
        } else {
            CameraDescriptor.Facing.BACK
        }
        return CameraDescriptor(
            id = cameraId,
            facing = facing,
            previewWidth = width,
            previewHeight = height,
            orientation = orientation,
            hasFlash = hasFlash,
            supportsTouchFocus = supportTouchFocus,
            touchFocusEnabled = touchFocusMode
        )
    }

    companion object {
        private const val TAG = "LegacyCameraDevice"
        private const val FRAME_SAMPLE_WINDOW_MS = 2_000L
        private const val FOCUS_AREA_SIZE = 80
    }
}
