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


/**
 * <pre>
 *     author  : devyk on 20-07-07 22:47
 *     blog    : https://juejin.im/user/578259398ac24700613a3fb/posts
 *     github  : https://github.com/yangkun19921001
 *     mailbox : yang1001yk@gmail.com
 *     desc    : This is CameraView
 * </pre>
 */
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
        renderer = CameraRenderer(context)
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
        renderer.setOnRendererListener(object : CameraRenderer.OnRendererListener {
            override fun onCreate(surfaceTexture: SurfaceTexture, textureID: Int) {
                mTextureId = textureID
                try {
                    CameraHolder.instance().setConfiguration(mCameraConfiguration)
                    CameraHolder.instance().openCamera()
                    CameraHolder.instance().setSurfaceTexture(surfaceTexture, this@CameraView)
                    CameraHolder.instance().startPreview()
                    LogHelper.i(TAG, "camera opened, textureId=${mTextureId}")
                    mCameraOpenListener?.onCameraOpen()
                } catch (t: Throwable) {
                    // 关键路径容错：相机服务不可用/无权限/设备无相机时不崩溃，记录错误日志
                    LogHelper.e(TAG, "open camera failed: ${t.javaClass.simpleName} ${t.message}")
                }
            }

            override fun onCreate(cameraTextureId: Int, textureID: Int) {

            }

            override fun onDraw() {
                CameraHolder.instance().updateTexImage()
            }
        })
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
        renderer.setWatemark(watermark)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        LogHelper.e(TAG, "相机纹理刷新");
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
}
