package com.devyk.av.rtmp.library.config

/**
 * <pre>
 *     author  : devyk on 2020-05-28 23:20
 *     blog    : https://juejin.im/user/578259398ac2470061f3a3fb/posts
 *     github  : https://github.com/yangkun19921001
 *     mailbox : yang1001yk@gmail.com
 *     desc    : This is CameraConfiguration
 * </pre>
 */
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