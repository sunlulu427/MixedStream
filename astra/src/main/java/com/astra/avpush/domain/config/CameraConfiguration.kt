package com.astra.avpush.domain.config

class CameraConfiguration(
    val width: Int = 720,
    val height: Int = 1280,
    val fps: Int = 30,
    val rotation: Int = 0,
    val facing: Facing = Facing.BACK,
    val orientation: Orientation = Orientation.PORTRAIT,
    val focusMode: FocusMode = FocusMode.AUTO
) {
    enum class Facing {
        FRONT, BACK;

        fun switch(): Facing = if (this == FRONT) BACK else FRONT
    }

    enum class Orientation { LANDSCAPE, PORTRAIT }
    enum class FocusMode { AUTO, TOUCH }
}
