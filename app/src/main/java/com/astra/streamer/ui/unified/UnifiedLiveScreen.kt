package com.astra.streamer.ui.unified

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astra.avpush.unified.ConnectionQuality
import com.astra.avpush.unified.StreamStats
import java.time.Duration

/**
 * 统一推流API的UI界面
 */
@Composable
fun UnifiedLiveScreen(
    state: UnifiedUiState,
    onToggleStreaming: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleAudio: () -> Unit,
    onAdjustBitrate: () -> Unit,
    onToggleStats: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modeSwitcher: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 预览区域 (占位符)
        PreviewArea(
            modifier = Modifier.fillMaxSize()
        )

        // 顶部控制栏
        TopControlBar(
            onBack = onBack,
            isStreaming = state.isStreaming,
            isConnecting = state.isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // 模式切换器
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            modeSwitcher()
        }

        // 底部控制栏
        BottomControlBar(
            isStreaming = state.isStreaming,
            isConnecting = state.isConnecting,
            audioEnabled = state.audioEnabled,
            onToggleStreaming = onToggleStreaming,
            onSwitchCamera = onSwitchCamera,
            onToggleAudio = onToggleAudio,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )

        // 右侧控制面板
        RightControlPanel(
            onAdjustBitrate = onAdjustBitrate,
            onToggleStats = onToggleStats,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // 连接质量指示器
        ConnectionQualityIndicator(
            quality = state.connectionQuality,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        // 统计信息面板
        if (state.showStatsPanel) {
            StatsPanel(
                stats = state.streamStats,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }

        // 错误提示
        state.currentError?.let { error ->
            ErrorSnackbar(
                error = error,
                onDismiss = onDismissError,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun PreviewArea(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "统一推流API预览\n(集成中...)",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun TopControlBar(
    onBack: () -> Unit,
    isStreaming: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮
        IconButton(
            onClick = onBack,
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

        // 占位符，保持布局平衡
        Spacer(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun BottomControlBar(
    isStreaming: Boolean,
    isConnecting: Boolean,
    audioEnabled: Boolean,
    onToggleStreaming: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 切换摄像头
        IconButton(
            onClick = onSwitchCamera,
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
            onClick = onToggleStreaming,
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
            onClick = onToggleAudio,
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
private fun RightControlPanel(
    onAdjustBitrate: () -> Unit,
    onToggleStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 统计信息按钮
        IconButton(
            onClick = onToggleStats,
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
            onClick = onAdjustBitrate,
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
                isStreaming -> "统一API直播中"
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
                text = "统一API统计",
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
                    text = "时长: ${formatDuration(it.sessionDuration)}",
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

private fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes()
    val seconds = duration.seconds % 60
    return String.format("%d:%02d", minutes, seconds)
}