package com.devyk.av.rtmppush

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.devyk.av.rtmp.library.callback.OnConnectListener
import com.devyk.av.rtmp.library.camera.Watermark
import com.devyk.av.rtmp.library.config.AudioConfiguration
import com.devyk.av.rtmp.library.config.CameraConfiguration
import com.devyk.av.rtmp.library.config.VideoConfiguration
import com.devyk.av.rtmp.library.controller.LiveStreamSession
import com.devyk.av.rtmp.library.stream.packer.rtmp.RtmpPacker
import com.devyk.av.rtmp.library.stream.sender.rtmp.RtmpSender
import com.devyk.av.rtmp.library.utils.LogHelper
import com.devyk.av.rtmp.library.widget.AVLiveView
import com.devyk.av.rtmppush.base.BaseActivity
import com.devyk.av.rtmppush.ui.theme.AVLiveTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor

class LiveActivity : BaseActivity<View>(), OnConnectListener {

    private val captureOptions = listOf(
        ResolutionOption(width = 720, height = 1280, label = "720 × 1280"),
        ResolutionOption(width = 960, height = 1920, label = "960 × 1920"),
        ResolutionOption(width = 1080, height = 1920, label = "1080 × 1920")
    )

    private val streamOptions = listOf(
        ResolutionOption(width = 540, height = 960, label = "540 × 960"),
        ResolutionOption(width = 720, height = 1280, label = "720 × 1280"),
        ResolutionOption(width = 960, height = 1920, label = "960 × 1920")
    )

    private val encoderOptions = listOf(
        EncoderOption(label = "硬件编解码 (MediaCodec)", useHardware = true),
        EncoderOption(label = "软件编解码", useHardware = false)
    )

    private val uiState: MutableState<LiveUiState> = mutableStateOf(
        LiveUiState(
            streamUrl = "",
            captureResolution = captureOptions[1],
            streamResolution = streamOptions[2],
            encoder = encoderOptions.first(),
            targetBitrate = 800,
            showParameterPanel = true,
            showStats = true
        )
    )

    private val audioConfiguration = AudioConfiguration()
    private val watermarkLabel = "Mato"
    private var packer: RtmpPacker? = null
    private var sender: RtmpSender? = null
    private var liveView: AVLiveView? = null
    private var previewStarted = false
    private var previewRequested = false
    private val statsListener = object : LiveStreamSession.StatsListener {
        override fun onVideoStats(bitrateKbps: Int, fps: Int) {
            runOnUiThread {
                val current = uiState.value
                if (current.currentBitrate != bitrateKbps || current.currentFps != fps) {
                    uiState.value = current.copy(currentBitrate = bitrateKbps, currentFps = fps)
                }
            }
        }
    }

    override fun initListener() {
        // No-op; listeners are attached when needed.
    }

    override fun initData() {
        // Watermark will be applied once GL is ready
    }

    override fun init() {
        packer = RtmpPacker().also { currentPacker ->
            liveView?.setPacker(currentPacker)
        }
        applyStreamConfiguration()
        liveView?.setStatsListener(statsListener)
        tryStartPreview()
    }


    override fun onContentViewBefore() {
        super.onContentViewBefore()
        Utils.init(application)
        LogHelper.isShowLog = true
        checkPermission()
        setNotTitleBar()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun getLayoutId(): View = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AVLiveTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LiveActivityScreen(
                        state = uiState.value,
                        captureOptions = captureOptions,
                        streamOptions = streamOptions,
                        encoderOptions = encoderOptions,
                        onCaptureResolutionSelected = { updateCaptureResolution(it) },
                        onStreamResolutionSelected = { updateStreamResolution(it) },
                        onEncoderSelected = { updateEncoderOption(it) },
                        onBitrateChanged = { updateBitrate(it) },
                        onBitrateInput = { updateBitrateFromInput(it) },
                        onStreamUrlChanged = { updateStreamUrl(it) },
                        onTogglePanel = { toggleParameterPanel() },
                        onShowUrlDialog = { showUrlDialog() },
                        onDismissUrlDialog = { hideUrlDialog() },
                        onConfirmUrl = { confirmStreamUrl(it) },
                        onStatsToggle = { updateStatsVisibility(it) },
                        onSwitchCamera = { liveView?.switchCamera() },
                        onToggleLive = { handleToggleLive() },
                        onLiveViewReady = { attachLiveView(it) }
                    )
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LogHelper.e(TAG, "方向改变:${newConfig.densityDpi}")
        liveView?.previewAngle(this)
    }

