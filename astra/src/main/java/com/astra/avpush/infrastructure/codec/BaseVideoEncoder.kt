package com.astra.avpush.infrastructure.codec


import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.runtime.AstraLog
import java.util.concurrent.locks.ReentrantLock

abstract class BaseVideoEncoder : IVideoCodec {

    private var mMediaCodec: MediaCodec? = null
    private var mPause: Boolean = false
    private var mHandlerThread: HandlerThread? = null
    private var mEncoderHandler: Handler? = null
    protected var mConfiguration = VideoConfiguration()
    private var mBufferInfo: MediaCodec.BufferInfo? = null

    @Volatile
    private var isStarted: Boolean = false
    private val encodeLock = ReentrantLock()
    private lateinit var mSurface: Surface
    val TAG = this.javaClass.simpleName

    protected var mPts = 0L

    /**
     * 准备硬编码工作
     */
    override fun prepare(videoConfiguration: VideoConfiguration) {
        videoConfiguration.run {
            mConfiguration = videoConfiguration
            mMediaCodec = VideoMediaCodec.getVideoMediaCodec(mConfiguration)
            AstraLog.e(TAG, "prepare success!")
        }
    }

    /**
     * 渲染画面销毁了 open 子类可以重写
     */
    protected open fun onSurfaceDestory(surface: Surface?) {
    }

    /**
     * 可以创建渲染画面了 open 子类可以重写
     */
    protected open fun onSurfaceCreate(surface: Surface?) {

    }


    /**
     * 创建一个输入型的 Surface
     */
    open fun getSurface(): Surface? {
        return mSurface
    }


    /**
     * 开始编码
     */
    override fun start() {
        mHandlerThread = HandlerThread("AVSample-Encode")

        mHandlerThread?.run {
            this.start()
            mEncoderHandler = Handler(getLooper())
            mBufferInfo = MediaCodec.BufferInfo()
            //必须在  mMediaCodec?.start() 之前
            mSurface = mMediaCodec!!.createInputSurface()
            mMediaCodec?.start()
            mEncoderHandler?.post(swapDataRunnable)
            isStarted = true
            //必须在  mMediaCodec?.start() 之后
            onSurfaceCreate(mSurface)
        }
    }

    /**
     * 编码的线程
     */
    private val swapDataRunnable = Runnable { drainEncoder() }

    /**
     * 停止编码
     */
    override fun stop() {
        if (!isStarted) return
        isStarted = false
        mEncoderHandler?.removeCallbacks(swapDataRunnable)
        mHandlerThread?.quit()
        encodeLock.lock()
        //提交一个空的缓冲区
        mMediaCodec?.signalEndOfInputStream()
        releaseEncoder()
        encodeLock.unlock()
    }

    /**
     * 释放编码器
     */
    private fun releaseEncoder() {
        onSurfaceDestory(getSurface())
        mMediaCodec?.stop()
        mMediaCodec?.release()
        mMediaCodec = null
    }

    /**
     * 动态码率设置
     */
    fun setEncodeBps(bps: Int) {
        if (mMediaCodec == null) {
            return
        }
        AstraLog.d(TAG, "bps :" + bps * 1024)
        val bitrate = Bundle()
        bitrate.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bps * 1024)
        mMediaCodec?.setParameters(bitrate)
    }

    /**
     * 解码函数
     */
    private fun drainEncoder() {
        var outBuffers = mMediaCodec?.outputBuffers
        if (!isStarted) {
            // if not running anymore, complete stream
            mMediaCodec?.signalEndOfInputStream()
        }
        while (isStarted) {
            encodeLock.lock()
            if (mMediaCodec != null) {
                val outBufferIndex = mMediaCodec?.dequeueOutputBuffer(mBufferInfo!!, 12000)


                if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    onVideoOutformat(mMediaCodec?.outputFormat)
                    encodeLock.unlock()
                    continue
                }

                if (outBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outBuffers = mMediaCodec?.outputBuffers
                    encodeLock.unlock()
                    continue
                }


                if (outBufferIndex != null && outBufferIndex >= 0) {
                    val buffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mMediaCodec?.getOutputBuffer(outBufferIndex)
                    } else {
                        outBuffers?.get(outBufferIndex)
                    }
                    if (mPts == 0L)
                        mPts = System.nanoTime() / 1000;

                    mBufferInfo!!.presentationTimeUs = System.nanoTime() / 1000 - mPts;

                    if (mBufferInfo?.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                        if (!mPause) {
                            onVideoOutformat(mMediaCodec?.outputFormat)
                        }
                    }
                    AstraLog.e(TAG, "视频时间戳：${mBufferInfo!!.presentationTimeUs / 1000_000}")
                    if (!mPause && buffer != null) {
                        onVideoEncode(buffer, mBufferInfo!!)
                    }
                    mMediaCodec?.releaseOutputBuffer(outBufferIndex, false)
                    encodeLock.unlock()
                    continue
                } else {
                    try {
                        // wait 10ms
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                }
                encodeLock.unlock()
            } else {
                encodeLock.unlock()
                break
            }
        }
    }

    abstract fun onVideoOutformat(outputFormat: MediaFormat?)
}
