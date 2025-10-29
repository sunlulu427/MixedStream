package com.astra.avpush.infrastructure.camera.renderer

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import com.astra.avpush.domain.callback.IRenderer
import com.astra.avpush.infrastructure.camera.ShaderHelper
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.runtime.LogHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


class CameraRenderer(private val context: Context) : IRenderer {

    private var TAG = this.javaClass.simpleName

    /**
     * 顶点坐标
     */
    private val mVertexData: FloatArray = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    /**
     *  FBO 纹理坐标
     */
    private val mFragmentData: FloatArray = floatArrayOf(
//        0f, 0f,
//        1f, 0f,
//        0f, 1f,
//        1f, 1f

        //正常纹理坐标
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    /**
     * 执行着色器代码的程序
     */
    private var program = 0

    /**
     * 顶点索引
     */
    private var vPosition = 0

    /**
     * 纹理索引
     */
    private var fPosition = 0

    /**
     * 绘制的纹理 ID
     */
    private var mTextureID = 0
    private var samplerOesLocation = 0

    /**
     * 使用 VBO
     * 概念:
     * - 不使用VBO时，我们每次绘制（ glDrawArrays ）图形时都是从本地内存处获取顶点数据然后传输给OpenGL来绘制，
     *   这样就会频繁的操作CPU->GPU增大开销，从而降低效率。
     * - 使用VBO，我们就能把顶点数据缓存到GPU开辟的一段内存中，然后使用时不必再从本地获取，
     *   而是直接从显存中获取，这样就能提升绘制的效率。
     *
     */
    private var mVboID = 0

    /**
     *  FBO 概念：
     * 为什么要用FBO?
     * - 当我们需要对纹理进行多次渲染采样时，而这些渲染采样是不需要展示给用户看的，
     *   所以我们就可以用一个单独的缓冲对象（离屏渲染）来存储我们的这几次渲染采样的结果，
     *   等处理完后才显示到窗口上。
     *
     * 优势
     * - 提高渲染效率，避免闪屏，可以很方便的实现纹理共享等。
     */
    private var mFboID = 0


    /**
     * 矩阵值:正交投影
     */
    private var umatrix = 0

    /**
     * 为顶点坐标分配 native 地址空间
     */
    private val mVertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(mVertexData.size * 4)
        .order(ByteOrder.nativeOrder()) //大内存在前面，字节对齐
        .asFloatBuffer()
        .put(mVertexData).also {
            //指向第一个索引，相当于 C 里面的第一个内存地址
            it.position(0)
        }

    /**
     * 为片元坐标分配 native 地址空间
     */
    private val mFragmentBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(mFragmentData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(mFragmentData).also {
            it.position(0)
        }

    /**
     * fbo renderer
     */
    private val mFboRenderer: FboRenderer = FboRenderer(context)

    /**
     * bitmap 纹理 ID
     */
    private var mCameraTextureId = 0

    /**
     * 设置 fbo 离屏渲染的 size
     */
    private var mWidth = 1080
    private var mHeight = 1920

    private var mScreenWidth = 1080
    private var mScreenHeight = 1920

    /**
     * 设置 fbo 离屏渲染的 size
     */
    private var mBitmapWidth = 0f
    private var mbitmapHeight = 0f

    private var drawFrameCount = 0
    private var lastDrawLogTimestamp = 0L


    @Volatile
    private var isTexture = true
    private var switchCametaListener: OnSwitchCameraListener? = null

    /**
     * 做矩阵方向变换的
     */
    private val mMatrix = FloatArray(16)
    private var mRendererListener: OnRendererListener? = null

    override fun onSurfaceCreate(width: Int, height: Int) {
        LogHelper.d(TAG, "onSurfaceCreate w=$width h=$height")
        drawFrameCount = 0
        lastDrawLogTimestamp = 0L
        mScreenWidth = context.resources.displayMetrics.widthPixels
        mScreenHeight = context.resources.displayMetrics.heightPixels
        this.mHeight = height
        this.mWidth = width
        mFboRenderer.onSurfaceCreate(width, height)

        //1. 获取顶点/片元源代码资源
        val vertexSource = ShaderHelper.getScript(ShaderHelper.Script.CAMERA_VERTEX)
        val fragmentSource = ShaderHelper.getScript(ShaderHelper.Script.CAMERA_FRAGMENT)

        //2. 为 顶点和片元创建一个执行程序
        program = ShaderHelper.createProgram(vertexSource, fragmentSource)

        //3. 拿到顶点/片元 源代码中的索引位置
        vPosition = GLES20.glGetAttribLocation(program, "v_Position")
        fPosition = GLES20.glGetAttribLocation(program, "f_Position")
        umatrix = GLES20.glGetUniformLocation(program, "u_Matrix")
        samplerOesLocation = GLES20.glGetUniformLocation(program, "sTexture")

        //4. 生成一个 VBO
        val vbo = IntArray(1)
        GLES20.glGenBuffers(1, vbo, 0)
        mVboID = vbo[0]
        //4.1 绑定 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboID)
        //4.2 分配 VBO 需要的缓存大小
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            mVertexData.size * 4 + mFragmentData.size * 4,
            null,
            GLES20.GL_STATIC_DRAW
        )
        //4.3 为 VBO 设置顶点、片元数据的值
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mVertexData.size * 4, mVertexBuffer)
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER,
            mVertexData.size * 4,
            mFragmentData.size * 4,
            mFragmentBuffer
        )
        //4.4 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        //5. 生成一个 FBO
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        mFboID = fbo[0]
        //5.1 绑定 FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboID)


