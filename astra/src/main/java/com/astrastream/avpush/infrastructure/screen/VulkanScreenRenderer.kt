package com.astrastream.avpush.infrastructure.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.astrastream.avpush.domain.config.ScreenCaptureConfiguration
import com.astrastream.avpush.runtime.LogHelper
import java.util.concurrent.CountDownLatch

internal class VulkanScreenRenderer(
    private val context: Context,
    private var configuration: ScreenCaptureConfiguration,
    private val targetSurface: Surface
) : ImageReader.OnImageAvailableListener {

    private val tag = "VulkanScreenRenderer"
    private val legacyMode = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private var projection: MediaProjection? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var renderer: android.graphics.HardwareRenderer? = null
    private var rootNode: RenderNode? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var started = false
    @Volatile
    private var paused = false

    fun start() {
        if (started) return
        started = true
        if (legacyMode) {
            recreateVirtualDisplay()
            return
        }
        handlerThread = HandlerThread("Astra-ScreenCapture").also { it.start() }
        handler = Handler(handlerThread!!.looper)
        renderer = android.graphics.HardwareRenderer().also { hardwareRenderer ->
            hardwareRenderer.setSurface(targetSurface)
            hardwareRenderer.start()
            val renderNode = RenderNode("screen-root")
            rootNode = renderNode
            hardwareRenderer.setContentRoot(renderNode)
        }
        scheduleSurfaceCreation()
    }

    fun stop() {
        started = false
        val thread = handlerThread
        val handler = handler
        if (thread != null && handler != null) {
            val latch = CountDownLatch(1)
            handler.post {
                releaseVirtualDisplay()
                latch.countDown()
            }
            try {
                latch.await()
            } catch (_: InterruptedException) {
            }
            thread.quitSafely()
        } else {
            releaseVirtualDisplay()
        }
        handlerThread = null
        this.handler = null
        renderer?.apply {
            stop()
            destroy()
        }
        renderer = null
        rootNode = null
    }

    fun updateConfiguration(configuration: ScreenCaptureConfiguration) {
        this.configuration = configuration
        if (started) {
            if (legacyMode) {
                recreateVirtualDisplay()
            } else {
                handler?.post { recreateVirtualDisplay() }
            }
        }
    }

    fun updateProjection(projection: MediaProjection?) {
        this.projection = projection
        if (started) {
            if (legacyMode) {
                recreateVirtualDisplay()
            } else {
                handler?.post { recreateVirtualDisplay() }
            }
        }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    private fun scheduleSurfaceCreation() {
        if (legacyMode) {
            recreateVirtualDisplay()
        } else {
            handler?.post { recreateVirtualDisplay() }
        }
    }

    private fun recreateVirtualDisplay() {
        releaseVirtualDisplay()
        val projection = projection ?: run {
            LogHelper.w(tag, "projection missing; waiting")
            return
        }
        val config = configuration
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
        val density = config.densityDpi.takeIf { it > 0 } ?: context.resources.displayMetrics.densityDpi
        if (legacyMode) {
            imageReader = null
            virtualDisplay = projection.createVirtualDisplay(
                "Astra-Screen",
                config.width,
                config.height,
                density,
                flags,
                targetSurface,
                null,
                null
            )
            LogHelper.d(tag) { "legacy virtual display created ${config.width}x${config.height}@${config.fps}" }
            return
        }
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        val reader = ImageReader.newInstance(
            config.width,
            config.height,
            android.graphics.PixelFormat.RGBA_8888,
            /*maxImages*/ 4,
            usage.toLong()
        )
        reader.setOnImageAvailableListener(this, handler)
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "Astra-Screen",
            config.width,
            config.height,
            density,
            flags,
            reader.surface,
            null,
            handler
        )
        LogHelper.d(tag) { "virtual display created ${config.width}x${config.height}@${config.fps}" }
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
    }

    override fun onImageAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        if (paused) {
            image.close()
            return
        }
        val buffer = image.hardwareBuffer
        if (buffer == null) {
            image.close()
            return
        }
        try {
            renderBuffer(buffer, image.timestamp)
        } catch (error: Throwable) {
            LogHelper.e(tag, error, "failed to render frame")
        } finally {
            image.close()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                buffer.close()
            }
        }
    }

    private fun renderBuffer(buffer: HardwareBuffer, timestamp: Long) {
        val renderer = renderer ?: return
        val root = rootNode ?: return
        val targetColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        val bitmap = Bitmap.wrapHardwareBuffer(buffer, targetColorSpace)
        if (bitmap != null) {
            val width = configuration.width
            val height = configuration.height
            val canvas = root.beginRecording(width, height)
            try {
                canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), paint)
            } finally {
                root.endRecording()
                bitmap.recycle()
            }
            renderer.createRenderRequest()
                .setVsyncTime(timestamp)
                .setWaitForPresent(false)
                .syncAndDraw()
        }
    }
}
