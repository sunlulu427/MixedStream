package com.devyk.av.rtmp.library.camera

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.SystemClock
import android.util.Log
import com.devyk.av.rtmp.library.config.CameraConfiguration
import com.devyk.av.rtmp.library.utils.LogHelper
import java.io.IOException

class CameraHolder {
    private val TAG = "CameraHolder"
    private val FOCUS_WIDTH = 80
    private val FOCUS_HEIGHT = 80

    private var mCameraDatas: MutableList<CameraData>? = null
    private var mCameraDevice: Camera? = null


    var cameraData: CameraData? = null
        private set
    var state: State? = null
        private set
    private var mTexture: SurfaceTexture? = null
    private var frameCount: Int = 0
    private var lastFrameLogTimestamp: Long = 0
    private var isTouchMode = false
    private var isOpenBackFirst = true
    private var mConfiguration = CameraConfiguration()

    val numberOfCameras: Int
        get() = Camera.getNumberOfCameras()

    val isLandscape: Boolean
        get() = mConfiguration.orientation !== CameraConfiguration.Orientation.PORTRAIT

    enum class State {
        INIT,
        OPENED,
        PREVIEW
    }

    init {
        state = State.INIT
    }

    /**
     * 打开相机
     */
    @Synchronized
    @Throws(CameraHardwareException::class, CameraNotSupportException::class)
    fun openCamera(): Camera {
        if (mCameraDatas == null || mCameraDatas!!.size == 0) {
            mCameraDatas = CameraUtils.getAllCamerasData(isOpenBackFirst)
        }
        val candidates = mCameraDatas ?: mutableListOf()
        if (candidates.isEmpty()) {
            Log.e(TAG, "no cameras available on device")
            throw CameraNotSupportException()
        }

        // 若已打开且相同摄像头，直接复用
        if (mCameraDevice != null && this.cameraData === candidates[0]) {
            return mCameraDevice!!
        }
        if (mCameraDevice != null) {
            releaseCamera()
        }

        var lastError: RuntimeException? = null
        for (candidate in candidates) {
            try {
                Log.d(TAG, "open camera ${candidate.cameraID}")
                mCameraDevice = Camera.open(candidate.cameraID)
                if (mCameraDevice != null) {
                    // 初始化参数
                    try {
                        CameraUtils.initCameraParams(mCameraDevice, candidate, isTouchMode, mConfiguration)
                    } catch (e: Exception) {
                        // 初始化失败，释放并尝试下一个候选
                        Log.e(TAG, "init camera params failed for id=${candidate.cameraID}: ${e.message}")
                        try { mCameraDevice?.release() } catch (_: Throwable) {}
                        mCameraDevice = null
                        continue
                    }
                    this.cameraData = candidate
                    state = State.OPENED
                    return mCameraDevice!!
                }
            } catch (e: RuntimeException) {
                lastError = e
                Log.e(TAG, "fail to connect Camera id=${candidate.cameraID}: ${e.message}")
                // 继续尝试其他摄像头
            }
        }

        // 所有候选都失败
        if (lastError != null) {
            throw CameraHardwareException(lastError as Throwable)
        } else {
            throw CameraNotSupportException()
        }
    }


    /**
     *设置纹理
     */
    fun setSurfaceTexture(textureId: Int, listener: SurfaceTexture.OnFrameAvailableListener?) {
        mTexture = SurfaceTexture(textureId)
        Log.d(TAG, "setSurfaceTexture(id) called, textureId=$textureId")
        try {
            mCameraDevice?.run {
                setPreviewTexture(mTexture)
                mTexture?.setOnFrameAvailableListener(listener)
            }
            LogHelper.d(
                TAG,
                "setSurfaceTexture(id) success -> camera=${cameraData?.cameraID} state=$state"
            )
        } catch (e: IOException) {
            LogHelper.e(TAG, "setSurfaceTexture(id) failed: ${e.message}")
            releaseCamera()
        }
    }

