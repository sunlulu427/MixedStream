package com.astra.avpush.infrastructure.camera

import android.opengl.EGL14
import android.view.Surface
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface


class EglHelper {

    private var egl: EGL10? = null
    private var display: EGLDisplay? = null
    private var context: EGLContext? = null
    private var surface: EGLSurface? = null

    val eglContext: EGLContext?
        get() = context

    fun create(surface: Surface?, sharedContext: EGLContext? = null) {
        require(surface != null) { "Surface must not be null" }
        release()
        val egl = (EGLContext.getEGL() as EGL10).also { this.egl = it }
        val eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY).also { display = it }
        check(eglDisplay !== EGL10.EGL_NO_DISPLAY) { "Unable to acquire EGL display" }
        val version = IntArray(2)
        check(egl.eglInitialize(eglDisplay, version)) { "Unable to initialize EGL" }
        val attributes = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, 4,
            EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
            EGL10.EGL_NONE
        )
        val numConfig = IntArray(1)
        check(egl.eglChooseConfig(eglDisplay, attributes, null, 1, numConfig)) { "Failed to choose EGLConfig" }
        check(numConfig[0] > 0) { "No EGLConfig available for attributes" }
        val configs = arrayOfNulls<EGLConfig>(numConfig[0])
        check(egl.eglChooseConfig(eglDisplay, attributes, configs, configs.size, numConfig)) { "Failed to obtain EGLConfig" }
        val resolvedConfig = configs.firstOrNull { it != null }
        check(resolvedConfig != null) { "EGLConfig not resolved" }
        val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE)
        context = egl.eglCreateContext(eglDisplay, resolvedConfig, sharedContext ?: EGL10.EGL_NO_CONTEXT, contextAttrs)
        check(context != null && context !== EGL10.EGL_NO_CONTEXT) { "Failed to create EGLContext" }
        this.surface = egl.eglCreateWindowSurface(eglDisplay, resolvedConfig, surface, null)
        check(this.surface != null && this.surface !== EGL10.EGL_NO_SURFACE) { "Failed to create EGLSurface" }
        makeCurrent()
    }

    fun makeCurrent() {
        val egl = egl ?: return
        val eglDisplay = display ?: return
        val eglSurface = surface ?: return
        val eglContext = context ?: return
        check(egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent failed" }
    }

    fun swapBuffers(): Boolean {
        val egl = egl ?: return false
        val eglDisplay = display ?: return false
        val eglSurface = surface ?: return false
        return egl.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        val egl = egl ?: return
        val eglDisplay = display
        val eglSurface = surface
        val eglContext = context
        if (eglDisplay != null && eglDisplay !== EGL10.EGL_NO_DISPLAY) {
            egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
            if (eglSurface != null && eglSurface !== EGL10.EGL_NO_SURFACE) {
                egl.eglDestroySurface(eglDisplay, eglSurface)
            }
            if (eglContext != null && eglContext !== EGL10.EGL_NO_CONTEXT) {
                egl.eglDestroyContext(eglDisplay, eglContext)
            }
            egl.eglTerminate(eglDisplay)
        }
        surface = null
        context = null
        display = null
        this.egl = null
    }
}
