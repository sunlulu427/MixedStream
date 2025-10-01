package com.astrastream.avpush.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView


object BitmapUtils {
    /**
     * 将文字 生成 文字图片 生成显示编码的Bitmap,目前这个方法是可用的
     * 
     * @param contents
     * @param context
     * @return
     */
    fun creatBitmap(
        contents: String,
        context: Context,
        testSize: Int,
        testColor: Int,
        bg: Int
    ): Bitmap {
        val scale = context.resources.displayMetrics.scaledDensity
        val tv = TextView(context)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tv.layoutParams = layoutParams
        tv.text = contents
        tv.textSize = scale * testSize
        tv.gravity = Gravity.CENTER_HORIZONTAL
        tv.isDrawingCacheEnabled = true
        tv.setTextColor(testColor)
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        tv.layout(0, 0, tv.measuredWidth, tv.measuredHeight)
        tv.setBackgroundColor(bg)
        tv.buildDrawingCache()
        return tv.drawingCache
    }

    fun createBitmapByCanvas(
        text: String,
        textSize: Float,
        textColor: Int,
        bg: Int = Color.TRANSPARENT,
        typeface: Typeface? = null
    ): Bitmap {
        // 设置画笔属性
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.textSize = textSize
            it.color = textColor
            it.textAlign = Paint.Align.LEFT
            it.typeface = typeface ?: Typeface.create("sans-serif", Typeface.BOLD)
        }
        // 测量文字的宽度和高度
        val baseLine = -paint.ascent()
        val width = (paint.measureText(text) + 0.5f).toInt()
        val height = (baseLine + paint.descent() + 0.5f).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        //将 bitmap和canvas关联
        val canvas = Canvas(bitmap)
        canvas.drawText(text, 0f, baseLine, paint)
        canvas.drawColor(bg)
        return bitmap
    }

    fun changeBitmapSize(context: Context, src: Int, width: Float, height: Float): Bitmap {
        val bitmap = BitmapFactory.decodeResource(context.applicationContext.resources, src)
        return getBitmap(bitmap, width, height)
    }

    private fun getBitmap(bitmap: Bitmap, width: Float, height: Float): Bitmap {
        var bitmap1 = bitmap
        val oldWidth = bitmap1.width
        val oldHeight = bitmap1.height
        //设置想要的大小

        //计算压缩的比率
        val scaleWidth = (width) / oldWidth
        val scaleHeight = (height) / oldHeight

        //获取想要缩放的matrix
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        //获取新的bitmap
        bitmap1 = Bitmap.createBitmap(bitmap, 0, 0, oldWidth, oldHeight, matrix, true)
        return bitmap1
    }
}
