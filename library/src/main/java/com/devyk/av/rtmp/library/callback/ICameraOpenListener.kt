package com.devyk.av.rtmp.library.callback

interface ICameraOpenListener {
    fun onCameraOpen()

    fun onCameraError(message: String) {}

    fun onCameraPreviewSizeSelected(width: Int, height: Int) {}
}
