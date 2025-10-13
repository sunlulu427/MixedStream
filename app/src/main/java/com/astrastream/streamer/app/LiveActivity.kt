package com.astrastream.streamer.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.astrastream.avpush.domain.callback.OnConnectListener
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.core.utils.LogHelper
import com.astrastream.streamer.R
import com.astrastream.streamer.core.util.SPUtils
import com.astrastream.streamer.core.util.Utils
import com.astrastream.streamer.data.LivePreferencesStore
import com.astrastream.streamer.ui.live.LiveScreen
import com.astrastream.streamer.ui.live.LiveSessionCoordinator
import com.astrastream.streamer.ui.live.LiveUiState
import com.astrastream.streamer.ui.theme.AVLiveTheme
import com.tbruyelle.rxpermissions2.RxPermissions

class LiveActivity : AppCompatActivity(), OnConnectListener {

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

    private val audioConfig = AudioConfiguration()
    private lateinit var coordinator: LiveSessionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeEnvironment()
        val contentView = createContentView()
        setContentView(contentView)
        coordinator.ensurePacker()
        ensurePreview()
        requestRuntimePermissions()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        coordinator.liveView?.previewAngle(this)
    }

    override fun onResume() {
        super.onResume()
        ensurePreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        coordinator.sender?.close()
        coordinator.liveView?.stopLive()
        coordinator.liveView?.releaseCamera()
    }

    private fun initializeEnvironment() {
        Utils.init(application)
        LogHelper.isShowLog = true
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
                LiveScreen(
                    state = uiState.value,
                    captureOptions = coordinator.captureOptions,
                    streamOptions = coordinator.streamOptions,
                    encoderOptions = coordinator.encoderOptions,
                    onCaptureResolutionSelected = { coordinator.updateCapture(it) },
                    onStreamResolutionSelected = { coordinator.updateStream(it) },
                    onEncoderSelected = { coordinator.updateEncoder(it) },
                    onBitrateChanged = { coordinator.updateBitrate(it) },
                    onBitrateInput = { coordinator.updateBitrateFromInput(it) },
                    onStreamUrlChanged = { coordinator.updateStreamUrl(it) },
                    onTogglePanel = { coordinator.togglePanel() },
                    onShowUrlDialog = { coordinator.showUrlDialog() },
                    onDismissUrlDialog = { coordinator.hideUrlDialog() },
                    onConfirmUrl = { url -> coordinator.confirmUrl(url) { handleToggleLive() } },
                    onStatsToggle = { coordinator.setStatsVisible(it) },
                    onSwitchCamera = { coordinator.liveView?.switchCamera() },
                    onToggleLive = { handleToggleLive() },
                    onLiveViewReady = { coordinator.attachLiveView(it) }
                )
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

    private fun handleToggleLive() {
        if (uiState.value.isStreaming || uiState.value.isConnecting) {
            stopStreaming()
        } else {
            startStreaming()
        }
    }

    private fun startStreaming() {
        if (uiState.value.streamUrl.isBlank()) {
            coordinator.showUrlDialog()
            return
        }
        coordinator.markConnecting()
        if (!ensureSender()) {
            coordinator.clearConnecting()
            return
        }
        coordinator.sender?.setDataSource(uiState.value.streamUrl)
        coordinator.sender?.connect()
    }

    private fun stopStreaming() {
        coordinator.liveView?.stopLive()
        coordinator.packer?.stop()
        coordinator.sender?.close()
        coordinator.markStreamingStopped(uiState.value.videoFps)
    }

    private fun ensureSender(): Boolean {
        if (!coordinator.ensureSender { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }) return false
        coordinator.sender?.setOnConnectListener(this)
        return coordinator.sender != null
    }

    private fun stopWithError(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            coordinator.clearConnecting()
            coordinator.markStreamingStopped(uiState.value.videoFps)
        }
    }

    override fun onFail(message: String) = stopWithError(message)

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onConnecting() = coordinator.markConnecting()

    override fun onConnected() {
        coordinator.packer?.start()
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
}
