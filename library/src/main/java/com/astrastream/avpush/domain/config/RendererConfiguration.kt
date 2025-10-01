package com.astrastream.avpush.domain.config

import android.view.Surface
import com.astrastream.avpush.domain.callback.IRenderer
import com.astrastream.avpush.presentation.widget.GLSurfaceView
import javax.microedition.khronos.egl.EGLContext

class RendererConfiguration(
    val renderer: IRenderer = object : IRenderer{},
    val rendererMode: Int = GLSurfaceView.RENDERERMODE_CONTINUOUSLY,
    val surface: Surface? = null,
    val eglContext: EGLContext? = null,
    val width: Int = 720,
    val height: Int = 1280
)
