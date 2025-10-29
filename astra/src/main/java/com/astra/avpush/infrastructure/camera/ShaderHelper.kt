package com.astra.avpush.infrastructure.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES20
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.astra.avpush.runtime.LogHelper
import com.astra.avpush.runtime.NativeShaders
import java.nio.ByteBuffer

object ShaderHelper {

    enum class Script(val id: Int) {
        BASIC_VERTEX(0),
        BASIC_FRAGMENT(1),
        CAMERA_VERTEX(2),
        CAMERA_FRAGMENT(3)
    }

    private val tag = ShaderHelper::class.java.simpleName

    fun getScript(script: Script): String = NativeShaders.script(script.id)

    @Synchronized
    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader == 0) return -1
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compile = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compile, 0)
        if (compile[0] != GLES20.GL_TRUE) {
            LogHelper.e(tag, "shader compile error")
            GLES20.glDeleteShader(shader)
            shader = -1
        }
        return shader
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == -1 || fragmentShader == -1) return -1
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }


    fun createTextImage(text: String, textSize: Int, textColor: String, bgColor: String, padding: Int): Bitmap {
        val paint = Paint()
        paint.color = textColor.toColorInt()
        paint.textSize = textSize.toFloat()
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true

        val width = paint.measureText(text, 0, text.length)

        val top = paint.fontMetrics.top
        val bottom = paint.fontMetrics.bottom

        val bm = createBitmap(
            width = (width + padding * 2).toInt(),
            height = (bottom - top + padding * 2).toInt()
        )
        val canvas = Canvas(bm)
        canvas.drawColor(bgColor.toColorInt())
        canvas.drawText(text, padding.toFloat(), -top + padding, paint)
        return bm
    }

    fun loadBitmapTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val bitmapBuffer = ByteBuffer.allocate(bitmap.height * bitmap.width * 4)
        bitmap.copyPixelsToBuffer(bitmapBuffer)
        bitmapBuffer.flip()

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap.width,
            bitmap.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer
        )
        return textureIds[0]
    }
}
