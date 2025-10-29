package com.astra.avpush.domain.callback

interface ICameraOpenListener {
    fun onCameraOpen()

    fun onCameraError(message: String) {}

    fun onCameraPreviewSizeSelected(width: Int, height: Int) {}
}
