package com.astra.avpush.presentation.widget

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.WindowManager
import com.astra.avpush.domain.camera.CameraDevice
import com.astra.avpush.domain.config.CameraConfiguration
import com.astra.avpush.domain.config.RendererConfiguration
import com.astra.avpush.infrastructure.camera.NativeCameraDevice
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.camera.renderer.CameraRenderer
import com.astra.avpush.runtime.AstraLog


open class CameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GLSurfaceView(context, attrs, defStyleAttr), SurfaceTexture.OnFrameAvailableListener {
    /**
     * Camera 渲染器
     */
    protected lateinit var previewRenderer: CameraRenderer

    /**
     * 相机预览的纹理 ID
     */
    protected var mTextureId = -1
    protected var mCameraTexture = -1
    private var onCameraOpened: (() -> Unit)? = null
    private var onCameraErrorCallback: ((String) -> Unit)? = null
    private var onPreviewSizeSelected: ((Int, Int) -> Unit)? = null
    private var glReady = false
    private var pendingWatermark: Watermark? = null
    private var fallbackTried = false
    private var cameraDevice: CameraDevice = NativeCameraDevice()
    private var currentSurface: SurfaceTexture? = null

    fun attachCameraDevice(device: CameraDevice) {
        cameraDevice = device
    }

    fun setCameraOpenedCallback(listener: (() -> Unit)?) {
        onCameraOpened = listener
    }

    fun setCameraErrorCallback(listener: ((String) -> Unit)?) {
        onCameraErrorCallback = listener
    }

    fun setCameraPreviewSizeCallback(listener: ((Int, Int) -> Unit)?) {
        onPreviewSizeSelected = listener
    }

    /**
     * 相机预览默认配置
     */
    private var mCameraConfiguration = CameraConfiguration()

    /**
     * 默认后置摄像头
     */
    private var cameraId = CameraConfiguration.Facing.BACK

    fun startPreview(cameraConfiguration: CameraConfiguration) {
        this.mCameraConfiguration = cameraConfiguration
        cameraId = cameraConfiguration.facing
        fallbackTried = false
        previewRenderer = CameraRenderer(context)
        AstraLog.d(TAG, "startPreview: configure renderer and GL thread")
        configure(
            RendererConfiguration(
                renderer = previewRenderer,
                rendererMode = RENDERERMODE_CONTINUOUSLY
            )
        )
        //第一次需要初始化预览角度
        previewAngle(context)
        addRendererListener()
    }

    private fun addRendererListener() {
        AstraLog.d(TAG, "addRendererListener")
        previewRenderer.setOnRendererListener(object : CameraRenderer.OnRendererListener {
            override fun onCreate(surfaceTexture: SurfaceTexture, textureID: Int) {
                AstraLog.d(TAG, "onRendererCreate(surfaceTexture) textureID=$textureID")
                mTextureId = textureID
                tryOpenCamera(surfaceTexture)
            }

            override fun onCreate(cameraTextureId: Int, textureID: Int) {
                AstraLog.d(TAG, "onRendererCreate(cameraTextureId=$cameraTextureId, textureID=$textureID)")

            }

            override fun onDraw() {
                cameraDevice.updateTexImage()
            }
        })
    }

    private fun tryOpenCamera(surfaceTexture: SurfaceTexture) {
        try {
            openCameraWith(surfaceTexture)
        } catch (error: Throwable) {
            handleCameraInitFailure(surfaceTexture, error)
        }
    }

    @Synchronized
    private fun openCameraWith(surfaceTexture: SurfaceTexture) {
        AstraLog.d(
            TAG,
            "openCameraWith -> request ${mCameraConfiguration.width}x${mCameraConfiguration.height} fps=${mCameraConfiguration.fps}"
        )
        cameraDevice.configure(mCameraConfiguration)
        cameraDevice.open()
        currentSurface = surfaceTexture
        cameraDevice.bind(surfaceTexture, this)
        cameraDevice.startPreview()
        AstraLog.i(
            TAG,
            "camera opened with requested ${mCameraConfiguration.width}x${mCameraConfiguration.height}"
        )
        glReady = true
        pendingWatermark?.let {
            try {
                previewRenderer.setWatemark(it)
            } catch (t: Throwable) {
                AstraLog.e(TAG, "apply pending watermark failed: ${t.message}")
            }
            pendingWatermark = null
        }
        cameraDevice.currentDescriptor?.let { descriptor ->
            onPreviewSizeSelected?.invoke(descriptor.previewWidth, descriptor.previewHeight)
        }
        onCameraOpened?.invoke()
    }

