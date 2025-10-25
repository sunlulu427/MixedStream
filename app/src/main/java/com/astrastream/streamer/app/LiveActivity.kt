package com.astrastream.streamer.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.astrastream.avpush.domain.callback.OnConnectListener
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.runtime.LogHelper
import com.astrastream.streamer.R
import com.astrastream.streamer.core.util.SPUtils
import com.astrastream.streamer.core.util.Utils
import com.astrastream.streamer.data.LivePreferencesStore
import com.astrastream.streamer.ui.live.LiveScreen
import com.astrastream.streamer.ui.live.LiveSessionCoordinator
import com.astrastream.streamer.ui.live.LiveUiState
import com.astrastream.streamer.ui.screen.ScreenLiveScreen
import com.astrastream.streamer.ui.screen.ScreenLiveSessionCoordinator
import com.astrastream.streamer.ui.screen.ScreenLiveUiState
import com.astrastream.streamer.ui.theme.AVLiveTheme
import com.tbruyelle.rxpermissions2.RxPermissions
import androidx.lifecycle.lifecycleScope

class LiveActivity : AppCompatActivity(), OnConnectListener {

    private enum class LiveMode { CAMERA, SCREEN }

    private val captureDefaults = LiveSessionCoordinator.defaultCaptureOptions()
    private val streamDefaults = LiveSessionCoordinator.defaultStreamOptions()
    private val encoderDefaults = LiveSessionCoordinator.defaultEncoderOptions()
    private val preferences by lazy { LivePreferencesStore(this) }

    private val uiState: MutableState<LiveUiState> by lazy {
        val defaultState = LiveUiState(
            streamUrl = "",
            captureResolution = captureDefaults.first(),
            streamResolution = streamDefaults.getOrElse(1) { streamDefaults.first() },
            encoder = encoderDefaults.first(),
            targetBitrate = 800,
            showParameterPanel = true
        )
        mutableStateOf(preferences.load(defaultState, captureDefaults, streamDefaults, encoderDefaults))
    }

    private val screenState: MutableState<ScreenLiveUiState> = mutableStateOf(
        ScreenLiveUiState(streamUrl = "")
    )

    private val audioConfig = AudioConfiguration()
    private lateinit var coordinator: LiveSessionCoordinator
    private lateinit var screenCoordinator: ScreenLiveSessionCoordinator
    private lateinit var projectionManager: MediaProjectionManager

    private lateinit var projectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    private var currentMode: LiveMode = LiveMode.CAMERA

    private val screenConnectListener = object : OnConnectListener {
        override fun onConnecting() {}

        override fun onConnected() {
            val fps = screenCoordinator.configuredFps()
            screenCoordinator.confirmConnected(screenState.value.targetBitrate, fps)
            ScreenOverlayManager.update(applicationContext, screenState.value)
        }

        override fun onFail(message: String) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            screenCoordinator.markConnectionFailed()
            ScreenOverlayManager.hide(applicationContext)
        }

