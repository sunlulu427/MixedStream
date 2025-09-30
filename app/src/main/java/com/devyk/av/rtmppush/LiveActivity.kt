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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.devyk.av.rtmp.library.callback.OnConnectListener
import com.devyk.av.rtmp.library.camera.Watermark
import com.devyk.av.rtmp.library.config.AudioConfiguration
import com.devyk.av.rtmp.library.config.CameraConfiguration
import com.devyk.av.rtmp.library.config.VideoConfiguration
import com.devyk.av.rtmp.library.stream.packer.rtmp.RtmpPacker
import com.devyk.av.rtmp.library.stream.sender.rtmp.RtmpSender
import com.devyk.av.rtmp.library.utils.LogHelper
import com.devyk.av.rtmp.library.widget.AVLiveView
import com.devyk.av.rtmppush.base.BaseActivity
import com.devyk.av.rtmppush.ui.theme.AVLiveTheme
import kotlin.math.max
import kotlin.math.roundToInt

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
            streamUrl = DEFAULT_STREAM_URL,
            captureResolution = captureOptions[1],
            streamResolution = streamOptions[2],
            encoder = encoderOptions.first(),
            targetBitrate = 800
        )
    )

    private val audioConfiguration = AudioConfiguration()
    private var watermark: Watermark = Watermark("Mato", Color.WHITE, 20, null)
    private var packer: RtmpPacker? = null
    private var sender: RtmpSender? = null
    private var liveView: AVLiveView? = null
    private var previewStarted = false
    private var previewRequested = false

    override fun initListener() {
        // No-op; listeners are attached when needed.
    }

    override fun initData() {
        // Watermark will be applied once GL is ready
    }

    override fun init() {
        packer = RtmpPacker().also { liveView?.setPacker(it) }
        applyStreamConfiguration()
        tryStartPreview()
    }

    override fun onContentViewBefore() {
        super.onContentViewBefore()
        Utils.init(application)
        LogHelper.isShowLog = true
        checkPermission()
        setNotTitleBar()
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
                        onSwitchCamera = { liveView?.switchCamera() },
                        onToggleLive = { handleToggleLive() },
                        onDismissUrlDialog = { hideUrlDialog() },
                        onConfirmUrl = { startStreamingWithUrl(it) },
                        onShowUrlDialog = { showUrlDialog() },
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
        applyStreamConfiguration()
        view.setWatermark(watermark)
        if (previewRequested || hasCameraPermission()) {
            tryStartPreview()
        }
    }

    private fun applyStreamConfiguration() {
        val current = uiState.value
        val view = liveView ?: return
        val target = current.targetBitrate
        val minBps = max(current.bitrateRange.first, (target * 0.7f).roundToInt())
        val maxBps = max(target + 100, (target * 1.3f).roundToInt())
        view.setVideoConfigure(
            VideoConfiguration(
                width = current.streamResolution.width,
                height = current.streamResolution.height,
                fps = 30,
                maxBps = maxBps,
                minBps = minBps,
                ifi = 5,
                mediaCodec = current.encoder.useHardware
            )
        )
        view.setCameraConfigure(
            CameraConfiguration(
                width = current.captureResolution.width,
                height = current.captureResolution.height,
                fps = 30
            )
        )
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
        val clamped = bitrate.coerceIn(current.bitrateRange)
        uiState.value = current.copy(targetBitrate = clamped)
        applyStreamConfiguration()
        if (current.isStreaming) {
            liveView?.setVideoBps(clamped)
        }
    }

    private fun handleToggleLive() {
        val current = uiState.value
        if (current.isStreaming || current.isConnecting) {
            stopStreaming()
        } else {
            showUrlDialog()
        }
    }

    private fun showUrlDialog() {
        uiState.value = uiState.value.copy(showUrlDialog = true)
    }

    private fun hideUrlDialog() {
        uiState.value = uiState.value.copy(showUrlDialog = false)
    }

    private fun startStreamingWithUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            Toast.makeText(applicationContext, "推流地址不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        hideUrlDialog()
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
        uiState.value = uiState.value.copy(isStreaming = false, isConnecting = false)
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
            uiState.value = uiState.value.copy(isConnecting = false, isStreaming = false)
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
            uiState.value = uiState.value.copy(isConnecting = false, isStreaming = true)
        }
    }

    override fun onClose() {
        runOnUiThread {
            uiState.value = uiState.value.copy(isConnecting = false, isStreaming = false)
        }
    }

    companion object {
        private const val DEFAULT_STREAM_URL = "rtmp://www.devyk.cn:1992/devykLive/live1"
    }
}

data class ResolutionOption(val width: Int, val height: Int, val label: String)

data class EncoderOption(val label: String, val useHardware: Boolean)

data class LiveUiState(
    val streamUrl: String,
    val isStreaming: Boolean = false,
    val isConnecting: Boolean = false,
    val showUrlDialog: Boolean = false,
    val captureResolution: ResolutionOption,
    val streamResolution: ResolutionOption,
    val encoder: EncoderOption,
    val targetBitrate: Int,
    val bitrateRange: IntRange = 300..3500
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
    onSwitchCamera: () -> Unit,
    onToggleLive: () -> Unit,
    onShowUrlDialog: () -> Unit,
    onDismissUrlDialog: () -> Unit,
    onConfirmUrl: (String) -> Unit,
    onLiveViewReady: (AVLiveView) -> Unit
) {
    val parameterEnabled = !state.isStreaming && !state.isConnecting
    val cameraEnabled = !state.isConnecting
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Live Studio", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSwitchCamera, enabled = cameraEnabled) {
                        Icon(imageVector = Icons.Outlined.Cameraswitch, contentDescription = "切换摄像头")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            val fabEnabled = !state.isConnecting
            val fabContainer = when {
                state.isStreaming -> MaterialTheme.colorScheme.error
                fabEnabled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            }
            ExtendedFloatingActionButton(
                onClick = {
                    if (!fabEnabled) return@ExtendedFloatingActionButton
                    if (state.isStreaming) onToggleLive() else onShowUrlDialog()
                },
                containerColor = fabContainer,
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
                    Text(text = when {
                        state.isStreaming -> "结束直播"
                        state.isConnecting -> "连接中..."
                        else -> "开始直播"
                    })
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            LivePreview(
                onLiveViewReady = onLiveViewReady,
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = state.isConnecting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .fillMaxWidth(0.5f)
                )
            }

            ParameterPanel(
                state = state,
                captureOptions = captureOptions,
                streamOptions = streamOptions,
                encoderOptions = encoderOptions,
                onCaptureResolutionSelected = onCaptureResolutionSelected,
                onStreamResolutionSelected = onStreamResolutionSelected,
                onEncoderSelected = onEncoderSelected,
                onBitrateChanged = onBitrateChanged,
                controlsEnabled = parameterEnabled,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "推流码率", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "${state.targetBitrate} kbps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = state.targetBitrate.toFloat(),
                    onValueChange = { onBitrateChanged(it.roundToInt()) },
                    valueRange = state.bitrateRange.first.toFloat()..state.bitrateRange.last.toFloat(),
                    steps = ((state.bitrateRange.last - state.bitrateRange.first) / 100).coerceAtLeast(1) - 1,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
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
                Text(text = "请输入推流 URL：", style = MaterialTheme.typography.bodyMedium)
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
                Text(text = "开始连接")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}
