package com.astra.avpush.infrastructure.camera

import android.graphics.Bitmap

class Watermark {
    var markImg: Bitmap? = null
    var txt: String? = null
    var textColor = -1
    var textSize = -1
    var floatArray: FloatArray? = null
    var scale: Float = 1.0f

    constructor(
        markImg: Bitmap,
        floatArray: FloatArray? = null,
        scale: Float = 1.0f
    ) {
        this.markImg = markImg
        this.floatArray = floatArray
        this.scale = scale
    }

    constructor(
        txt: String,
        txtColor: Int,
        txtSize: Int,
        floatArray: FloatArray? = null,
        scale: Float = 1.0f
    ) {
        this.txt = txt
        this.textSize = txtSize
        this.textColor = txtColor
        this.floatArray = floatArray
        this.scale = scale
    }
}
