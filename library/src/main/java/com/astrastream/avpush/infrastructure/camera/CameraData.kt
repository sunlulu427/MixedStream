package com.astrastream.avpush.infrastructure.camera

internal data class CameraData(
    val cameraId: Int,
    val facing: Int,
    var width: Int = 0,
    var height: Int = 0,
    var hasFlash: Boolean = false,
    var orientation: Int = 0,
    var supportTouchFocus: Boolean = false,
    var touchFocusMode: Boolean = false
) {
    companion object {
        const val FACING_FRONT = 1
        const val FACING_BACK = 2
    }
}
