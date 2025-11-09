package com.astra.avpush.domain

import android.view.Surface
import com.astra.avpush.presentation.widget.GLSurfaceView
import com.astra.avpush.presentation.widget.GlRenderer
import javax.microedition.khronos.egl.EGLContext

class RendererConfiguration(
    val renderer: GlRenderer = object : GlRenderer {},
    val rendererMode: Int = GLSurfaceView.Companion.RENDERERMODE_CONTINUOUSLY,
    val surface: Surface? = null,
    val eglContext: EGLContext? = null,
    val width: Int = 720,
    val height: Int = 1280
)