    fun setSurfaceTexture(
        texture: SurfaceTexture,
        listener: SurfaceTexture.OnFrameAvailableListener?
    ) {
        mTexture = texture
        Log.d(TAG, "setSurfaceTexture(surfaceTexture) called")
        try {
            mCameraDevice?.run {
                cameraData?.let {
                    try {
                        texture.setDefaultBufferSize(it.cameraWidth, it.cameraHeight)
                        LogHelper.d(
                            TAG,
                            "setDefaultBufferSize ${it.cameraWidth}x${it.cameraHeight} for camera=${it.cameraID}"
                        )
                    } catch (setSizeError: Throwable) {
                        LogHelper.w(
                            TAG,
                            "setDefaultBufferSize failed: ${setSizeError.message}"
                        )
                    }
                }
                setPreviewTexture(mTexture)
                mTexture?.setOnFrameAvailableListener(listener)
            }
            LogHelper.d(
                TAG,
                "setSurfaceTexture(surface) success -> camera=${cameraData?.cameraID} state=$state"
            )
        } catch (e: IOException) {
            LogHelper.e(TAG, "setSurfaceTexture(surface) failed: ${e.message}")
            releaseCamera()
        }
    }

    /**
     * 设置预览相机属性
     */
    fun setConfiguration(configuration: CameraConfiguration) {
        isTouchMode = configuration.focusMode !== CameraConfiguration.FocusMode.AUTO
        isOpenBackFirst = configuration.facing !== CameraConfiguration.Facing.FRONT
        mConfiguration = configuration
    }

    /**
     * 开始预览
     */
    @Synchronized
    fun startPreview() {
        if (state != State.OPENED) {
            Log.d(TAG, "startPreview ignored: state=$state")
            return
        }
        if (mCameraDevice == null) {
            Log.d(TAG, "startPreview ignored: mCameraDevice=null")
            return
        }
        if (mTexture == null) {
            Log.d(TAG, "startPreview ignored: mTexture=null (wait surface)")
            return
        }
        try {
            mCameraDevice!!.setPreviewTexture(mTexture)
            mCameraDevice!!.startPreview()
            state = State.PREVIEW
            frameCount = 0
            lastFrameLogTimestamp = 0
            Log.d(TAG, "startPreview success")
            LogHelper.i(
                TAG,
                "startPreview success -> actual ${cameraData?.cameraWidth}x${cameraData?.cameraHeight} state=$state"
            )
        } catch (e: Exception) {
            releaseCamera()
            e.printStackTrace()
        }

    }

    /**
     * 停止预览
     */
    @Synchronized
    fun stopPreview() {
        if (state != State.PREVIEW) {
            return
        }
        if (mCameraDevice == null) {
            return
        }
        mCameraDevice!!.setPreviewCallback(null)
        val cameraParameters = mCameraDevice!!.parameters
        if (cameraParameters != null && cameraParameters.flashMode != null
            && cameraParameters.flashMode != Camera.Parameters.FLASH_MODE_OFF
        ) {
            cameraParameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
        }
        mCameraDevice!!.parameters = cameraParameters
        mCameraDevice!!.stopPreview()
        state = State.OPENED
    }

    /**
     * 释放相机
     */
    @Synchronized
    fun releaseCamera() {
        if (state == State.PREVIEW) {
            stopPreview()
        }
        if (state != State.OPENED) {
            return
        }
        if (mCameraDevice == null) {
            return
        }
        mCameraDevice?.release()
        mCameraDevice = null
        cameraData = null
        state = State.INIT
        CameraUtils.stop()
    }

    fun release() {
        mTexture?.release()
        mTexture = null
        mCameraDatas = null
        isTouchMode = false
        isOpenBackFirst = false
        mConfiguration = CameraConfiguration()
    }


    fun setFocusPoint(x: Int, y: Int) {
        if (state != State.PREVIEW || mCameraDevice == null) {
            return
        }
        if (x < -1000 || x > 1000 || y < -1000 || y > 1000) {
            Log.w(TAG, "setFocusPoint: values are not ideal x= $x y= $y")
            return
        }

        val params = mCameraDevice!!.parameters

        if (params != null && params.maxNumFocusAreas > 0) {
            val focusArea = ArrayList<Camera.Area>()
            focusArea.add(Camera.Area(Rect(x, y, x + FOCUS_WIDTH, y + FOCUS_HEIGHT), 1000))

            params.focusAreas = focusArea

            try {
                mCameraDevice!!.parameters = params
            } catch (e: Exception) {
                // Ignore, we might be setting it too
                // fast since previous attempt
            }
        } else {
            Log.w(TAG, "Not support Touch focus mode")
        }
    }

