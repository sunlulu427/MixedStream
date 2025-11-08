package com.astra.avpush.presentation.widget

import android.view.Surface
import javax.microedition.khronos.egl.EGLContext

interface GlRenderer {
    fun onSurfaceCreate(width: Int, height: Int) {}
    fun onSurfaceChange(width: Int, height: Int) {}
    fun onDraw() {}
}

interface GlThreadConfig {
    fun renderer(): GlRenderer?
    fun surface(): Surface?
    fun eglContext(): EGLContext?
    fun rendererMode(): Int
}
