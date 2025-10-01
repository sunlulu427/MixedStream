package com.devyk.av.rtmp.library.widget

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.WindowManager
import com.devyk.av.rtmp.library.callback.ICameraOpenListener
import com.devyk.av.rtmp.library.camera.CameraHolder
import com.devyk.av.rtmp.library.camera.Watermark
import com.devyk.av.rtmp.library.camera.renderer.CameraRenderer
import com.devyk.av.rtmp.library.config.CameraConfiguration
import com.devyk.av.rtmp.library.config.RendererConfiguration
import com.devyk.av.rtmp.library.utils.LogHelper


open class CameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GLSurfaceView(context, attrs, defStyleAttr), SurfaceTexture.OnFrameAvailableListener {
    /**
     * Camera 渲染器
     */
    protected lateinit var renderer: CameraRenderer

    /**
     * 相机预览的纹理 ID
     */
    protected var mTextureId = -1;
    protected var mCameraTexture = -1;
    protected var mCameraOpenListener: ICameraOpenListener? = null
    private var glReady = false
    private var pendingWatermark: Watermark? = null
    private var fallbackTried = false

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
        renderer = CameraRenderer(context)
        LogHelper.d(TAG, "startPreview: configure renderer and GL thread")
        configure(
            RendererConfiguration(
                renderer = renderer,
                rendererMode = RENDERERMODE_CONTINUOUSLY
            )
        )
        //第一次需要初始化预览角度
        previewAngle(context)
        addRendererListener()
    }

    private fun addRendererListener() {
        LogHelper.d(TAG, "addRendererListener")
        renderer.setOnRendererListener(object : CameraRenderer.OnRendererListener {
            override fun onCreate(surfaceTexture: SurfaceTexture, textureID: Int) {
                LogHelper.d(TAG, "onRendererCreate(surfaceTexture) textureID=$textureID")
                mTextureId = textureID
                tryOpenCamera(surfaceTexture)
            }

            override fun onCreate(cameraTextureId: Int, textureID: Int) {
                LogHelper.d(TAG, "onRendererCreate(cameraTextureId=$cameraTextureId, textureID=$textureID)")

            }

            override fun onDraw() {
                CameraHolder.instance().updateTexImage()
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
        val holder = CameraHolder.instance()
        LogHelper.d(
            TAG,
            "openCameraWith -> request ${mCameraConfiguration.width}x${mCameraConfiguration.height} fps=${mCameraConfiguration.fps}"
        )
        holder.setConfiguration(mCameraConfiguration)
        holder.openCamera()
        holder.cameraData?.let { data ->
            LogHelper.d(TAG, "camera opened -> id=${data.cameraID} preview=${data.cameraWidth}x${data.cameraHeight}")
        }
        holder.setSurfaceTexture(surfaceTexture, this)
        holder.startPreview()
        LogHelper.i(
            TAG,
            "camera opened with requested ${mCameraConfiguration.width}x${mCameraConfiguration.height}"
        )
        glReady = true
        pendingWatermark?.let {
            try {
                renderer.setWatemark(it)
            } catch (t: Throwable) {
                LogHelper.e(TAG, "apply pending watermark failed: ${t.message}")
            }
            pendingWatermark = null
        }
        holder.cameraData?.let { data ->
            mCameraOpenListener?.onCameraPreviewSizeSelected(data.cameraWidth, data.cameraHeight)
        }
        mCameraOpenListener?.onCameraOpen()
    }

    private fun handleCameraInitFailure(surfaceTexture: SurfaceTexture, error: Throwable) {
        LogHelper.e(
            TAG,
            "open camera failed (${mCameraConfiguration.width}x${mCameraConfiguration.height}): ${error.javaClass.simpleName} ${error.message}"
        )
        CameraHolder.instance().releaseCamera()
        CameraHolder.instance().release()
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
            LogHelper.w(
                TAG,
                "retry camera with safe preview ${fallback.width}x${fallback.height}"
            )
            mCameraConfiguration = fallback
            try {
                openCameraWith(surfaceTexture)
                LogHelper.i(TAG, "camera fallback succeeded with ${fallback.width}x${fallback.height}")
                return
            } catch (fallbackError: Throwable) {
                LogHelper.e(
                    TAG,
                    "fallback camera open failed: ${fallbackError.javaClass.simpleName} ${fallbackError.message}"
                )
                CameraHolder.instance().releaseCamera()
                CameraHolder.instance().release()
                mCameraOpenListener?.onCameraError(
                    "Camera unavailable: ${fallbackError.message ?: fallbackError.javaClass.simpleName}"
                )
                return
            }
        }
        mCameraOpenListener?.onCameraError(
            "Camera unavailable: ${error.message ?: error.javaClass.simpleName}"
        )
    }

    /**
     * 释放 Camera 资源的时候调用
     */
    open fun releaseCamera() {
        CameraHolder.instance().stopPreview()
        CameraHolder.instance().releaseCamera()
        CameraHolder.instance().release()
    }

    @Synchronized
    open fun switchCamera(): Boolean {
        val ret = CameraHolder.instance().switchCamera()
        if (ret) {
            cameraId = cameraId.switch()
            renderer.switchCamera(object : CameraRenderer.OnSwitchCameraListener {
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
        if (!this::renderer.isInitialized || !glReady) {
            pendingWatermark = watermark
            return
        }
        renderer.setWatemark(watermark)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        LogHelper.d(TAG, "onFrameAvailable -> texture=$mTextureId rendererReady=$glReady")
    }


    fun previewAngle(context: Context) {
        previewAngle(context, true)
    }

    private fun previewAngle(context: Context, isResetMatrix: Boolean) {
        val rotation =
            (context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        LogHelper.d(TAG, "旋转角度：$rotation")
        renderer.resetMatrix()
        when (rotation) {
            Surface.ROTATION_0 -> {
                if (cameraId == CameraConfiguration.Facing.BACK) {
                    renderer.setAngle(90, 0, 0, 1);
                    renderer.setAngle(180, 1, 0, 0);
                } else {
                    renderer.setAngle(90, 0, 0, 1);
                }
            }

            Surface.ROTATION_90 -> {
                if (cameraId == CameraConfiguration.Facing.BACK) {
                    renderer.setAngle(180, 0, 0, 1);
                    renderer.setAngle(180, 0, 1, 0);
                } else {
                    renderer.setAngle(180, 0, 0, 1);
                }
            }

            Surface.ROTATION_180 -> {
                if (cameraId == CameraConfiguration.Facing.BACK) {
                    renderer.setAngle(90, 0, 0, 1);
                    renderer.setAngle(180, 0, 1, 0);
                } else {
                    renderer.setAngle(-90, 0, 0, 1);
                }
            }

            Surface.ROTATION_270 -> {
                if (cameraId == CameraConfiguration.Facing.BACK) {
                    renderer.setAngle(180, 0, 1, 0);
                } else {
                    renderer.setAngle(0, 0, 0, 1);
                }
            }
        }
    }

    /**
     * 拿到纹理 ID
     */
    fun getTextureId(): Int = mTextureId

    fun addCameraOpenCallback(listener: ICameraOpenListener) {
        mCameraOpenListener = listener
    }

    companion object {
        private const val SAFE_PREVIEW_WIDTH = 720
        private const val SAFE_PREVIEW_HEIGHT = 1280
    }
}
