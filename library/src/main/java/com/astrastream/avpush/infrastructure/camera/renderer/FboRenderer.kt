package com.astrastream.avpush.infrastructure.camera.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import com.astrastream.avpush.R
import com.astrastream.avpush.domain.callback.IRenderer
import com.astrastream.avpush.infrastructure.camera.ShaderHelper
import com.astrastream.avpush.infrastructure.camera.Watermark
import com.astrastream.avpush.core.utils.BitmapUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FboRenderer(private val context: Context) : IRenderer {
    /**
     * 顶点坐标
     */
    private val mVertexData: FloatArray = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f,

        0.6f, -0.9f, //第一个点 左下角
        0.9f, -0.9f,
        0.6f, -0.6f,
        0.9f, -0.6f
    )

    /**
     * 水印坐标
     */
    private var mWatemarkData: FloatArray? = null

    /**
     * 纹理坐标
     */
    private val mFragmentData: FloatArray = floatArrayOf(
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

    /**
     * 使用 VBO
     * 概念:
     * - 不使用VBO时，我们每次绘制（ glDrawArrays ）图形时都是从本地内存处获取顶点数据然后传输给OpenGL来绘制，这样就会频繁的操作CPU->GPU增大开销，从而降低效率。
     * - 使用VBO，我们就能把顶点数据缓存到GPU开辟的一段内存中，然后使用时不必再从本地获取，而是直接从显存中获取，这样就能提升绘制的效率。
     *
     *
     */
    private var mVboID = 0;

    /**
     *纹理采样器 （获取对应的纹理ID）
     */
    private var sampler = 0

    /**
     * 为顶点坐标分配 native 地址空间
     */
    private val mVertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(mVertexData.size * 4)
        .order(ByteOrder.nativeOrder()) //大内存在前面，字节对齐
        .asFloatBuffer()
        .put(mVertexData).also {
            it.position(0)
        }
    /**
     * 为片元坐标分配 native 地址空间
     */
    private val mFragmentBuffer: FloatBuffer = ByteBuffer.allocateDirect(mFragmentData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(mFragmentData).also {
            it.position(0)
        }

    private var mBitmap: Bitmap? = null
    private var mBitmapTextureId = -1

    init {
        initWatemark()
    }

    override fun onSurfaceCreate(width: Int, height: Int) {
        //启用透明
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        //1. 获取顶点/片元源代码资源
        val vertexSource = ShaderHelper.getRawShaderResource(context, R.raw.vertex_shader)
        val fragmentSource = ShaderHelper.getRawShaderResource(context, R.raw.fragment_shader)

        //2. 为 顶点和片元创建一个执行程序
        program = ShaderHelper.createProgram(vertexSource, fragmentSource)

        //3. 拿到顶点/片元 源代码中的索引位置
        vPosition = GLES20.glGetAttribLocation(program, "v_Position")
        fPosition = GLES20.glGetAttribLocation(program, "f_Position")
        sampler = GLES20.glGetAttribLocation(program, "sTexture")

        //4. 生成一个 VBO
        val vbo = IntArray(1)
        GLES20.glGenBuffers(1, vbo, 0);
        mVboID = vbo[0]
        //4.1 绑定 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboID)
        //4.2 分配 VBO 需要的缓存大小
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            mVertexData.size * 4 + mFragmentData.size * 4,
            null,
            GLES20.GL_STATIC_DRAW
        );
        //4.3 为 VBO 设置顶点、片元数据的值
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mVertexData.size * 4, mVertexBuffer);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mVertexData.size * 4, mFragmentData.size * 4, mFragmentBuffer);
        //4.4 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        mBitmapTextureId = createWatemark()


    }

    override fun onSurfaceChange(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDraw() {
    }

    fun onDraw(textureId: Int) {
        //相当于清屏
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)

        //1. 使用顶点和片元创建出来的执行程序
        GLES20.glUseProgram(program)

        //2. 绑定纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        //3. 绑定 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboID);

        //4. 设置顶点坐标
        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 8, 0)

        //5. 设置纹理坐标
        GLES20.glEnableVertexAttribArray(fPosition)
        GLES20.glVertexAttribPointer(fPosition, 2, GLES20.GL_FLOAT, false, 8, mVertexData.size * 4)

        //6. 开始绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (mBitmapTextureId != -1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBitmapTextureId)
            GLES20.glEnableVertexAttribArray(vPosition)
            GLES20.glVertexAttribPointer(
                vPosition, 2, GLES20.GL_FLOAT, false, 8,
                32
            );

            GLES20.glEnableVertexAttribArray(fPosition);
            GLES20.glVertexAttribPointer(
                fPosition, 2, GLES20.GL_FLOAT, false, 8,
                mVertexData.size * 4
            )
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }


        //7. 解绑
        // 解绑 纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        // 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun initWatemark() {
        if (mBitmap == null) {
            mBitmap = BitmapUtils.createBitmapByCanvas("       ", 20f, Color.WHITE, Color.TRANSPARENT)
        }

        mWatemarkData?.let { watemark ->
            mVertexData[8] = watemark[0];
            mVertexData[9] = watemark[1];

            mVertexData[10] = watemark[2];
            mVertexData[11] = watemark[3];

            mVertexData[12] = watemark[4];
            mVertexData[13] = watemark[5];

            mVertexData[14] = watemark[6];
            mVertexData[15] = watemark[7];
        }
    }

    private fun createWatemark(): Int {
        val watemarkId = ShaderHelper.loadBitmapTexture(mBitmap!!);
        //解绑纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return watemarkId
    }

    fun setWatemark(watermark: Watermark) {

        watermark.txt?.let {
//            mBitmap =
//                BitmapUtils.creatBitmap(it, context, watermark.textSize, watermark.textColor, Color.TRANSPARENT)
            mBitmap = BitmapUtils.createBitmapByCanvas(it, watermark.textSize.toFloat(), watermark.textColor)
        }

        watermark.markImg?.let {
            mBitmap = it
        }

        if (watermark.floatArray == null) {
            mBitmap?.let { bitmap ->
                val r = 1.0f * bitmap.getWidth() / bitmap.getHeight();
                val w = r * 0.1f;
                mVertexData[8] =0.7f - w;
                mVertexData[9] = -0.9f;
                mVertexData[10] = 0.9f;
                mVertexData[11] = -0.9f;
                mVertexData[12] = 0.7f - w;
                mVertexData[13] = -0.75f;
                mVertexData[14] = 0.9f;
                mVertexData[15] = -0.75f;
            }
        }

        watermark.floatArray?.let {
            this.mWatemarkData = watermark.floatArray
        }
        initWatemark()
        mBitmapTextureId = createWatemark()
    }
}
