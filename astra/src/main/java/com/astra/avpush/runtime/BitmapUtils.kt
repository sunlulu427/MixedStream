package com.astrastream.avpush.runtime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt


object BitmapUtils {

    fun createTextBitmap(
        text: CharSequence,
        context: Context,
        textSizeSp: Float,
        textColor: Int,
        backgroundColor: Int = Color.TRANSPARENT,
        typeface: Typeface? = null
    ): Bitmap {
        val textView = TextView(context).apply {
            this.text = text
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            gravity = android.view.Gravity.CENTER
            this.typeface = typeface ?: Typeface.create("sans-serif", Typeface.BOLD)
        }
        measureView(textView)
        val width = textView.measuredWidth.coerceAtLeast(1)
        val height = textView.measuredHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)
        textView.draw(canvas)
        return bitmap
    }

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
        val bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg)
        canvas.drawText(text, 0f, baseline, paint)
        return bitmap
    }

    fun changeBitmapSize(context: Context, src: Int, width: Float, height: Float): Bitmap {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val original = BitmapFactory.decodeResource(context.applicationContext.resources, src, options)
        return scaleBitmap(original, width, height)
    }

    private fun scaleBitmap(bitmap: Bitmap, width: Float, height: Float): Bitmap {
        val targetWidth = width.roundToInt().coerceAtLeast(1)
        val targetHeight = height.roundToInt().coerceAtLeast(1)
        if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun measureView(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
