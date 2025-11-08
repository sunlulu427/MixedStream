package com.astra.avpush.infrastructure.camera.renderer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.os.SystemClock
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.presentation.widget.GlRenderer
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.runtime.BitmapUtils

class CameraRenderer : GlRenderer {

    companion object {
        init {
            System.loadLibrary("astra")
        }
    }

    private val tag = javaClass.simpleName
    private var nativeHandle = 0L
    private var rendererListener: OnRendererListener? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var outputTextureId = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    @Volatile
    private var pendingWatermark: Watermark? = null
    @Volatile
    private var currentWatermark: Watermark? = null
    private var cachedBitmap: Bitmap? =
        BitmapUtils.createBitmapByCanvas("  ", 20f, Color.WHITE, Color.TRANSPARENT)
    private val matrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private var drawFrameCount = 0L
    private var lastDrawLogTimestamp = 0L

    @Volatile
    private var isTextureReady = true
    private var switchCameraListener: OnSwitchCameraListener? = null

    override fun onSurfaceCreate(width: Int, height: Int) {
        AstraLog.d(tag, "onSurfaceCreate w=$width h=$height")
        drawFrameCount = 0
        lastDrawLogTimestamp = 0L
        val textures = nativeOnSurfaceCreate(ensureHandle(), width, height)
        if (textures == null || textures.size < 2) {
            AstraLog.e(tag, "nativeOnSurfaceCreate returned invalid texture ids")
            return
        }
        val cameraTextureId = textures[0]
        outputTextureId = textures[1]
        surfaceTexture?.setOnFrameAvailableListener(null)
        surfaceTexture?.release()
        surfaceTexture = SurfaceTexture(cameraTextureId).apply {
            try {
                setDefaultBufferSize(width, height)
            } catch (_: Throwable) {
            }
        }
        rendererListener?.onCreate(cameraTextureId, outputTextureId)
        surfaceTexture?.let { rendererListener?.onCreate(it, outputTextureId) }
        surfaceWidth = width
        surfaceHeight = height
        nativeUpdateMatrix(nativeHandle, matrix)
        attemptApplyWatermark()
    }

    override fun onSurfaceChange(width: Int, height: Int) {
        val handle = ensureHandle()
        nativeOnSurfaceChanged(handle, width, height)
        val sizeChanged = surfaceWidth != width || surfaceHeight != height
        surfaceWidth = width
        surfaceHeight = height
        if (sizeChanged && pendingWatermark == null) {
            pendingWatermark = currentWatermark
        }
        attemptApplyWatermark()
        AstraLog.d(tag, "surface changed width=$width height=$height")
    }

    override fun onDraw() {
        val handle = ensureHandle()
        if (!isTextureReady) {
            if (drawFrameCount == 0L) {
                AstraLog.d(tag, "onDraw skip while switching camera")
            }
            Thread.sleep(300)
            isTextureReady = switchCameraListener?.onChange() ?: true
            if (!isTextureReady) return
        }
        attemptApplyWatermark()
        rendererListener?.onDraw()
        nativeOnDraw(handle)
        drawFrameCount += 1
        val now = SystemClock.elapsedRealtime()
        if (drawFrameCount <= 5 || now - lastDrawLogTimestamp >= 2000) {
            AstraLog.d(tag, "onDraw frame=$drawFrameCount outputTex=$outputTextureId")
            lastDrawLogTimestamp = now
        }
    }

    fun setWatemark(watermark: Watermark) {
        pendingWatermark = watermark
        attemptApplyWatermark()
    }

    interface OnRendererListener {
        fun onCreate(cameraTextureId: Int, textureID: Int)
        fun onCreate(surfaceTexture: SurfaceTexture, textureID: Int)
        fun onDraw()
    }

    fun setOnRendererListener(listener: OnRendererListener) {
        this.rendererListener = listener
    }

    fun setAngle(angle: Int, x: Int, y: Int, z: Int) {
        Matrix.rotateM(matrix, 0, angle.toFloat(), x.toFloat(), y.toFloat(), z.toFloat())
        nativeUpdateMatrix(ensureHandle(), matrix)
    }

    fun resetMatrix() {
        Matrix.setIdentityM(matrix, 0)
        nativeUpdateMatrix(ensureHandle(), matrix)
    }

    fun switchCamera(listener: OnSwitchCameraListener) {
        isTextureReady = false
        this.switchCameraListener = listener
    }

    fun release() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        surfaceTexture?.release()
        surfaceTexture = null
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private fun attemptApplyWatermark() {
        val watermark = pendingWatermark ?: return
        if (surfaceWidth == 0 || surfaceHeight == 0) {
            return
        }
        if (applyWatermark(watermark)) {
            pendingWatermark = null
        }
    }

    private fun applyWatermark(watermark: Watermark): Boolean {
        val bitmap = prepareBitmap(watermark)
        val coords = watermark.floatArray?.takeIf { it.size >= 8 }?.copyOf(8)

        val handle = ensureHandle()
        nativeApplyWatermark(handle, bitmap, coords, watermark.scale)
        currentWatermark = watermark
        AstraLog.d(tag, "watermark applied scale=${watermark.scale}")
        return true
    }

    private fun prepareBitmap(watermark: Watermark): Bitmap {
        val bitmap = when {
            watermark.markImg != null -> watermark.markImg!!
            watermark.txt != null -> {
                val text = watermark.txt!!
                val size = if (watermark.textSize > 0) watermark.textSize.toFloat() else 32f
                val color = if (watermark.textColor != -1) watermark.textColor else Color.WHITE
                BitmapUtils.createBitmapByCanvas(text, size, color)
            }
            cachedBitmap != null -> cachedBitmap!!
            else -> BitmapUtils.createBitmapByCanvas(" ", 20f, Color.WHITE, Color.TRANSPARENT)
        }
        cachedBitmap = bitmap
        return bitmap
    }

    private fun ensureHandle(): Long {
        if (nativeHandle == 0L) {
            nativeHandle = nativeCreate()
        }
        return nativeHandle
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeOnSurfaceCreate(handle: Long, width: Int, height: Int): IntArray?
    private external fun nativeOnSurfaceChanged(handle: Long, width: Int, height: Int)
    private external fun nativeOnDraw(handle: Long)
    private external fun nativeUpdateMatrix(handle: Long, matrix: FloatArray)
    private external fun nativeApplyWatermark(
        handle: Long,
        bitmap: Bitmap,
        coords: FloatArray?,
        scale: Float
    )

    interface OnSwitchCameraListener {
        fun onChange(): Boolean
    }
}