        //6. 生成一个纹理 ID
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        mTextureID = textureIds[0]

        //6.1. 指定将要绘制的纹理对象并且传递给对应的片元着色器中
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID)

        //6.2. 设置环绕和过滤方式
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        //5.2 设置 FBO 分配的内存大小
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            mWidth,
            mHeight,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )

        //5.3 把纹理绑定到 FBO 上
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            mTextureID,
            0
        )


        //5.4 检查 FBO 是否绑定成功
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            LogHelper.e(TAG, "fbo bind error")
        } else {
            LogHelper.e(TAG, "fbo bind success")
        }

        //5.5 解绑 FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        //解绑纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)


        //7.绘制 Camera 数据
        mCameraTextureId = genCameraTextureId()

        val surfaceTexture = SurfaceTexture(mCameraTextureId)
        // 将 SurfaceTexture 的默认缓冲区设置为当前渲染尺寸，避免尺寸不匹配导致黑屏
        try {
            surfaceTexture.setDefaultBufferSize(mWidth, mHeight)
        } catch (_: Throwable) {}
        mRendererListener?.onCreate(mCameraTextureId, mTextureID)
        mRendererListener?.onCreate(surfaceTexture, mTextureID)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    override fun onSurfaceChange(width: Int, height: Int) {
        //指定渲染框口的大小
        this.mHeight = height
        this.mWidth = width
    }

    override fun onDraw() {
        // 渲染一次
        // 注意：SurfaceTexture.updateTexImage() 在回调中调用
        if (!isTexture) {
            if (drawFrameCount == 0) {
                LogHelper.d(TAG, "onDraw skip while switching camera")
            }
            Thread.sleep(300)
            isTexture = switchCametaListener?.onChange()!!
            if (!isTexture)
                return
        }
        mRendererListener?.onDraw()
        drawFrameCount += 1

        //相当于清屏
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)

        //1. 使用顶点和片元创建出来的执行程序
        GLES20.glUseProgram(program)
        // 绘制到离屏 FBO，使用 FBO 尺寸
        GLES20.glViewport(0, 0, mWidth, mHeight)
        GLES20.glUniformMatrix4fv(umatrix, 1, false, mMatrix, 0)

        //绑定 fbo
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboID)

        // 绑定摄像头 OES 纹理到采样器
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureId)
        if (samplerOesLocation >= 0) {
            GLES20.glUniform1i(samplerOesLocation, 0)
        }

        //3. 绑定 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboID)


        //4. 设置顶点坐标
        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 8, 0)

        //5. 设置纹理坐标
        GLES20.glEnableVertexAttribArray(fPosition)
        GLES20.glVertexAttribPointer(fPosition, 2, GLES20.GL_FLOAT, false, 8, mVertexData.size * 4)

        //6. 开始绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        //7. 解绑
        // 解绑 OES 纹理
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        // 解绑 纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        // 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        //解绑 fbo
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        mFboRenderer.onSurfaceChange(mWidth, mHeight)
        //绘制纹理
        mFboRenderer.onDraw(mTextureID)

        val now = SystemClock.elapsedRealtime()
        val glError = GLES20.glGetError()
        val errorLabel = when (glError) {
            GLES20.GL_NO_ERROR -> "OK"
            GLES20.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GLES20.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GLES20.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GLES20.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            else -> "0x${Integer.toHexString(glError)}"
        }
        if (drawFrameCount <= 5 || now - lastDrawLogTimestamp >= 2000 || glError != GLES20.GL_NO_ERROR) {
            LogHelper.d(
                TAG,
                "onDraw frame=$drawFrameCount viewport=${mWidth}x${mHeight} cameraTex=$mCameraTextureId fboTex=$mTextureID glError=$errorLabel"
            )
            lastDrawLogTimestamp = now
        }
        if (glError != GLES20.GL_NO_ERROR) {
            LogHelper.e(TAG, "GL error detected in onDraw: $errorLabel")
        }
    }

    private fun genCameraTextureId(): Int {
        val textureidseos = IntArray(1)
        GLES20.glGenTextures(1, textureidseos, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureidseos[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_REPEAT
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_REPEAT
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        return textureidseos[0]

    }

    /**
     * 设置水印
     */
    fun setWatemark(watermark: Watermark) {
        mFboRenderer.setWatemark(watermark)
    }

    interface OnRendererListener {
        fun onCreate(cameraTextureId: Int, textureID: Int)
        fun onCreate(surfaceTexture: SurfaceTexture, textureID: Int)
        fun onDraw()
    }

    fun setOnRendererListener(listener: OnRendererListener) {
        this.mRendererListener = listener
    }

    /**
     * 设置方向
     */
    fun setAngle(angle: Int, x: Int, y: Int, z: Int) {
        Matrix.rotateM(mMatrix, 0, angle.toFloat(), x.toFloat(), y.toFloat(), z.toFloat())
    }

    fun resetMatrix() {
        Matrix.setIdentityM(mMatrix, 0)
    }

    fun switchCamera(listener: OnSwitchCameraListener) {
        isTexture = false
        this.switchCametaListener = listener

    }

    interface OnSwitchCameraListener {
        fun onChange(): Boolean
    }
}
