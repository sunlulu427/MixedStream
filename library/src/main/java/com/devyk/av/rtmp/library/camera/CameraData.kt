package com.devyk.av.rtmp.library.camera

class CameraData {

    var cameraID: Int = 0            //camera的id
    var cameraFacing: Int = 0        //区分前后摄像头
    var cameraWidth: Int = 0         //camera的宽度
    var cameraHeight: Int = 0        //camera的高度
    var hasLight: Boolean = false
    var orientation: Int = 0
    var supportTouchFocus: Boolean = false
    var touchFocusMode: Boolean = false

    constructor(id: Int, facing: Int, width: Int, height: Int) {
        cameraID = id
        cameraFacing = facing
        cameraWidth = width
        cameraHeight = height
    }

    constructor(id: Int, facing: Int) {
        cameraID = id
        cameraFacing = facing
    }

    companion object {
        val FACING_FRONT = 1
        val FACING_BACK = 2
    }
}