package com.astra.avpush.infrastructure.camera.renderer

import android.graphics.Bitmap
import android.graphics.Color
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.presentation.widget.GlRenderer
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.runtime.BitmapUtils

class EncodeRenderer(private val textureId: Int) : GlRenderer {

    companion object {
        init {
            System.loadLibrary("astra")
        }
    }

    private var nativeHandle: Long = 0L
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    @Volatile
    private var pendingWatermark: Watermark? = null
    @Volatile
    private var currentWatermark: Watermark? = null
    private var cachedBitmap: Bitmap? =
        BitmapUtils.createBitmapByCanvas("  ", 20f, Color.WHITE, Color.TRANSPARENT)

    override fun onSurfaceCreate(width: Int, height: Int) {
        val handle = ensureHandle()
        nativeOnSurfaceCreate(handle, width, height)
        surfaceWidth = width
        surfaceHeight = height
        attemptApplyWatermark()
        AstraLog.d(javaClass.simpleName) { "surface created width=$width height=$height" }
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
        AstraLog.d(javaClass.simpleName) { "surface changed width=$width height=$height" }
    }

    override fun onDraw() {
        nativeOnDraw(ensureHandle())
    }

    fun setWatemark(watermark: Watermark) {
        pendingWatermark = watermark
        attemptApplyWatermark()
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
            AstraLog.d(javaClass.simpleName) { "renderer released" }
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
        AstraLog.d(javaClass.simpleName) { "watermark applied scale=${watermark.scale}" }
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
            nativeHandle = nativeCreate(textureId)
        }
        return nativeHandle
    }

    private external fun nativeCreate(textureId: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeOnSurfaceCreate(handle: Long, width: Int, height: Int)
    private external fun nativeOnSurfaceChanged(handle: Long, width: Int, height: Int)
    private external fun nativeOnDraw(handle: Long)
    private external fun nativeApplyWatermark(
        handle: Long,
        bitmap: Bitmap,
        coords: FloatArray?,
        scale: Float
    )
}
