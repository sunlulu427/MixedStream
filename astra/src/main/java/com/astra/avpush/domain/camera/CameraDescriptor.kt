package com.astra.avpush.domain.camera

data class CameraDescriptor(
    val id: Int,
    val facing: Facing,
    val previewWidth: Int,
    val previewHeight: Int,
    val orientation: Int,
    val hasFlash: Boolean,
    val supportsTouchFocus: Boolean,
    val touchFocusEnabled: Boolean
) {
    enum class Facing {
        FRONT,
        BACK
    }
}
