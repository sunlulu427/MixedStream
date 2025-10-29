package com.astra.avpush.domain.callback

import android.opengl.GLES20

interface IRenderer {
    /**
     * 当 Surface 创建的时候
     */
    fun onSurfaceCreate(width: Int, height: Int) {}

    /**
     * 当 surface 窗口改变的时候
     */
    fun onSurfaceChange(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * 绘制的时候
     */
    fun onDraw() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0f, 1f, 0f, 1f)
    }
}