        override fun onClose() {
            screenCoordinator.stopStreaming()
            ScreenOverlayManager.hide(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeEnvironment()
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCoordinator = ScreenLiveSessionCoordinator(this, screenState)
        screenCoordinator.updateStreamUrl(uiState.value.streamUrl)
        screenCoordinator.setOverlayObserver { ScreenOverlayManager.update(applicationContext, it) }
        projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val projection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
                if (projection != null) {
                    screenCoordinator.attachProjection(projection)
                }
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_LONG).show()
                screenCoordinator.markConnectionFailed()
            }
        }
        val contentView = createContentView()
        setContentView(contentView)
        updateOverlayState()
        ensurePreview()
        requestRuntimePermissions()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        coordinator.liveView?.previewAngle(this)
    }

    override fun onResume() {
        super.onResume()
        updateOverlayState()
        if (currentMode == LiveMode.CAMERA) {
            ensurePreview()
        }
        ScreenOverlayManager.hide(applicationContext)
    }

    override fun onStart() {
        super.onStart()
        ScreenOverlayManager.hide(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        if (screenState.value.isStreaming && screenState.value.overlayPermissionGranted) {
            ScreenOverlayManager.show(applicationContext, screenState.value)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coordinator.sender?.close()
        coordinator.liveView?.stopLive()
        coordinator.liveView?.releaseCamera()
        screenCoordinator.release()
        ScreenOverlayManager.hide(applicationContext)
    }

    private fun initializeEnvironment() {
        Utils.init(application)
        LogHelper.initialize(applicationContext)
        LogHelper.enable(true)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun createContentView(): View = ComposeView(this).apply {
        coordinator = LiveSessionCoordinator(this@LiveActivity, uiState, audioConfig, preferences)
        setContent {
            AVLiveTheme {
                var selectedMode by rememberSaveable { mutableStateOf(currentMode) }
                val modeSwitcherContent: @Composable () -> Unit = {
                    ModeSwitcher(
                        selected = selectedMode,
                        onSelect = { mode ->
                            if (mode != selectedMode) {
                                selectedMode = mode
                                handleModeSwitch(mode)
                            }
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedMode) {
                        LiveMode.CAMERA -> LiveScreen(
                            state = uiState.value,
                            captureOptions = coordinator.captureOptions,
                            streamOptions = coordinator.streamOptions,
                            encoderOptions = coordinator.encoderOptions,
                            onCaptureResolutionSelected = { coordinator.updateCapture(it) },
                            onStreamResolutionSelected = { coordinator.updateStream(it) },
                            onEncoderSelected = { coordinator.updateEncoder(it) },
                            onBitrateChanged = { coordinator.updateBitrate(it) },
                            onBitrateInput = { coordinator.updateBitrateFromInput(it) },
                            onStreamUrlChanged = { updateSharedStreamUrl(it) },
                            onTogglePanel = { coordinator.togglePanel() },
                            onShowUrlDialog = { coordinator.showUrlDialog() },
                            onDismissUrlDialog = { coordinator.hideUrlDialog() },
                            onConfirmUrl = { url ->
                                val sanitized = url.trim()
                                coordinator.confirmUrl(sanitized) {
                                    screenCoordinator.updateStreamUrl(sanitized)
                                    handleCameraToggle()
                                }
                            },
                            onStatsToggle = { coordinator.setStatsVisible(it) },
                            onSwitchCamera = { coordinator.liveView?.switchCamera() },
                            onToggleLive = { handleCameraToggle() },
                            onLiveViewReady = { coordinator.attachLiveView(it) },
                            modeSwitcher = modeSwitcherContent,
                            onBack = { finish() }
                        )
                        LiveMode.SCREEN -> ScreenLiveScreen(
                            state = screenState.value,
                            onStreamUrlChanged = { updateSharedStreamUrl(it) },
                            onBitrateChanged = { screenCoordinator.updateBitrate(it) },
                            onToggleMic = { screenCoordinator.toggleMic(it) },
                            onTogglePlayback = { screenCoordinator.togglePlayback(it) },
                            onToggleStats = { screenCoordinator.toggleStats(it) },
                            onRequestOverlay = { requestOverlayPermission() },
                            onStart = { handleScreenStart() },
                            onStop = { handleScreenStop() },
                            modeSwitcher = modeSwitcherContent,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun requestRuntimePermissions() {
        val key = getString(R.string.OPEN_PERMISSIONS)
        val permissionsGranted = SPUtils.getInstance().getBoolean(key) && hasCameraPermission()
        if (permissionsGranted) {
            return
        }
        RxPermissions(this)
            .request(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .subscribe { allGranted ->
                if (allGranted) {
                    SPUtils.getInstance().put(key, true)
                    Toast.makeText(this, getString(R.string.GET_PERMISSION_ERROR), Toast.LENGTH_SHORT).show()
                } else {
                    SPUtils.getInstance().put(key, false)
                }
                handlePermissionsUpdated(allGranted)
            }
    }

    private fun handlePermissionsUpdated(allGranted: Boolean) {
        if (allGranted) {
            ensurePreview()
        } else {
            coordinator.markPreviewPending()
        }
    }

    private fun handleCameraToggle() {
        if (uiState.value.isStreaming || uiState.value.isConnecting) {
            stopCameraStreaming()
        } else {
            startCameraStreaming()
        }
    }

    private fun startCameraStreaming() {
        if (uiState.value.streamUrl.isBlank()) {
            coordinator.showUrlDialog()
            return
        }
        if (!coordinator.isPreviewReady()) {
            Toast.makeText(this, "摄像头预览初始化中，请稍候", Toast.LENGTH_SHORT).show()
            coordinator.startPreview()
            return
        }
        coordinator.markConnecting()
        if (!ensureCameraSender()) {
            coordinator.clearConnecting()
            return
        }
        if (coordinator.useUnifiedApi) {
            // 使用统一API启动推流
            startUnifiedStreaming()
        } else {
            // 使用传统RTMP推流
            coordinator.sender?.setDataSource(uiState.value.streamUrl)
            coordinator.sender?.connect()
        }
    }

    private fun startUnifiedStreaming() {
        // TODO: 实现统一API的推流启动逻辑
        // 这里需要与UnifiedStreamSession集成
        coordinator.unifiedSession?.let { session ->
            // 暂时标记为连接成功，实际实现需要异步处理
            coordinator.markStreamingStarted(uiState.value.targetBitrate, uiState.value.videoFps)
            Toast.makeText(this, "统一API推流已启动 (${uiState.value.streamUrl})", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCameraStreaming() {
        if (coordinator.useUnifiedApi) {
            // 停止统一API推流
            coordinator.unifiedSession?.let { session ->
                // TODO: 实现统一API的停止逻辑
                coordinator.markStreamingStopped(uiState.value.videoFps)
            }
        } else {
            // 停止传统RTMP推流
            coordinator.liveView?.stopLive()
            coordinator.sender?.close()
            coordinator.markStreamingStopped(uiState.value.videoFps)
        }
    }

    private fun ensureCameraSender(): Boolean {
        if (!coordinator.ensureSender { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }) return false

        if (coordinator.useUnifiedApi) {
            // 统一API不需要设置连接监听器，有自己的状态管理
            return coordinator.unifiedSession != null
        } else {
            // 传统RTMP需要设置连接监听器
            coordinator.sender?.setOnConnectListener(this)
            return coordinator.sender != null
        }
    }

    private fun handleScreenStart() {
        if (!screenCoordinator.ensureSender { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }) {
            screenCoordinator.markConnectionFailed()
            return
        }
        screenCoordinator.attachConnectListener(screenConnectListener)
        val started = screenCoordinator.startStreaming { requestProjectionPermission() }
        if (!started) {
            screenCoordinator.detachConnectListener()
            return
        }
        screenCoordinator.sender?.apply {
            setDataSource(screenState.value.streamUrl)
            connect()
        }
    }

    private fun handleScreenStop() {
        screenCoordinator.stopStreaming()
        screenCoordinator.detachConnectListener()
        ScreenOverlayManager.hide(applicationContext)
    }

    private fun requestProjectionPermission() {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay already enabled", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun stopWithError(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            coordinator.clearConnecting()
            coordinator.markStreamingStopped(uiState.value.videoFps)
        }
    }

    override fun onFail(message: String) = stopWithError(message)

    override fun onConnecting() = coordinator.markConnecting()

    override fun onConnected() {
        coordinator.liveView?.startLive()
        coordinator.liveView?.setVideoBps(uiState.value.targetBitrate)
        coordinator.markStreamingStarted(uiState.value.targetBitrate, uiState.value.videoFps)
    }

    override fun onClose() {
        coordinator.markStreamingStopped(uiState.value.videoFps)
    }

    private fun ensurePreview() {
        if (hasCameraPermission()) {
            coordinator.startPreview()
        } else {
            coordinator.markPreviewPending()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun updateSharedStreamUrl(url: String) {
        coordinator.updateStreamUrl(url)
        screenCoordinator.updateStreamUrl(url)
    }

    private fun updateOverlayState() {
        val granted = Settings.canDrawOverlays(this)
        screenState.value = screenState.value.copy(overlayPermissionGranted = granted)
        ScreenOverlayManager.update(applicationContext, screenState.value)
    }

    private fun handleModeSwitch(mode: LiveMode) {
        if (currentMode == mode) return
        currentMode = mode
        if (mode == LiveMode.CAMERA) {
            ensurePreview()
        } else {
            coordinator.liveView?.pause()
        }
    }

    @Composable
    private fun ModeSwitcher(selected: LiveMode, onSelect: (LiveMode) -> Unit) {
        val tabs = listOf(
            ModeTab(label = "视频", mode = LiveMode.CAMERA, enabled = true),
            ModeTab(label = "手游", mode = LiveMode.SCREEN, enabled = true)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.mode == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable(enabled = tab.enabled && tab.mode != null) {
                            tab.mode?.let(onSelect)
                        }
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            isSelected -> Color.White
                            tab.enabled -> Color.White.copy(alpha = 0.6f)
                            else -> Color.White.copy(alpha = 0.35f)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .width(32.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    )
                }
            }
        }
    }

    private data class ModeTab(val label: String, val mode: LiveMode?, val enabled: Boolean)
}
