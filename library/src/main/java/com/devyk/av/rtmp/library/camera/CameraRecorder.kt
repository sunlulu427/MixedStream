package com.devyk.av.rtmp.library.camera

import android.content.Context
import android.view.Surface
import com.devyk.av.camera_recorder.callback.IGLThreadConfig
import com.devyk.av.rtmp.library.callback.IRenderer
import com.devyk.av.rtmp.library.camera.renderer.EncodeRenderer
import com.devyk.av.rtmp.library.mediacodec.VideoEncoder
import com.devyk.av.rtmp.library.widget.GLSurfaceView
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGLContext

/**
 * <pre>
 *     author  : devyk on 2020-07-11 15:18
 *     blog    : https://juejin.im/user/578259398ac2470061f3a3fb/posts
 *     github  : https://github.com/yangkun19921001
 *     mailbox : yang1001yk@gmail.com
 *     desc    : This is CameraRecorder 摄像头录制
 * </pre>
 */
class CameraRecorder(context: Context, textureId: Int, private val eglContext: EGLContext?) :
    VideoEncoder(), IGLThreadConfig {
    private val mRenderer: EncodeRenderer = EncodeRenderer(context, textureId)
    private var mRendererMode = GLSurfaceView.RENDERERMODE_CONTINUOUSLY
    private var mGLThread: EncodeRendererThread? = null
    private var mSurface: Surface? = null

    /**
     * surface 创建的时候开始进行 GL 线程渲染
     */
    override fun onSurfaceCreate(surface: Surface?) {
        super.onSurfaceCreate(surface)
        mSurface = surface
        mGLThread = EncodeRendererThread(WeakReference(this))
        mGLThread?.run {
            setRendererSize(mConfiguration.width, mConfiguration.height)
            isCreate = true
            isChange = true
            start()
        }
    }

    override fun stop() {
        super.stop()
        mGLThread?.onDestory()
    }


    fun pause() {
        mGLThread?.setPause()
    }

    fun resume() {
        mGLThread?.setResume()
    }

    override fun getSurface(): Surface? {
        if (mSurface != null)
            return mSurface
        return super.getSurface()
    }

    override fun getEGLContext(): EGLContext? = eglContext
    override fun getRenderer(): IRenderer = mRenderer
    override fun getRendererMode(): Int = mRendererMode

    fun setWatermark(watermark: Watermark) {
        mRenderer.setWatemark(watermark)
    }

    /**
     * 摄像头渲染线程
     */
    class EncodeRendererThread(
        weakReference: WeakReference<IGLThreadConfig>
    ) : GLThread(weakReference)

    /**
     * 编码完成的 H264 数据
     *   00 00 00 01 06:  SEI信息
     *   00 00 00 01 67:  0x67&0x1f = 0x07 :SPS
     *   00 00 00 01 68:  0x68&0x1f = 0x08 :PPS
     *   00 00 00 01 65:  0x65&0x1f = 0x05: IDR Slice
     */
//    fun onVideoEncodes(bb: ByteBuffer?, bi: MediaCodec.BufferInfo) {
//        var h264Arrays = ByteArray(bi.size)
//        bb?.position(bi.offset)
//        bb?.limit(bi.offset + bi.size)
//        bb?.get(h264Arrays)
//        val tag = h264Arrays[4].and(0x1f).toInt()
//        if (tag == 0x07) {//sps
//            LogHelper.e(TAG, " SPS " + h264Arrays.size)
//        } else if (tag == 0x08) {//pps
//            LogHelper.e(TAG, " PPS ")
//        } else if (tag == 0x05) {//关键字帧
//            LogHelper.e(TAG, " 关键帧 " + h264Arrays.size)
//        } else {
//            //普通帧
//            LogHelper.e(TAG, " 普通帧 " + h264Arrays.size)
//        }
//    }
}