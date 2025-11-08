package com.astra.avpush.runtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt


object BitmapUtils {

    fun createBitmapByCanvas(
        text: String,
        textSizePx: Float,
        textColor: Int,
        bg: Int = Color.TRANSPARENT,
        typeface: Typeface? = null
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = textSizePx
            textAlign = Paint.Align.LEFT
            this.typeface = typeface ?: Typeface.create("sans-serif", Typeface.BOLD)
        }
        val baseline = -paint.ascent()
        val measuredWidth = paint.measureText(text).roundToInt().coerceAtLeast(1)
        val measuredHeight = (baseline + paint.descent()).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(measuredWidth, measuredHeight)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg)
        canvas.drawText(text, 0f, baseline, paint)
        return bitmap
    }
}
