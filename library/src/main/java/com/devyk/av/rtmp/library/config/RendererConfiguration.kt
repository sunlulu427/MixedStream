package com.devyk.av.rtmp.library.config

import android.view.Surface
import com.devyk.av.rtmp.library.callback.IRenderer
import com.devyk.av.rtmp.library.widget.GLSurfaceView
import javax.microedition.khronos.egl.EGLContext

/**
 * <pre>
 *     author  : devyk on 2020-07-06 11:45
 *     blog    : https://juejin.im/user/578259398ac2470061f3a3fb/posts
 *     github  : https://github.com/yangkun19921001
 *     mailbox : yang1001yk@gmail.com
 *     desc    : This is RendererConfiguration
 * </pre>
 */
class RendererConfiguration(
    val renderer: IRenderer = object : IRenderer{},
    val rendererMode: Int = GLSurfaceView.RENDERERMODE_CONTINUOUSLY,
    val surface: Surface? = null,
    val eglContext: EGLContext? = null,
    val width: Int = 720,
    val height: Int = 1280
)