    private fun handleCameraInitFailure(surfaceTexture: SurfaceTexture, error: Throwable) {
        AstraLog.e(
            TAG,
            "open camera failed (${mCameraConfiguration.width}x${mCameraConfiguration.height}): ${error.javaClass.simpleName} ${error.message}"
        )
        cameraDevice.releaseCamera()
        cameraDevice.releaseResources()
        if (!fallbackTried) {
            fallbackTried = true
            val fallback = CameraConfiguration(
                width = SAFE_PREVIEW_WIDTH,
                height = SAFE_PREVIEW_HEIGHT,
                fps = mCameraConfiguration.fps,
                rotation = mCameraConfiguration.rotation,
                facing = mCameraConfiguration.facing,
                orientation = mCameraConfiguration.orientation,
                focusMode = mCameraConfiguration.focusMode
            )
            AstraLog.w(
                TAG,
                "retry camera with safe preview ${fallback.width}x${fallback.height}"
            )
            mCameraConfiguration = fallback
            try {
                openCameraWith(surfaceTexture)
                AstraLog.i(TAG, "camera fallback succeeded with ${fallback.width}x${fallback.height}")
                return
            } catch (fallbackError: Throwable) {
                AstraLog.e(
                    TAG,
                    "fallback camera open failed: ${fallbackError.javaClass.simpleName} ${fallbackError.message}"
                )
                cameraDevice.releaseCamera()
                cameraDevice.releaseResources()
                onCameraErrorCallback?.invoke(
                    "Camera unavailable: ${fallbackError.message ?: fallbackError.javaClass.simpleName}"
                )
                return
            }
        }
        onCameraErrorCallback?.invoke(
            "Camera unavailable: ${error.message ?: error.javaClass.simpleName}"
        )
    }

    /**
     * 释放 Camera 资源的时候调用
     */
    open fun releaseCamera() {
        cameraDevice.stopPreview()
        cameraDevice.releaseCamera()
        cameraDevice.releaseResources()
        currentSurface?.setOnFrameAvailableListener(null)
        currentSurface = null
    }

    @Synchronized
    open fun switchCamera(): Boolean {
        val ret = cameraDevice.switchCamera()
        if (ret) {
            cameraId = cameraId.switch()
            currentSurface?.let {
                cameraDevice.bind(it, this)
                cameraDevice.startPreview()
            }
            previewRenderer.switchCamera(object : CameraRenderer.OnSwitchCameraListener {
                override fun onChange(): Boolean {
                    previewAngle(context)
                    return true
                }
            })
        }
        return ret
    }

    /**
     * 设置水印
     */
    open fun setWatermark(watermark: Watermark) {
        // 若 GL/renderer 尚未就绪，则保存待应用
        if (!this::previewRenderer.isInitialized || !glReady) {
            pendingWatermark = watermark
            return
        }
        previewRenderer.setWatemark(watermark)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        AstraLog.d(TAG, "onFrameAvailable -> texture=$mTextureId rendererReady=$glReady")
    }


    fun previewAngle(context: Context) {
        previewAngle(context, true)
    }

    private fun previewAngle(context: Context, isResetMatrix: Boolean) {
        val rotation =
            (context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        AstraLog.d(TAG, "旋转角度：$rotation")
        previewRenderer.resetMatrix()
        when (rotation) {
            Surface.ROTATION_0 -> {
                if (cameraId == CameraConfiguration.Facing.BACK) {
                    previewRenderer.setAngle(90, 0, 0, 1);
                    previewRenderer.setAngle(180, 1, 0, 0);
                } else {
                    previewRenderer.setAngle(90, 0, 0, 1);
                }
            }

            Surface.ROTATION_90 -> {
                if (cameraId == CameraConfiguration.Facing.BACK) {
                    previewRenderer.setAngle(180, 0, 0, 1);
                    previewRenderer.setAngle(180, 0, 1, 0);
                } else {
                    previewRenderer.setAngle(180, 0, 0, 1);
                }
            }

            Surface.ROTATION_180 -> {
                if (cameraId == CameraConfiguration.Facing.BACK) {
                    previewRenderer.setAngle(90, 0, 0, 1);
                    previewRenderer.setAngle(180, 0, 1, 0);
                } else {
                    previewRenderer.setAngle(-90, 0, 0, 1);
                }
            }

            Surface.ROTATION_270 -> {
                if (cameraId == CameraConfiguration.Facing.BACK) {
                    previewRenderer.setAngle(180, 0, 1, 0);
                } else {
                    previewRenderer.setAngle(0, 0, 0, 1);
                }
            }
        }
    }

    /**
     * 拿到纹理 ID
     */
    fun getTextureId(): Int = mTextureId

    companion object {
        private const val SAFE_PREVIEW_WIDTH = 720
        private const val SAFE_PREVIEW_HEIGHT = 1280
    }
}
