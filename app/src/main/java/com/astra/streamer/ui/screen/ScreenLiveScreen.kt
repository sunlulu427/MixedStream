package com.astra.streamer.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext

@Composable
fun ScreenLiveScreen(
    state: ScreenLiveUiState,
    onStreamUrlChanged: (String) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onToggleMic: (Boolean) -> Unit,
    onTogglePlayback: (Boolean) -> Unit,
    onToggleStats: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modeSwitcher: @Composable () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val hasUrl = state.streamUrl.isNotBlank()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF12131A),
                            Color(0xFF1B1F2A),
                            Color(0xFF222836)
                        )
                    )
                )
        )

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                        ) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.4f)
                    ) {
                        val statusText = when {
                            state.isStreaming -> "手游直播中"
                            state.isConnecting -> "连接中"
                            state.projectionReady -> "投屏已就绪"
                            else -> "等待投屏"
                        }
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }

                modeSwitcher()

                ScreenPreparationCard(
                    state = state,
                    hasUrl = hasUrl,
                    onRequestOverlay = onRequestOverlay
                )
            }

            ScreenPublishField(value = state.streamUrl, onValueChange = onStreamUrlChanged)

            ScreenBitrateControl(targetBitrate = state.targetBitrate, onBitrateChanged = onBitrateChanged)

            ScreenToggleRow(
                label = "采集麦克风",
                checked = state.includeMic,
                onToggle = onToggleMic
            )
            ScreenToggleRow(
                label = "捕获游戏音频",
                checked = state.includePlayback,
                onToggle = onTogglePlayback
            )
            ScreenToggleRow(
                label = "显示数据面板",
                checked = state.showStats,
                onToggle = onToggleStats
            )

            OverlayPermissionRow(granted = state.overlayPermissionGranted, onRequest = onRequestOverlay)

            AnimatedVisibility(
                visible = state.showStats,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ScreenStatsCard(
                    bitrate = state.currentBitrate,
                    fps = state.currentFps,
                    onCopy = {
                        val textValue = "Bitrate: ${state.currentBitrate} kbps\nFPS: ${state.currentFps}"
                        clipboard.setText(AnnotatedString(textValue))
                    }
                )
            }

            ScreenFeatureRow()

            Spacer(modifier = Modifier.height(160.dp))
        }

        ScreenStartButton(
            state = state,
            hasUrl = hasUrl,
            onStart = onStart,
            onStop = onStop,
            onMissingUrl = { Toast.makeText(context, "请先填写推流地址", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        AnimatedVisibility(
            visible = state.isConnecting,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(0.55f),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ScreenStartButton(
    state: ScreenLiveUiState,
    hasUrl: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMissingUrl: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor = when {
        state.isStreaming -> MaterialTheme.colorScheme.error
        state.isConnecting -> MaterialTheme.colorScheme.tertiary
        !hasUrl -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

    val buttonLabel = when {
        state.isStreaming -> "结束手游直播"
        state.isConnecting -> "取消连接"
        !hasUrl -> "设置推流地址"
        else -> "开始手游直播"
    }

    val buttonIcon = when {
        state.isStreaming -> Icons.Rounded.Stop
        state.isConnecting -> Icons.Rounded.MoreHoriz
        else -> Icons.Rounded.PlayArrow
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        Button(
            onClick = {
                when {
                    state.isStreaming -> onStop()
                    state.isConnecting -> onStop()
                    !hasUrl -> onMissingUrl()
                    else -> onStart()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(imageVector = buttonIcon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = buttonLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ScreenPreparationCard(
    state: ScreenLiveUiState,
    hasUrl: Boolean,
    onRequestOverlay: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Rounded.SportsEsports, contentDescription = null, tint = Color.White)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "手游直播准备就绪",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = if (state.projectionReady) "分辨率 ${state.resolutionLabel}" else "等待获取投屏权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Text(
                        text = if (hasUrl) "推流地址已配置" else "请先配置推流地址",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasUrl) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "悬浮窗控制", color = Color.White, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (state.overlayPermissionGranted) "已开启快捷控件" else "授予权限后可快速结束直播",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                if (state.overlayPermissionGranted) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "已启用",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    TextButton(onClick = onRequestOverlay) {
                        Text(text = "去开启", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenPublishField(value: String, onValueChange: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(18.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(text = "推流地址", color = Color.White.copy(alpha = 0.8f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Link, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
            },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(value))
                        Toast.makeText(context, "已复制推流地址", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "复制推流地址", tint = Color.White.copy(alpha = 0.85f))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenBitrateControl(targetBitrate: Int, onBitrateChanged: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "目标码率：${targetBitrate} kbps", color = Color.White, fontWeight = FontWeight.SemiBold)
        Slider(
            value = targetBitrate.toFloat(),
            onValueChange = { onBitrateChanged(it.toInt()) },
            valueRange = 500f..6000f,
            steps = 10,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun ScreenToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun OverlayPermissionRow(granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "悬浮窗控制", color = Color.White)
            Text(
                text = if (granted) "在所有界面快速结束直播" else "授予权限后可在游戏内操作",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        if (granted) {
            Text(text = "已开启", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        } else {
            TextButton(onClick = onRequest) {
                Text(text = "去开启", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ScreenStatsCard(bitrate: Int, fps: Int, onCopy: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "实时数据", color = Color.White, fontWeight = FontWeight.Medium)
                Text(text = "码率：${bitrate} kbps", color = Color.White.copy(alpha = 0.8f))
                Text(text = "帧率：$fps fps", color = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = onCopy) {
                Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "复制", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ScreenFeatureRow() {
    val features = listOf(
        ScreenFeature(icon = Icons.Rounded.Hd, label = "高清"),
        ScreenFeature(icon = Icons.Rounded.Cast, label = "投屏"),
        ScreenFeature(icon = Icons.Rounded.SportsEsports, label = "游戏"),
        ScreenFeature(icon = Icons.Rounded.MoreHoriz, label = "更多")
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        features.forEach { feature ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = feature.icon, contentDescription = feature.label, tint = Color.White)
                }
                Text(text = feature.label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class ScreenFeature(val icon: ImageVector, val label: String)
