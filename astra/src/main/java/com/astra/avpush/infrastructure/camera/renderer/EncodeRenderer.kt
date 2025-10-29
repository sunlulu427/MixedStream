package com.astra.avpush.infrastructure.camera.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.astra.avpush.domain.callback.IRenderer
import com.astra.avpush.infrastructure.camera.ShaderHelper
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.runtime.BitmapUtils
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.runtime.NativeRenderUtil

class EncodeRenderer(private val context: Context, private val textureId: Int) : IRenderer {

    companion object {
        private val DEFAULT_WATERMARK_COORDS = floatArrayOf(
            0.55f, -0.9f,
            0.9f, -0.9f,
            0.55f, -0.7f,
            0.9f, -0.7f
        )
        private const val MAX_HEIGHT_NDC = 0.3f
        private const val MIN_HEIGHT_NDC = 0.1f
        private const val MAX_WIDTH_NDC = 0.6f
        private const val HORIZONTAL_MARGIN = 0.05f
        private const val VERTICAL_MARGIN = 0.06f

        init {
            System.loadLibrary("astra")
        }
    }

    private val vertexData: FloatArray = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f,

        0.55f, -0.9f,
        0.9f, -0.9f,
        0.55f, -0.7f,
        0.9f, -0.7f
    )

    private val fragmentData: FloatArray = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private var watermarkCoords: FloatArray = DEFAULT_WATERMARK_COORDS.copyOf()
    private var nativeHandle: Long = 0L

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var pendingWatermark: Watermark? = null
    private var currentWatermark: Watermark? = null
    private var cachedBitmap: Bitmap? = null

    init {
        cachedBitmap = BitmapUtils.createBitmapByCanvas("  ", 20f, Color.WHITE, Color.TRANSPARENT)
    }

    override fun onSurfaceCreate(width: Int, height: Int) {
        val vertexSource = ShaderHelper.getScript(ShaderHelper.Script.BASIC_VERTEX)
        val fragmentSource = ShaderHelper.getScript(ShaderHelper.Script.BASIC_FRAGMENT)
        val handle = ensureHandle()
        nativeOnSurfaceCreate(handle, vertexSource, fragmentSource)
        nativeOnSurfaceChanged(handle, width, height)
        cachedBitmap?.let { nativeUpdateWatermarkTexture(handle, it) }
        nativeUpdateWatermarkCoords(handle, watermarkCoords)
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
        val handle = ensureHandle()
        nativeOnDraw(handle)
    }

    private fun attemptApplyWatermark() {
        val watermark = pendingWatermark ?: return
        if (watermark.floatArray == null && (surfaceWidth == 0 || surfaceHeight == 0)) {
            return
        }
        if (applyWatermark(watermark)) {
            pendingWatermark = null
        }
    }

    private fun applyWatermark(watermark: Watermark): Boolean {
        val bitmap = prepareBitmap(watermark)
        val coords = watermark.floatArray?.takeIf { it.size >= 8 }?.copyOf(8)
            ?: computeDefaultQuad(bitmap, watermark)
            ?: return false

        watermarkCoords = coords
        updateVertexArray(coords)
        val handle = ensureHandle()
        nativeUpdateWatermarkCoords(handle, watermarkCoords)
        nativeUpdateWatermarkTexture(handle, bitmap)
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

    private fun computeDefaultQuad(bitmap: Bitmap, watermark: Watermark): FloatArray? {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return null
        return NativeRenderUtil.computeWatermarkQuad(
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            scale = watermark.scale,
            minHeight = MIN_HEIGHT_NDC,
            maxHeight = MAX_HEIGHT_NDC,
            maxWidth = MAX_WIDTH_NDC,
            horizontalMargin = HORIZONTAL_MARGIN,
            verticalMargin = VERTICAL_MARGIN
        )
    }

    private fun updateVertexArray(coords: FloatArray) {
        if (coords.size < 8) return
        vertexData[8] = coords[0]
        vertexData[9] = coords[1]
        vertexData[10] = coords[2]
        vertexData[11] = coords[3]
        vertexData[12] = coords[4]
        vertexData[13] = coords[5]
        vertexData[14] = coords[6]
        vertexData[15] = coords[7]
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

    private fun ensureHandle(): Long {
        if (nativeHandle == 0L) {
            nativeHandle = nativeCreate(textureId, vertexData, fragmentData)
        }
        return nativeHandle
    }

    private external fun nativeCreate(textureId: Int, vertexData: FloatArray, fragmentData: FloatArray): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeOnSurfaceCreate(handle: Long, vertexSource: String, fragmentSource: String)
    private external fun nativeOnSurfaceChanged(handle: Long, width: Int, height: Int)
    private external fun nativeOnDraw(handle: Long)
    private external fun nativeUpdateWatermarkCoords(handle: Long, coords: FloatArray)
    private external fun nativeUpdateWatermarkTexture(handle: Long, bitmap: Bitmap)
}
