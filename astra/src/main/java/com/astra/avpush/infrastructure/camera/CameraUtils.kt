package com.astra.avpush.infrastructure.camera

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.Log
import com.astra.avpush.domain.config.CameraConfiguration
import com.astra.avpush.runtime.AstraLog

object CameraUtils {

    private var TAG = this.javaClass.simpleName

    private var sPreviewCallback: Camera.PreviewCallback? = null
    private var sRotation = 90
    private var sCameraId: Int = 0
    private var sWidth: Int = 0
    private var sHeight: Int = 0
    private var sCamera: Camera? = null

    /**
     * 停止
     */
    fun stop() {
        if (sPreviewCallback != null)
            sPreviewCallback = null
        if (sCamera != null) {
            sCamera!!.release()
            sCamera = null
        }
    }

    /**
     * 获取所有的 camera 数据
     */
   fun getAllCamerasData(isBackFirst: Boolean): MutableList<CameraData>? {
        val cameraDatas = ArrayList<CameraData>()
        val cameraInfo = Camera.CameraInfo()
        val numberOfCameras = Camera.getNumberOfCameras()
        for (i in 0 until numberOfCameras) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                val cameraData = CameraData(i, CameraData.FACING_FRONT)
                if (isBackFirst) {
                    cameraDatas.add(cameraData)
                } else {
                    cameraDatas.add(0, cameraData)
                }
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                val cameraData = CameraData(i, CameraData.FACING_BACK)
                if (isBackFirst) {
                    cameraDatas.add(0, cameraData)
                } else {
                    cameraDatas.add(cameraData)
                }
            }
        }
        return cameraDatas
    }

    /**
     * 初始化  camera 参数
     */
    fun initCameraParams(
        camera: Camera?,
        cameraData: CameraData,
        isTouchMode: Boolean,
        configuration: CameraConfiguration
    ) {
        val isLandscape = configuration.orientation !== CameraConfiguration.Orientation.PORTRAIT
        val width = Math.max(configuration.height, configuration.width)
        val height = Math.min(configuration.height, configuration.width)
        sWidth = width
        sHeight = height
        sRotation = configuration.rotation
        sCameraId = configuration.facing.ordinal
        sCamera = camera
        camera?.run {
            val parameters = camera.parameters
            logSupportedPreviewSizes(parameters)
            setPreviewFormat(camera, parameters)
            setPreviewFps(camera, configuration.fps, parameters)
            setPreviewSize(camera, cameraData, width, height, parameters)

            setPreviewCallback()
            cameraData.hasFlash = supportFlash(camera)
            setOrientation(cameraData, isLandscape, camera)
            setFocusMode(camera, cameraData, isTouchMode)
        }
    }

    /**
     * 设置预览回调
     */
    internal var myCallback: Camera.PreviewCallback = Camera.PreviewCallback { data, camera ->

        if (sPreviewCallback != null)
            // data数据依然是倒的
            sPreviewCallback!!.onPreviewFrame(data, camera)
        camera.addCallbackBuffer(data)
    }

    /**
     * 设置预览格式
     */
    @Throws(CameraNotSupportException::class)
    fun setPreviewFormat(camera: Camera, parameters: Camera.Parameters) {
        //设置预览回调的图片格式
        try {
            val supportedPreviewFormats = parameters.getSupportedPreviewFormats()
            if (supportedPreviewFormats.contains(ImageFormat.NV21))
                parameters.previewFormat = ImageFormat.NV21
            else
                throw CameraNotSupportException()
            camera.parameters = parameters
        } catch (e: Exception) {
            throw CameraNotSupportException()
        }
    }

    /**
     * 设置帧率
     */
    fun setPreviewFps(camera: Camera, fps: Int, parameters: Camera.Parameters) {
        try {
            parameters.previewFrameRate = fps
            camera.parameters = parameters
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val range = adaptPreviewFps(fps, parameters.supportedPreviewFpsRange)

        try {
            parameters.setPreviewFpsRange(range[0], range[1])
            camera.parameters = parameters
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun adaptPreviewFps(expectedFps: Int, fpsRanges: List<IntArray>): IntArray {
        var expectedFps = expectedFps
        expectedFps *= 1000
        var closestRange = fpsRanges[0]
        var measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps)
        for (range in fpsRanges) {
            if (range[0] <= expectedFps && range[1] >= expectedFps) {
                val curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps)
                if (curMeasure < measure) {
                    closestRange = range
                    measure = curMeasure
                }
            }
        }
        return closestRange
    }

    /**
     * 设置预览大小
     */
    @Throws(CameraNotSupportException::class)
    fun setPreviewSize(
        camera: Camera, cameraData: CameraData, width: Int, height: Int,
        parameters: Camera.Parameters
    ) {
        val size = getOptimalPreviewSize(camera, width, height)
        if (size == null) {
            throw CameraNotSupportException()
        } else {
            cameraData.width = size.width
            cameraData.height = size.height
        }
        //设置预览大小
        Log.d(TAG, "Camera Width: " + size.width + "    Height: " + size.height)
        try {
            parameters.setPreviewSize(cameraData.width, cameraData.height)
            camera.parameters = parameters
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setOrientation(cameraData: CameraData, isLandscape: Boolean, camera: Camera) {


        var orientation = getDisplayOrientation(cameraData.cameraId)
        if (isLandscape) {
            orientation -= 90
        }
        camera.setDisplayOrientation(orientation)
    }

    /**
     * 设置聚焦模式
     */
    private fun setFocusMode(camera: Camera, cameraData: CameraData, isTouchMode: Boolean) {
        cameraData.supportTouchFocus = supportTouchFocus(camera)
        if (!cameraData.supportTouchFocus) {
            setAutoFocusMode(camera)
        } else {
            if (!isTouchMode) {
                cameraData.touchFocusMode = false
                setAutoFocusMode(camera)
            } else {
                cameraData.touchFocusMode = true
            }
        }
    }

    /**
     * 是否支持聚焦
     */
    fun supportTouchFocus(camera: Camera?): Boolean {
        return if (camera != null) {
            camera.parameters.maxNumFocusAreas != 0
        } else false
    }

    /**
     * 设置自动聚焦
     */
    fun setAutoFocusMode(camera: Camera?) {
        try {
            camera?.run {
                val parameters = camera.parameters
                val focusModes = parameters.supportedFocusModes
                if (focusModes.size > 0 && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    camera.parameters = parameters
                } else if (focusModes.size > 0) {
                    parameters.focusMode = focusModes[0]
                    camera.parameters = parameters
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 设置触摸聚集
     */
    fun setTouchFocusMode(camera: Camera?) {
        try {
            camera?.run {
                val parameters = parameters;
                val focusModes = parameters.supportedFocusModes
                if (focusModes.size > 0 && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                    camera.parameters = parameters
                } else if (focusModes.size > 0) {
                    parameters.focusMode = focusModes[0]
                    camera.parameters = parameters
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * 找到最为合适的预览大小
     */
    fun getOptimalPreviewSize(camera: Camera, width: Int, height: Int): Camera.Size? {
        val sizes = camera.parameters.supportedPreviewSizes ?: return null
        val targetRatio = if (height != 0) width.toFloat() / height else 0f
        val aspectTolerance = 0.02f
        var optimalSize: Camera.Size? = null
        var minDiff = Double.MAX_VALUE

        for (size in sizes) {
            val ratio = size.width.toFloat() / size.height
            if (targetRatio > 0f && Math.abs(ratio - targetRatio) > aspectTolerance) {
                continue
            }
            val diff = Math.abs(size.height - height).toDouble()
            if (diff < minDiff) {
                optimalSize = size
                minDiff = diff
            }
        }

        if (optimalSize == null) {
            // 无法满足容差要求时，选择高度最接近的
            minDiff = Double.MAX_VALUE
            for (size in sizes) {
                val diff = Math.abs(size.height - height).toDouble()
                if (diff < minDiff) {
                    optimalSize = size
                    minDiff = diff
                }
            }
        }

        optimalSize?.let {
            AstraLog.i(
                TAG,
                "selected preview size ${it.width}x${it.height} for request ${width}x$height (ratio=${"%.3f".format(it.width.toFloat() / it.height)})"
            )
        }

        return optimalSize
    }

    private fun logSupportedPreviewSizes(parameters: Camera.Parameters) {
        try {
            val sizes = parameters.supportedPreviewSizes ?: return
            val ordered = sizes.sortedByDescending { it.width * it.height }
            val summary = ordered.joinToString(separator = ", ") { "${it.width}x${it.height}" }
            AstraLog.i(TAG, "supported preview sizes: $summary")
        } catch (t: Throwable) {
            AstraLog.w(TAG, "list preview sizes failed: ${t.message}")
        }
    }

    /**
     * 根据摄像头方向来获取显示的方向
     */
    fun getDisplayOrientation(cameraId: Int): Int {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = info.orientation % 360
            result = (360 - result) % 360  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation + 360) % 360
        }
        return result
    }

    /**
     * 检查相机服务
     */
    @Throws(CameraDisabledException::class, NoCameraException::class)
    fun checkCameraService(context: Context) {
        // Check if device policy has disabled the camera.
        val dpm = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        if (dpm.getCameraDisabled(null)) {
            throw CameraDisabledException()
        }
        val cameraDatas = getAllCamerasData(false)
        if (cameraDatas?.size == 0) {
            throw NoCameraException()
        }
    }

    fun supportFlash(camera: Camera): Boolean {
        val params = camera.parameters
        val flashModes = params.supportedFlashModes ?: return false
        for (flashMode in flashModes) {
            if (Camera.Parameters.FLASH_MODE_TORCH == flashMode) {
                return true
            }
        }
        return false
    }

    /**
     * 将 YUV 数据旋转
     *
     * @param data
     */
    private fun rotation90(data: ByteArray, cameraId: Int, width: Int, height: Int): ByteArray {
        val yuv = ByteArray(width * height * 3 / 2)
        var index = 0
        val ySize = width * height
        //u和v
        val uvHeight = height / 2
        //后置摄像头顺时针旋转90度
        if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            //将y的数据旋转之后 放入新的byte数组
            for (i in 0 until height) {
                for (j in height - 1 downTo 0) {
                    yuv[index++] = data[width * j + i]
                }
            }

            //每次处理两个数据
            var i = 0
            while (i < width) {
                for (j in uvHeight - 1 downTo 0) {
                    // v
                    yuv[index++] = data[ySize + width * j + i]
                    // u
                    yuv[index++] = data[ySize + width * j + i + 1]
                }
                i += 2
            }
        } else {
            //逆时针旋转90度
            for (i in 0 until width) {
                var nPos = width - 1
                for (j in 0 until width) {
                    yuv[index++] = data[nPos - i]
                    nPos += width
                }
            }
            //u v
            var i = 0
            while (i < width) {
                var nPos = ySize + width - 1
                for (j in 0 until uvHeight) {
                    yuv[index++] = data[nPos - i - 1]
                    yuv[index++] = data[nPos - i]
                    nPos += width
                }
                i += 2
            }
        }
        return yuv
    }


    /**
     * 设置预览回调，用于软编
     */
    fun setPreviewCallback() {
        val buffer = ByteArray(sHeight * sWidth * 3 / 2)
        //数据缓存区
        sCamera!!.addCallbackBuffer(buffer)
        sCamera!!.setPreviewCallbackWithBuffer(myCallback)
    }

    /**
     * 设置预览回调，用于软编
     *
     * @param previewCallback
     */
    fun setPreviewCallback(previewCallback: Camera.PreviewCallback) {
        sPreviewCallback = previewCallback
        val buffer = ByteArray(sHeight * sWidth * 3 / 2)
        //数据缓存区
        sCamera!!.addCallbackBuffer(buffer)
        sCamera!!.setPreviewCallbackWithBuffer(myCallback)
    }
}
