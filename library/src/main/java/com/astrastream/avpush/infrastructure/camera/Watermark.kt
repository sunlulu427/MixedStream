package com.astrastream.avpush.infrastructure.camera

import android.graphics.Bitmap

class Watermark {
    var markImg: Bitmap? = null
    var txt: String? = null
    var textColor = -1
    var textSize = -1
    var floatArray: FloatArray? = null

    constructor(
        markImg: Bitmap,
        floatArray: FloatArray?
    ) {
        this.markImg = markImg
        this.floatArray = floatArray
    }

    constructor(
        txt: String,
        txtColor: Int,
        txtSize: Int,
        floatArray: FloatArray?
    ) {
        this.txt = txt
        this.textSize = txtSize
        this.textColor = txtColor
        this.floatArray = floatArray
    }
}
