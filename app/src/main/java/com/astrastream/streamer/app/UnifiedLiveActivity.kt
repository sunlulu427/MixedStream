package com.astrastream.streamer.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.astrastream.avpush.unified.*
import com.astrastream.avpush.unified.builder.createStreamSession
import com.astrastream.avpush.unified.config.*
import com.astrastream.avpush.unified.error.StreamError
import com.astrastream.streamer.ui.theme.AVLiveTheme
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.coroutines.launch

/**
 * 使用统一推流API的示例Activity
 *
 * 展示如何使用新的统一推流接口进行直播推流
 */
class UnifiedLiveActivity : AppCompatActivity() {

    // 统一推流会话
    private var streamSession: UnifiedStreamSession? = null

    // UI状态
    private var isStreaming by mutableStateOf(false)
    private var isConnecting by mutableStateOf(false)
    private var streamUrl by mutableStateOf("rtmp://live.example.com/live/stream")
    private var connectionQuality by mutableStateOf(ConnectionQuality.POOR)
    private var streamStats by mutableStateOf<StreamStats?>(null)
    private var showUrlDialog by mutableStateOf(false)
    private var showStatsPanel by mutableStateOf(false)
    private var currentError by mutableStateOf<String?>(null)

    // 配置状态
    private var videoBitrate by mutableStateOf(2_000_000)
    private var videoResolution by mutableStateOf("1280x720")
    private var audioEnabled by mutableStateOf(true)
    private var cameraFacing by mutableStateOf(CameraFacing.BACK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeEnvironment()
        createUnifiedSession()
        setupUI()
        requestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            streamSession?.release()
        }
    }

    private fun initializeEnvironment() {
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

    private fun createUnifiedSession() {
        streamSession = createStreamSession {
            // 视频配置
            video {
                width = 1280
                height = 720
                frameRate = 30
                bitrate = videoBitrate
                keyFrameInterval = 2
                codec = VideoCodec.H264
                profile = VideoProfile.BASELINE
                enableHardwareAcceleration = true
                enableAdaptiveBitrate = true
                minBitrate = 500_000
                maxBitrate = 4_000_000
            }

            // 音频配置
            audio {
                sampleRate = 44100
                bitrate = 128_000
                channels = 2
                codec = AudioCodec.AAC
                enableAEC = true
                enableAGC = true
                enableNoiseReduction = true
            }

            // 摄像头配置
            camera {
                facing = cameraFacing
                autoFocus = true
                stabilization = true
                exposureMode = ExposureMode.AUTO
                whiteBalanceMode = WhiteBalanceMode.AUTO
            }

            // RTMP传输配置
            addRtmp(streamUrl) {
                connectTimeout = java.time.Duration.ofSeconds(10)
                retryPolicy = RetryPolicy.exponentialBackoff(maxRetries = 3)
                enableLowLatency = false
                enableTcpNoDelay = true
            }

            // 高级配置
            advanced {
                enableSimultaneousPush = false
                fallbackEnabled = true
                enableMetrics = true
                metricsInterval = java.time.Duration.ofSeconds(1)
                enableGpuAcceleration = true
                enableMemoryPool = true
            }
        }

        // 设置事件监听器
        streamSession?.setEventListener(object : StreamEventListener {
            override fun onSessionStateChanged(state: SessionState) {
                runOnUiThread {
                    when (state) {
                        SessionState.IDLE -> {
                            isStreaming = false
                            isConnecting = false
                            currentError = null
                        }
                        SessionState.PREPARING -> {
                            isConnecting = true
                            currentError = null
                        }
                        SessionState.STREAMING -> {
                            isStreaming = true
                            isConnecting = false
                            currentError = null
                            Toast.makeText(this@UnifiedLiveActivity, "推流已开始", Toast.LENGTH_SHORT).show()
                        }
                        is SessionState.ERROR -> {
                            isStreaming = false
                            isConnecting = false
                            currentError = state.error.message
                            Toast.makeText(this@UnifiedLiveActivity, "推流错误: ${state.error.message}", Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
            }

            override fun onConnectionQualityChanged(quality: ConnectionQuality) {
                runOnUiThread {
                    connectionQuality = quality
                }
            }

            override fun onStatsUpdated(stats: StreamStats) {
                runOnUiThread {
                    streamStats = stats
                }
            }

            override fun onError(error: StreamError) {
                runOnUiThread {
                    currentError = error.message
                    Toast.makeText(this@UnifiedLiveActivity, "错误: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun setupUI() {
        setContentView(ComposeView(this).apply {
            setContent {
                AVLiveTheme {
                    UnifiedLiveScreen()
                }
            }
        })
    }

    @Composable
    private fun UnifiedLiveScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 预览区域 (这里需要集成实际的预览Surface)
            PreviewArea(
                modifier = Modifier.fillMaxSize()
            )

            // 顶部控制栏
            TopControlBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            // 底部控制栏
            BottomControlBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )

            // 右侧控制面板
            RightControlPanel(
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            // 连接质量指示器
            ConnectionQualityIndicator(
                quality = connectionQuality,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )

            // 统计信息面板
            if (showStatsPanel) {
                StatsPanel(
                    stats = streamStats,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                )
            }

            // URL输入对话框
            if (showUrlDialog) {
                StreamUrlDialog(
                    currentUrl = streamUrl,
                    onConfirm = { url ->
                        streamUrl = url
                        updateStreamUrl(url)
                        showUrlDialog = false
                    },
                    onDismiss = { showUrlDialog = false }
                )
            }

            // 错误提示
            currentError?.let { error ->
                ErrorSnackbar(
                    error = error,
                    onDismiss = { currentError = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    @Composable
    private fun PreviewArea(modifier: Modifier = Modifier) {
        // 这里应该集成实际的预览Surface
        // 由于我们目前只是创建接口，这里用占位符
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (!isStreaming && !isConnecting) {
                Text(
                    text = "摄像头预览\n(需要集成实际预览组件)",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    @Composable
    private fun TopControlBar(modifier: Modifier = Modifier) {
        Row(
            modifier = modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            IconButton(
                onClick = { finish() },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }

            // 推流状态指示
            StreamStatusIndicator(
                isStreaming = isStreaming,
                isConnecting = isConnecting
            )

            // 设置按钮
            IconButton(
                onClick = { showUrlDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = Color.White
                )
            }
        }
    }

    @Composable
    private fun BottomControlBar(modifier: Modifier = Modifier) {
        Row(
            modifier = modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 切换摄像头
            IconButton(
                onClick = { switchCamera() },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "切换摄像头",
                    tint = Color.White
                )
            }

            // 开始/停止推流按钮
            Button(
                onClick = { toggleStreaming() },
                enabled = !isConnecting,
                modifier = Modifier
                    .height(56.dp)
                    .widthIn(min = 120.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) Color.Red else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isStreaming) "停止" else "开始",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 静音按钮
            IconButton(
                onClick = { toggleAudio() },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (audioEnabled) "静音" else "取消静音",
                    tint = if (audioEnabled) Color.White else Color.Red
                )
            }
        }
    }

    @Composable
    private fun RightControlPanel(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 统计信息按钮
            IconButton(
                onClick = { showStatsPanel = !showStatsPanel },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "统计信息",
                    tint = Color.White
                )
            }

            // 码率调节按钮
            IconButton(
                onClick = { adjustBitrate() },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "调节码率",
                    tint = Color.White
                )
            }
        }
    }

    @Composable
    private fun StreamStatusIndicator(
        isStreaming: Boolean,
        isConnecting: Boolean
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        when {
                            isStreaming -> Color.Green
                            isConnecting -> Color.Yellow
                            else -> Color.Gray
                        },
                        CircleShape
                    )
            )

            Text(
                text = when {
                    isStreaming -> "直播中"
                    isConnecting -> "连接中"
                    else -> "未连接"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    @Composable
    private fun ConnectionQualityIndicator(
        quality: ConnectionQuality,
        modifier: Modifier = Modifier
    ) {
        val (color, text) = when (quality) {
            ConnectionQuality.EXCELLENT -> Color.Green to "优秀"
            ConnectionQuality.GOOD -> Color.Yellow to "良好"
            ConnectionQuality.FAIR -> Color(0xFFFF9500) to "一般"
            ConnectionQuality.POOR -> Color.Red to "较差"
        }

        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    private fun StatsPanel(
        stats: StreamStats?,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "推流统计",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                stats?.let {
                    Text(
                        text = "码率: ${it.averageBitrate / 1000} kbps",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "帧率: ${it.currentFps} fps",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "丢帧: ${it.framesDropped}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "时长: ${it.sessionDuration.toMinutes()}:${it.sessionDuration.seconds % 60}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                } ?: run {
                    Text(
                        text = "暂无数据",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    @Composable
    private fun StreamUrlDialog(
        currentUrl: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        var url by remember { mutableStateOf(currentUrl) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("推流地址") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("RTMP URL") },
                    placeholder = { Text("rtmp://live.example.com/live/stream") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(url) }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        )
    }

    @Composable
    private fun ErrorSnackbar(
        error: String,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Red)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = error,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // 业务逻辑方法

    private fun toggleStreaming() {
        lifecycleScope.launch {
            try {
                if (isStreaming) {
                    streamSession?.stop()
                } else {
                    // 准备会话（需要提供SurfaceProvider的实际实现）
                    streamSession?.prepare(this@UnifiedLiveActivity, object : SurfaceProvider {
                        override fun getPreviewSurface(): android.view.Surface? {
                            // 这里需要返回实际的预览Surface
                            return null
                        }
                    })
                    streamSession?.start()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UnifiedLiveActivity, "操作失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun switchCamera() {
        cameraFacing = if (cameraFacing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK

        val newCameraConfig = CameraConfig(
            facing = cameraFacing,
            autoFocus = true,
            stabilization = true
        )

        // 注意：这里需要重新创建会话或者实现动态切换
        // streamSession?.updateCameraConfig(newCameraConfig)
    }

    private fun toggleAudio() {
        audioEnabled = !audioEnabled

        val newAudioConfig = AudioConfig(
            sampleRate = 44100,
            bitrate = if (audioEnabled) 128_000 else 0,
            channels = 2,
            codec = AudioCodec.AAC,
            enableAEC = true,
            enableAGC = true,
            enableNoiseReduction = true
        )

        streamSession?.updateAudioConfig(newAudioConfig)
    }

    private fun adjustBitrate() {
        // 简单的码率调节逻辑
        videoBitrate = when (videoBitrate) {
            500_000 -> 1_000_000
            1_000_000 -> 2_000_000
            2_000_000 -> 4_000_000
            else -> 500_000
        }

        val newVideoConfig = VideoConfig(
            width = 1280,
            height = 720,
            frameRate = 30,
            bitrate = videoBitrate,
            keyFrameInterval = 2,
            codec = VideoCodec.H264,
            enableHardwareAcceleration = true
        )

        streamSession?.updateVideoConfig(newVideoConfig)
        Toast.makeText(this, "码率已调整为: ${videoBitrate / 1000} kbps", Toast.LENGTH_SHORT).show()
    }

    private fun updateStreamUrl(url: String) {
        // 更新推流地址需要重新创建传输
        streamSession?.let { session ->
            // 移除旧的传输
            // 添加新的传输
            val newTransportId = session.addTransport(
                RtmpConfig(
                    pushUrl = url,
                    connectTimeout = java.time.Duration.ofSeconds(10),
                    retryPolicy = RetryPolicy.exponentialBackoff(maxRetries = 3)
                )
            )
            session.switchPrimaryTransport(newTransportId)
        }
    }

    @SuppressLint("CheckResult")
    private fun requestPermissions() {
        RxPermissions(this)
            .request(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .subscribe { granted ->
                if (!granted) {
                    Toast.makeText(this, "需要相机和音频权限才能正常使用", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}