    fun doAutofocus(focusCallback: Camera.AutoFocusCallback): Boolean {
        if (state != State.PREVIEW || mCameraDevice == null) {
            return false
        }
        // Make sure our auto settings aren't locked
        val params = mCameraDevice!!.parameters
        if (params.isAutoExposureLockSupported) {
            params.autoExposureLock = false
        }

        if (params.isAutoWhiteBalanceLockSupported) {
            params.autoWhiteBalanceLock = false
        }

        mCameraDevice!!.parameters = params
        mCameraDevice!!.cancelAutoFocus()
        mCameraDevice!!.autoFocus(focusCallback)
        return true
    }

    fun changeFocusMode(touchMode: Boolean) {
        if (state != State.PREVIEW || mCameraDevice == null || cameraData == null) {
            return
        }
        isTouchMode = touchMode
        cameraData!!.touchFocusMode = touchMode
        if (touchMode) {
            CameraUtils.setTouchFocusMode(mCameraDevice)
        } else {
            CameraUtils.setAutoFocusMode(mCameraDevice)
        }
    }

    fun switchFocusMode() {
        changeFocusMode(!isTouchMode)
    }

    fun cameraZoom(isBig: Boolean): Float {
        if (state != State.PREVIEW || mCameraDevice == null || cameraData == null) {
            return -1f
        }
        val params = mCameraDevice!!.parameters
        if (isBig) {
            params.zoom = Math.min(params.zoom + 1, params.maxZoom)
        } else {
            params.zoom = Math.max(params.zoom - 1, 0)
        }
        mCameraDevice!!.parameters = params
        return params.zoom.toFloat() / params.maxZoom
    }

    fun switchCamera(): Boolean {
        if (state != State.PREVIEW) {
            return false
        }
        try {
            val camera = mCameraDatas!!.removeAt(1)
            mCameraDatas!!.add(0, camera)
            openCamera()
            startPreview()
            return true
        } catch (e: Exception) {
            val camera = mCameraDatas!!.removeAt(1)
            mCameraDatas!!.add(0, camera)
            try {
                openCamera()
                startPreview()
            } catch (e1: Exception) {
                e1.printStackTrace()
            }

            e.printStackTrace()
            return false
        }

    }

    fun switchLight(): Boolean {
        if (state != State.PREVIEW || mCameraDevice == null || cameraData == null) {
            return false
        }
        if (!cameraData!!.hasLight) {
            return false
        }
        val cameraParameters = mCameraDevice!!.parameters
        if (cameraParameters.flashMode == Camera.Parameters.FLASH_MODE_OFF) {
            cameraParameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
        } else {
            cameraParameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
        }
        try {
            mCameraDevice!!.parameters = cameraParameters
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }


    /**
     * 设置预览回调，用于软编
     *
     * @param previewCallback
     */
    fun setPreviewCallback(previewCallback: Camera.PreviewCallback) {
        CameraUtils.setPreviewCallback(previewCallback)
    }

    fun updateTexImage() {
        val texture = mTexture
        if (texture == null) {
            LogHelper.w(TAG, "updateTexImage skipped: texture=null state=$state")
            return
        }
        try {
            texture.updateTexImage()
            frameCount += 1
            val now = SystemClock.elapsedRealtime()
            if (frameCount <= 5 || now - lastFrameLogTimestamp >= 2000) {
                LogHelper.d(
                    TAG,
                    "updateTexImage frame=$frameCount timestamp=${texture.timestamp} state=$state"
                )
                lastFrameLogTimestamp = now
            }
        } catch (error: Throwable) {
            LogHelper.e(
                TAG,
                "updateTexImage failed: ${error.javaClass.simpleName} ${error.message}"
            )
            throw error
        }
    }

    /**
     * 是否后摄像头打开
     */
    fun isOpenBackFirst(): Boolean {
        return isOpenBackFirst
    }

    companion object {
        private val TAG = "CameraHolder"
        private val FOCUS_WIDTH = 80
        private val FOCUS_HEIGHT = 80

        private var sHolder: CameraHolder? = null

        @Synchronized
        fun instance(): CameraHolder {
            if (sHolder == null) {
                sHolder = CameraHolder()
            }
            return sHolder as CameraHolder
        }
    }

}
