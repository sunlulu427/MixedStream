package com.astra.avpush.domain.config

import android.view.Surface
import com.astra.avpush.domain.callback.IRenderer
import com.astra.avpush.presentation.widget.GLSurfaceView
import javax.microedition.khronos.egl.EGLContext

class RendererConfiguration(
    val renderer: IRenderer = object : IRenderer {},
    val rendererMode: Int = GLSurfaceView.Companion.RENDERERMODE_CONTINUOUSLY,
    val surface: Surface? = null,
    val eglContext: EGLContext? = null,
    val width: Int = 720,
    val height: Int = 1280
)
