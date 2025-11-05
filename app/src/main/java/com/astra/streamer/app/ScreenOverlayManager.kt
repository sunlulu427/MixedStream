package com.astra.streamer.app

import android.R
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.astra.streamer.ui.screen.ScreenLiveUiState
import java.lang.ref.WeakReference

object ScreenOverlayManager {

    private val handler = Handler(Looper.getMainLooper())
    private var overlayViewRef: WeakReference<View>? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var touchSlop: Int = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var dragging = false

    fun show(context: Context, state: ScreenLiveUiState) {
        if (!Settings.canDrawOverlays(context)) return
        handler.post {
            if (overlayViewRef?.get() == null) {
                createOverlay(context.applicationContext)
            }
            updateInternal(state)
        }
    }

    fun update(context: Context, state: ScreenLiveUiState) {
        if (!Settings.canDrawOverlays(context)) return
        handler.post { updateInternal(state) }
    }

    fun hide(@Suppress("UNUSED_PARAMETER") context: Context) {
        handler.post {
            overlayViewRef?.get()?.let { view ->
                windowManager?.removeView(view)
            }
            overlayViewRef = null
            windowManager = null
            layoutParams = null
        }
    }

    private fun createOverlay(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val padding = (context.resources.displayMetrics.density * 12).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val background = GradientDrawable().apply {
                cornerRadius = context.resources.displayMetrics.density * 12
                setColor(Color.parseColor("#CC1F1F1F"))
            }
            setPadding(padding, padding, padding, padding)
            this.background = background
        }

        val title = TextView(context).apply {
            text = "Screen Live"
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        val status = TextView(context).apply {
            id = R.id.text1
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        container.addView(title)
        container.addView(status)
        container.setOnClickListener {
            val intent = Intent(context, LiveActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ContextCompat.startActivity(context, intent, null)
        }

        touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (context.resources.displayMetrics.density * 12).toInt()
            y = (context.resources.displayMetrics.density * 80).toInt()
        }

        windowManager = wm
        overlayViewRef = WeakReference(container)
        layoutParams = params
        container.setOnTouchListener { view, event ->
            handleDrag(view, event)
            true
        }
        wm.addView(container, params)
    }

    private fun updateInternal(state: ScreenLiveUiState) {
        val view = overlayViewRef?.get() ?: return
        val status = view.findViewById<TextView>(R.id.text1) ?: return
        val builder = StringBuilder()
        builder.append("状态: ")
        builder.append(if (state.isStreaming) "直播中" else "空闲")
        builder.append('\n')
        builder.append("码率: ")
        builder.append(state.currentBitrate)
        builder.append(" kbps\n帧率: ")
        builder.append(state.currentFps)
        status.text = builder.toString()
    }

    private fun handleDrag(view: View, event: MotionEvent) {
        val wm = windowManager ?: return
        val params = layoutParams ?: return
        val metrics = view.context.resources.displayMetrics
        val viewWidth = view.width.takeIf { it > 0 } ?: view.measuredWidth
        val viewHeight = view.height.takeIf { it > 0 } ?: view.measuredHeight
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialX = params.x
                initialY = params.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaRawX = initialTouchX - event.rawX
                val deltaRawY = event.rawY - initialTouchY
                if (!dragging) {
                    val distanceSquared = deltaRawX * deltaRawX + deltaRawY * deltaRawY
                    if (distanceSquared >= (touchSlop * touchSlop).toFloat()) {
                        dragging = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (dragging) {
                    params.x = (initialX + deltaRawX.toInt()).coerceIn(0, maxX)
                    params.y = (initialY + deltaRawY.toInt()).coerceIn(0, maxY)
                    wm.updateViewLayout(view, params)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    view.performClick()
                }
                dragging = false
            }
        }
    }
}