    override fun onResume() {
        super.onResume()
        tryStartPreview()
    }

    override fun onPermissionsUpdated(allGranted: Boolean) {
        if (allGranted) {
            tryStartPreview()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sender?.close()
        liveView?.stopLive()
        liveView?.releaseCamera()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun tryStartPreview() {
        if (previewStarted) return
        if (!hasCameraPermission()) {
            previewRequested = true
            LogHelper.w(TAG, "camera permission not granted, skip startPreview")
            return
        }
        val view = liveView ?: run {
            previewRequested = true
            LogHelper.d(TAG, "liveView not ready, postpone preview")
            return
        }
        view.startPreview()
        previewStarted = true
        previewRequested = false
    }

    private fun restartPreview() {
        val view = liveView ?: return
        view.releaseCamera()
        previewStarted = false
        tryStartPreview()
    }

    private fun attachLiveView(view: AVLiveView) {
        if (liveView === view) return
        liveView = view
        packer?.let { view.setPacker(it) }
        sender?.let { view.setSender(it) }
        view.setAudioConfigure(audioConfiguration)
        applyStreamConfiguration(view)
        view.setStatsListener(statsListener)
        if (previewRequested || hasCameraPermission()) {
            tryStartPreview()
        }
    }

    private fun applyStreamConfiguration(targetView: AVLiveView? = liveView) {
        val currentState = uiState.value
        val minBps = max(300, (currentState.targetBitrate * 0.7f).roundToInt())
        val maxBps = max(minBps + 200, (currentState.targetBitrate * 1.3f).roundToInt())
        var updatedState = if (currentState.minBitrate != minBps || currentState.maxBitrate != maxBps)
            currentState.copy(minBitrate = minBps, maxBitrate = maxBps) else currentState
        val clampedTarget = updatedState.targetBitrate.coerceIn(updatedState.minBitrate, updatedState.maxBitrate)
        if (clampedTarget != updatedState.targetBitrate) {
            updatedState = updatedState.copy(targetBitrate = clampedTarget)
        }
        if (updatedState !== currentState) {
            uiState.value = updatedState
        }
        val view = targetView ?: return
        view.setVideoConfigure(
            VideoConfiguration(
                width = updatedState.streamResolution.width,
                height = updatedState.streamResolution.height,
                fps = updatedState.videoFps,
                maxBps = updatedState.maxBitrate,
                minBps = updatedState.minBitrate,
                ifi = updatedState.gop,
                mediaCodec = updatedState.encoder.useHardware
            )
        )
        view.setCameraConfigure(
            CameraConfiguration(
                width = updatedState.captureResolution.width,
                height = updatedState.captureResolution.height,
                fps = updatedState.videoFps
            )
        )
        if (updatedState.isStreaming) {
            view.setVideoBps(updatedState.targetBitrate)
        }
        updateWatermark(view, updatedState)
    }

    private fun updateCaptureResolution(option: ResolutionOption) {
        val current = uiState.value
        if (current.captureResolution == option) return
        uiState.value = current.copy(captureResolution = option)
        applyStreamConfiguration()
        if (!current.isStreaming && !current.isConnecting) {
            restartPreview()
        }
    }

    private fun updateStreamResolution(option: ResolutionOption) {
        val current = uiState.value
        if (current.streamResolution == option) return
        uiState.value = current.copy(streamResolution = option)
        applyStreamConfiguration()
    }

    private fun updateEncoderOption(option: EncoderOption) {
        val current = uiState.value
        if (current.encoder == option) return
        uiState.value = current.copy(encoder = option)
        applyStreamConfiguration()
    }

    private fun updateBitrate(bitrate: Int) {
        val current = uiState.value
        val clamped = bitrate.coerceIn(current.minBitrate, current.maxBitrate)
        uiState.value = current.copy(targetBitrate = clamped)
        applyStreamConfiguration()
        if (current.isStreaming) {
            liveView?.setVideoBps(clamped)
        }
    }

    private fun updateBitrateFromInput(text: String) {
        val current = uiState.value
        val digits = text.filter { it.isDigit() }
        val parsed = digits.toIntOrNull() ?: current.minBitrate
        updateBitrate(parsed)
    }

    private fun handleToggleLive() {
        val current = uiState.value
        if (current.isStreaming || current.isConnecting) {
            stopStreaming()
        } else {
            startStreamingIfPossible()
        }
    }

    private fun updateStreamUrl(url: String) {
        uiState.value = uiState.value.copy(streamUrl = url)
    }

    private fun toggleParameterPanel() {
        val current = uiState.value
        uiState.value = current.copy(showParameterPanel = !current.showParameterPanel)
    }

    private fun updateStatsVisibility(show: Boolean) {
        val current = uiState.value
        if (current.showStats != show) {
            uiState.value = current.copy(showStats = show)
        }
    }

    private fun showUrlDialog() {
        uiState.value = uiState.value.copy(showUrlDialog = true)
    }

    private fun hideUrlDialog() {
        uiState.value = uiState.value.copy(showUrlDialog = false)
    }

    private fun confirmStreamUrl(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) {
            Toast.makeText(this, "推流地址不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        uiState.value = uiState.value.copy(streamUrl = clean, showUrlDialog = false)
        startStreamingIfPossible()
    }

    private fun updateWatermark(target: AVLiveView, state: LiveUiState) {
        val base = min(state.captureResolution.width, state.captureResolution.height)
        val textSize = (base * 0.05f).roundToInt().coerceIn(24, 96)
        val watermark = Watermark(watermarkLabel, Color.WHITE, textSize, null)
        target.setWatermark(watermark)
    }

    private fun startStreamingIfPossible() {
        val cleanUrl = uiState.value.streamUrl.trim()
        if (cleanUrl.isEmpty()) {
            showUrlDialog()
            return
        }
        val updated = uiState.value.copy(streamUrl = cleanUrl, isConnecting = true)
        uiState.value = updated
        if (!ensureSenderSafely()) {
            uiState.value = updated.copy(isConnecting = false)
            return
        }
        sender?.setDataSource(cleanUrl)
        sender?.connect()
    }

    private fun stopStreaming() {
        liveView?.stopLive()
        packer?.stop()
        sender?.close()
        val current = uiState.value
        uiState.value = current.copy(isStreaming = false, isConnecting = false, currentBitrate = 0, currentFps = current.videoFps)
    }

    private fun ensureSenderSafely(): Boolean {
        if (sender != null) return true
        return try {
            val newSender = RtmpSender()
            newSender.setOnConnectListener(this)
            sender = newSender
            liveView?.setSender(newSender)
            true
        } catch (error: UnsatisfiedLinkError) {
            Toast.makeText(
                this,
                "当前设备架构不支持推流组件，请使用 ARM 真机",
                Toast.LENGTH_LONG
            ).show()
            LogHelper.e(TAG, "load native fail: ${error.message}")
            false
        } catch (t: Throwable) {
            LogHelper.e(TAG, "init sender error: ${t.message}")
            false
        }
    }

    override fun onFail(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            val current = uiState.value
            uiState.value = current.copy(isConnecting = false, isStreaming = false, currentBitrate = 0, currentFps = current.videoFps)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onConnecting() {
        runOnUiThread {
            uiState.value = uiState.value.copy(isConnecting = true)
        }
    }

    override fun onConnected() {
        packer?.start()
        liveView?.startLive()
        liveView?.setVideoBps(uiState.value.targetBitrate)
        runOnUiThread {
            val current = uiState.value
            uiState.value = current.copy(
                isConnecting = false,
                isStreaming = true,
                currentBitrate = current.targetBitrate,
                currentFps = current.videoFps
            )
        }
    }

    override fun onClose() {
        runOnUiThread {
            val current = uiState.value
            uiState.value = current.copy(isConnecting = false, isStreaming = false, currentBitrate = 0, currentFps = current.videoFps)
        }
    }

}

data class ResolutionOption(val width: Int, val height: Int, val label: String)

data class EncoderOption(val label: String, val useHardware: Boolean)

data class LiveUiState(
    val streamUrl: String,
    val isStreaming: Boolean = false,
    val isConnecting: Boolean = false,
    val showParameterPanel: Boolean = false,
    val showUrlDialog: Boolean = false,
    val showStats: Boolean = true,
    val captureResolution: ResolutionOption,
    val streamResolution: ResolutionOption,
    val encoder: EncoderOption,
    val targetBitrate: Int,
    val minBitrate: Int = 300,
    val maxBitrate: Int = 3500,
    val videoFps: Int = 30,
    val gop: Int = 5,
    val currentBitrate: Int = 0,
    val currentFps: Int = 30
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveActivityScreen(
    state: LiveUiState,
    captureOptions: List<ResolutionOption>,
    streamOptions: List<ResolutionOption>,
    encoderOptions: List<EncoderOption>,
    onCaptureResolutionSelected: (ResolutionOption) -> Unit,
    onStreamResolutionSelected: (ResolutionOption) -> Unit,
    onEncoderSelected: (EncoderOption) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onBitrateInput: (String) -> Unit,
    onStreamUrlChanged: (String) -> Unit,
    onTogglePanel: () -> Unit,
    onShowUrlDialog: () -> Unit,
    onDismissUrlDialog: () -> Unit,
    onConfirmUrl: (String) -> Unit,
    onStatsToggle: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleLive: () -> Unit,
    onLiveViewReady: (AVLiveView) -> Unit
) {
    val parameterEnabled = !state.isStreaming && !state.isConnecting
    val cameraEnabled = !state.isConnecting
    val hasUrl = state.streamUrl.isNotBlank()

    Scaffold(
        containerColor = ComposeColor.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!state.showParameterPanel) {
                val fabEnabled = !state.isConnecting
                val fabColor = when {
                    state.isStreaming -> MaterialTheme.colorScheme.error
                    hasUrl -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!fabEnabled) return@ExtendedFloatingActionButton
                        if (state.isStreaming) {
                            onToggleLive()
                        } else if (!hasUrl) {
                            onShowUrlDialog()
                        } else {
                            onToggleLive()
                        }
                    },
                    containerColor = fabColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    icon = {
                        if (state.isStreaming) {
                            Icon(imageVector = Icons.Rounded.Stop, contentDescription = "停止推流")
                        } else {
                            Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = "开始推流")
                        }
                    },
                    text = {
                        Text(
                            text = when {
                                state.isStreaming -> "结束直播"
                                state.isConnecting -> "连接中..."
                                state.streamUrl.isBlank() -> "填写推流地址"
                                else -> "开始直播"
                            }
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LivePreview(
                onLiveViewReady = onLiveViewReady,
                modifier = Modifier.fillMaxSize()
            )

            CameraSwitchButton(
                onClick = onSwitchCamera,
                enabled = cameraEnabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            )

            if (state.showStats && !state.showParameterPanel) {
                StreamingStatsOverlay(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 20.dp, start = 20.dp)
                        .zIndex(1f)
                )
            }

            PanelToggleButton(
                expanded = state.showParameterPanel,
                onToggle = onTogglePanel,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .zIndex(1f)
            )

            AnimatedVisibility(
                visible = state.isConnecting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                )
            }

            AnimatedVisibility(
                visible = state.showParameterPanel,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
            ) {
                Box(Modifier.fillMaxSize()) {
                    val overlayInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(ComposeColor.Black.copy(alpha = 0.25f))
                            .clickable(indication = null, interactionSource = overlayInteraction) { onTogglePanel() }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    ) {
                        ParameterPanel(
                            state = state,
                            captureOptions = captureOptions,
                            streamOptions = streamOptions,
                            encoderOptions = encoderOptions,
                            onCaptureResolutionSelected = onCaptureResolutionSelected,
                            onStreamResolutionSelected = onStreamResolutionSelected,
                            onEncoderSelected = onEncoderSelected,
                            onBitrateChanged = onBitrateChanged,
                            onBitrateInput = onBitrateInput,
                            onStreamUrlChanged = onStreamUrlChanged,
                            onStatsToggle = onStatsToggle,
                            controlsEnabled = parameterEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (state.showUrlDialog) {
        StreamUrlDialog(
            initialValue = state.streamUrl,
            onDismiss = onDismissUrlDialog,
            onConfirm = onConfirmUrl
        )
    }
}

@Composable
private fun LivePreview(onLiveViewReady: (AVLiveView) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            AVLiveView(context).also { onLiveViewReady(it) }
        },
        modifier = modifier
    )
}

@Composable
private fun CameraSwitchButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
    ) {
        Icon(imageVector = Icons.Outlined.Cameraswitch, contentDescription = "切换摄像头", tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PanelToggleButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = if (expanded) "收起参数面板" else "展开参数面板",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StreamingStatsOverlay(
    state: LiveUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val status = when {
                state.isConnecting -> "连接中"
                state.isStreaming -> "直播中"
                else -> "预览中"
            }
            Text(text = "状态: $status", style = MaterialTheme.typography.labelLarge)
            Text(text = "采集: ${state.captureResolution.label} @ ${state.videoFps}fps", style = MaterialTheme.typography.bodySmall)
            Text(text = "推流: ${state.streamResolution.label}", style = MaterialTheme.typography.bodySmall)
            Text(text = "当前码率: ${state.currentBitrate} kbps (目标 ${state.targetBitrate})", style = MaterialTheme.typography.bodySmall)
            Text(text = "GOP: ${state.gop}", style = MaterialTheme.typography.bodySmall)
            Text(text = "实际帧率: ${state.currentFps} fps", style = MaterialTheme.typography.bodySmall)
            Text(text = "编码: ${state.encoder.label}", style = MaterialTheme.typography.bodySmall)
            if (state.streamUrl.isNotBlank()) {
                Text(
                    text = "地址: ${state.streamUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterPanel(
    state: LiveUiState,
    captureOptions: List<ResolutionOption>,
    streamOptions: List<ResolutionOption>,
    encoderOptions: List<EncoderOption>,
    onCaptureResolutionSelected: (ResolutionOption) -> Unit,
    onStreamResolutionSelected: (ResolutionOption) -> Unit,
    onEncoderSelected: (EncoderOption) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onBitrateInput: (String) -> Unit,
    onStreamUrlChanged: (String) -> Unit,
    onStatsToggle: (Boolean) -> Unit,
    controlsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "直播参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = state.streamUrl,
                onValueChange = onStreamUrlChanged,
                label = { Text("推流地址（可选）") },
                placeholder = { Text("rtmp://host/app/stream") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !state.isStreaming && !state.isConnecting,
                modifier = Modifier.fillMaxWidth()
            )

            ResolutionDropdown(
                label = "采集分辨率",
                options = captureOptions,
                selected = state.captureResolution,
                onSelected = onCaptureResolutionSelected,
                enabled = controlsEnabled
            )

            ResolutionDropdown(
                label = "推流分辨率",
                options = streamOptions,
                selected = state.streamResolution,
                onSelected = onStreamResolutionSelected,
                enabled = controlsEnabled
            )

            EncoderSegmentedControl(
                options = encoderOptions,
                selected = state.encoder,
                onSelected = onEncoderSelected,
                enabled = controlsEnabled
            )

            Column {
                Text(text = "推流码率", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val step = 100
                    IconButton(onClick = { onBitrateChanged(state.targetBitrate - step) }, enabled = controlsEnabled) {
                        Icon(imageVector = Icons.Rounded.Remove, contentDescription = "降低码率")
                    }
                    OutlinedTextField(
                        value = state.targetBitrate.toString(),
                        onValueChange = onBitrateInput,
                        label = { Text("kbps") },
                        singleLine = true,
                        enabled = controlsEnabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onBitrateChanged(state.targetBitrate + step) }, enabled = controlsEnabled) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = "提升码率")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "显示实时信息", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.showStats, onCheckedChange = onStatsToggle)
            }
        }
    }
}

@Composable
private fun StreamUrlDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "推流地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "请输入 RTMP 推流地址", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(text = "确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolutionDropdown(
    label: String,
    options: List<ResolutionOption>,
    selected: ResolutionOption,
    onSelected: (ResolutionOption) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    enabled = enabled,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncoderSegmentedControl(
    options: List<EncoderOption>,
    selected: EncoderOption,
    onSelected: (EncoderOption) -> Unit,
    enabled: Boolean
) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                selected = option == selected,
                onClick = { if (enabled) onSelected(option) },
                enabled = enabled,
                label = { Text(option.label, maxLines = 1) }
            )
        }
    }
}
