package com.astrastream.avpush.runtime

internal object NativeRenderUtil {

    init {
        System.loadLibrary("astra")
    }

    fun computeWatermarkQuad(
        surfaceWidth: Int,
        surfaceHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
        scale: Float,
        minHeight: Float,
        maxHeight: Float,
        maxWidth: Float,
        horizontalMargin: Float,
        verticalMargin: Float
    ): FloatArray? = nativeComputeWatermarkQuad(
        surfaceWidth,
        surfaceHeight,
        bitmapWidth,
        bitmapHeight,
        scale,
        minHeight,
        maxHeight,
        maxWidth,
        horizontalMargin,
        verticalMargin
    )

    private external fun nativeComputeWatermarkQuad(
        surfaceWidth: Int,
        surfaceHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
        scale: Float,
        minHeight: Float,
        maxHeight: Float,
        maxWidth: Float,
        horizontalMargin: Float,
        verticalMargin: Float
    ): FloatArray?
}
