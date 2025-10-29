package com.astra.avpush.infrastructure.camera.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import com.astra.avpush.domain.callback.IRenderer
import com.astra.avpush.infrastructure.camera.ShaderHelper
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.runtime.BitmapUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

class FboRenderer(private val context: Context) : IRenderer {
    companion object {
        private val DEFAULT_WATERMARK_COORDS = floatArrayOf(
            0.6f, -0.9f,
            0.9f, -0.9f,
            0.6f, -0.6f,
            0.9f, -0.6f
        )
        private const val MAX_HEIGHT_NDC = 0.3f
        private const val MIN_HEIGHT_NDC = 0.1f
        private const val MAX_WIDTH_NDC = 0.6f
        private const val HORIZONTAL_MARGIN = 0.05f
        private const val VERTICAL_MARGIN = 0.06f
    }
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
    private var mWatemarkData: FloatArray = DEFAULT_WATERMARK_COORDS.copyOf()

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
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var pendingWatermark: Watermark? = null
    private var currentWatermark: Watermark? = null

    init {
        mBitmap = BitmapUtils.createBitmapByCanvas("       ", 20f, Color.WHITE, Color.TRANSPARENT)
        updateVertexBufferWith(mWatemarkData)
    }

    override fun onSurfaceCreate(width: Int, height: Int) {
        //启用透明
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        //1. 获取顶点/片元源代码资源
        val vertexSource = ShaderHelper.getScript(ShaderHelper.Script.BASIC_VERTEX)
        val fragmentSource = ShaderHelper.getScript(ShaderHelper.Script.BASIC_FRAGMENT)

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

        mBitmap?.let { uploadBitmapTexture(it) }
        attemptApplyWatermark()
    }

    override fun onSurfaceChange(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val sizeChanged = surfaceWidth != width || surfaceHeight != height
        surfaceWidth = width
        surfaceHeight = height
        if (sizeChanged && pendingWatermark == null) {
            pendingWatermark = currentWatermark
        }
        attemptApplyWatermark()
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

    private fun attemptApplyWatermark() {
        val watermark = pendingWatermark ?: return
        if (watermark.floatArray == null && (surfaceWidth == 0 || surfaceHeight == 0)) {
            return
        }
        if (applyWatermark(watermark)) {
            pendingWatermark = null
        }
    }

    private fun applyWatermark(watermark: Watermark): Boolean {
        val bitmap = prepareBitmap(watermark)
        val coords = watermark.floatArray?.takeIf { it.size >= 8 }?.copyOf(8)
            ?: computeDefaultQuad(bitmap, watermark)
            ?: return false

        mWatemarkData = coords
        updateVertexBufferWith(mWatemarkData)
        uploadBitmapTexture(bitmap)
        return true
    }

    private fun prepareBitmap(watermark: Watermark): Bitmap {
        val bitmap = when {
            watermark.markImg != null -> watermark.markImg!!
            watermark.txt != null -> {
                val text = watermark.txt!!
                val size = if (watermark.textSize > 0) watermark.textSize.toFloat() else 32f
                val color = if (watermark.textColor != -1) watermark.textColor else Color.WHITE
                BitmapUtils.createBitmapByCanvas(text, size, color)
            }
            mBitmap != null -> mBitmap!!
            else -> BitmapUtils.createBitmapByCanvas(" ", 20f, Color.WHITE, Color.TRANSPARENT)
        }
        mBitmap = bitmap
        return bitmap
    }

    private fun computeDefaultQuad(bitmap: Bitmap, watermark: Watermark): FloatArray? {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return null

        val safeScale = watermark.scale.takeIf { it > 0f } ?: 1f
        val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)

        var targetHeight = (2f * bitmap.height / surfaceHeight.toFloat()) * safeScale
        targetHeight = targetHeight.coerceIn(MIN_HEIGHT_NDC, MAX_HEIGHT_NDC)
        var targetWidth = targetHeight * ratio
        if (targetWidth > MAX_WIDTH_NDC) {
            val factor = MAX_WIDTH_NDC / targetWidth
            targetWidth = MAX_WIDTH_NDC
            targetHeight = max(MIN_HEIGHT_NDC, targetHeight * factor)
        }

        var right = 1f - HORIZONTAL_MARGIN
        var left = right - targetWidth
        if (left < -1f + HORIZONTAL_MARGIN) {
            val shift = (-1f + HORIZONTAL_MARGIN) - left
            left += shift
            right += shift
        }

        var bottom = -1f + VERTICAL_MARGIN
        var top = bottom + targetHeight
        if (top > 1f - VERTICAL_MARGIN) {
            val shift = top - (1f - VERTICAL_MARGIN)
            top -= shift
            bottom -= shift
        }

        return floatArrayOf(
            left, bottom,
            right, bottom,
            left, top,
            right, top
        )
    }

    private fun updateVertexBufferWith(coords: FloatArray) {
        if (coords.size < 8) return
        mVertexData[8] = coords[0]
        mVertexData[9] = coords[1]
        mVertexData[10] = coords[2]
        mVertexData[11] = coords[3]
        mVertexData[12] = coords[4]
        mVertexData[13] = coords[5]
        mVertexData[14] = coords[6]
        mVertexData[15] = coords[7]

        mVertexBuffer.position(0)
        mVertexBuffer.put(mVertexData)
        mVertexBuffer.position(0)

        if (mVboID != 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboID)
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mVertexData.size * 4, mVertexBuffer)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        }
    }

    private fun uploadBitmapTexture(bitmap: Bitmap) {
        if (mBitmapTextureId != -1) {
            val textures = IntArray(1)
            textures[0] = mBitmapTextureId
            GLES20.glDeleteTextures(1, textures, 0)
        }
        mBitmapTextureId = ShaderHelper.loadBitmapTexture(bitmap)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun setWatemark(watermark: Watermark) {
        currentWatermark = watermark
        pendingWatermark = watermark
        attemptApplyWatermark()
    }
}
