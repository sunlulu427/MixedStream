package com.astrastream.streamer.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.astra.avpush.presentation.widget.AVLiveView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import kotlin.math.roundToInt

@Composable
fun LiveScreen(
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
    onLiveViewReady: (AVLiveView) -> Unit,
    modeSwitcher: @Composable () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val hasUrl = state.streamUrl.isNotBlank()
    val immersiveMode = state.isStreaming || state.isConnecting
    val cameraEnabled = !state.isConnecting
    val parameterEnabled = !state.isStreaming && !state.isConnecting
    val statsTopPadding = if (state.isStreaming) 24.dp else 132.dp
    val quickActions = listOf(
        LiveQuickAction(
            icon = Icons.Outlined.Cameraswitch,
            label = "翻转",
            enabled = cameraEnabled,
            onClick = onSwitchCamera
        ),
        LiveQuickAction(
            icon = Icons.Rounded.Tune,
            label = "参数",
            selected = state.showParameterPanel,
            enabled = parameterEnabled || state.showParameterPanel,
            onClick = onTogglePanel
        ),
        LiveQuickAction(
            icon = Icons.Rounded.Info,
            label = "数据",
            selected = state.showStats,
            onClick = { onStatsToggle(!state.showStats) }
        ),
        LiveQuickAction(
            icon = Icons.Rounded.Link,
            label = if (hasUrl) "推流已设" else "推流地址",
            onClick = onShowUrlDialog
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val horizontalMarginPx = with(density) { 20.dp.toPx() }
        val bottomMarginPx = horizontalMarginPx
        val minTopPaddingPx = with(density) { statsTopPadding.toPx() }

        var statsOffset by remember { mutableStateOf<Offset?>(null) }
        var statsSize by remember { mutableStateOf(IntSize.Zero) }

        data class Bounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

        val bounds = remember(containerWidthPx, containerHeightPx, statsSize, minTopPaddingPx, horizontalMarginPx, bottomMarginPx) {
            if (statsSize == IntSize.Zero || containerWidthPx <= 0f || containerHeightPx <= 0f) {
                null
            } else {
                val availableWidth = containerWidthPx - statsSize.width
                val availableHeight = containerHeightPx - statsSize.height
                val minX = horizontalMarginPx.coerceAtMost(availableWidth)
                // 修复：为右上角信息按钮留出空间，避免重叠
                val rightButtonSpace = with(density) { 60.dp.toPx() } // 为右上角按钮留出空间
                val maxX = (availableWidth - horizontalMarginPx - rightButtonSpace).coerceAtLeast(minX)
                val minY = minTopPaddingPx.coerceAtMost(availableHeight)
                val maxY = (availableHeight - bottomMarginPx).coerceAtLeast(minY)
                Bounds(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
            }
        }

        LaunchedEffect(bounds) {
            bounds ?: return@LaunchedEffect
            // 修复：将默认位置设置为左上角，避免与右上角信息按钮重叠
            val target = statsOffset ?: Offset(bounds.minX, bounds.minY)
            val clamped = Offset(
                target.x.coerceIn(bounds.minX, bounds.maxX),
                target.y.coerceIn(bounds.minY, bounds.maxY)
            )
            if (statsOffset != clamped) {
                statsOffset = clamped
            }
        }

        LivePreview(onLiveViewReady = onLiveViewReady, modifier = Modifier.fillMaxSize())

        if (!immersiveMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween
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
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = Color.White
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.4f)
                        ) {
                            val statusLabel = when {
                                state.isStreaming -> "直播中"
                                state.isConnecting -> "连接中"
                                else -> "预览中"
                            }
                            Text(
                                text = statusLabel,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }

                    modeSwitcher()

                    LivePreparationCard(
                        state = state,
                        hasUrl = hasUrl,
                        onShowUrlDialog = onShowUrlDialog
                    )
                }

                AnimatedVisibility(visible = !state.showParameterPanel) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        LiveQuickActionRow(actions = quickActions)

                        val buttonColor = when {
                            state.isStreaming -> MaterialTheme.colorScheme.error
                            hasUrl -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        }
                        val buttonLabel = when {
                            state.isStreaming -> "结束视频直播"
                            state.isConnecting -> "连接中..."
                            state.streamUrl.isBlank() -> "设置推流地址"
                            else -> "开始视频直播"
                        }

                        Button(
                            onClick = {
                                when {
                                    state.isStreaming -> onToggleLive()
                                    !hasUrl -> onShowUrlDialog()
                                    else -> onToggleLive()
                                }
                            },
                            enabled = !state.isConnecting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            val buttonIcon = if (state.isStreaming) Icons.Rounded.Stop else Icons.Rounded.PlayArrow
                            Icon(imageVector = buttonIcon, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = buttonLabel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            LiveImmersiveControls(
                state = state,
                onToggleLive = onToggleLive,
                onStatsToggle = onStatsToggle,
                onBack = onBack
            )
        }

        PreviewStatusOverlay(
            visible = !state.previewReady,
            message = state.cameraError,
            modifier = Modifier.align(Alignment.Center)
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

        AnimatedVisibility(
            visible = state.showStats && state.previewReady,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val overlayOffset = statsOffset
                    ?: bounds?.let { Offset(it.minX, it.minY) }
                    ?: Offset(horizontalMarginPx, minTopPaddingPx)
                StreamingStatsOverlay(
                    state = state,
                    onClose = { onStatsToggle(false) },
                    modifier = Modifier
                        .offset {
                            IntOffset(overlayOffset.x.roundToInt(), overlayOffset.y.roundToInt())
                        }
                        .pointerInput(Unit) {
                            val currentBounds = bounds ?: return@pointerInput
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val current = statsOffset ?: overlayOffset
                                val newOffset = Offset(
                                    (current.x + dragAmount.x).coerceIn(currentBounds.minX, currentBounds.maxX),
                                    (current.y + dragAmount.y).coerceIn(currentBounds.minY, currentBounds.maxY)
                                )
                                statsOffset = newOffset
                            }
                        }
                        .onGloballyPositioned { coordinates: LayoutCoordinates ->
                            statsSize = coordinates.size
                        }
                )
            }
        }

        AnimatedVisibility(
            visible = state.showParameterPanel && !immersiveMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f)
        ) {
            ParameterPanelOverlay(
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
                onDismiss = onTogglePanel,
                controlsEnabled = parameterEnabled
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
private fun LivePreparationCard(
    state: LiveUiState,
    hasUrl: Boolean,
    onShowUrlDialog: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "相机直播准备就绪",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "开启位置 · 所有人可见",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    if (hasUrl) {
                        Text(
                            text = "推流地址已配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                    } else {
                        Text(
                            text = "尚未设置推流地址",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowUrlDialog),
                tonalElevation = 0.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "选择直播内容", color = Color.White, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (hasUrl) "推流地址可编辑" else "点击填写推流地址",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Icon(imageVector = Icons.Rounded.Link, contentDescription = null, tint = Color.White.copy(alpha = 0.9f))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "实时概览", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                if (state.showStats) {
                    Text(
                        text = "采集：${state.captureResolution.label} @ ${state.videoFps}fps",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "推流：${state.streamResolution.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "码率：${state.currentBitrate}kbps · 目标${state.targetBitrate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "实际帧率：${state.currentFps}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                } else {
                    Text(
                        text = "数据概览已隐藏，可在下方开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveQuickActionRow(actions: List<LiveQuickAction>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        actions.forEach { action ->
            LiveQuickActionItem(action = action)
        }
    }
}

@Composable
private fun LiveQuickActionItem(action: LiveQuickAction) {
    val backgroundColor = when {
        !action.enabled -> Color.White.copy(alpha = 0.06f)
        action.selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val contentColor = if (action.selected) MaterialTheme.colorScheme.primary else Color.White
    val interactionSource = remember { MutableInteractionSource() }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(backgroundColor)
                .clickable(enabled = action.enabled, interactionSource = interactionSource, indication = null) {
                    action.onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = action.icon, contentDescription = action.label, tint = contentColor)
        }
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (action.enabled) Color.White else Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class LiveQuickAction(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
private fun LiveImmersiveControls(
    state: LiveUiState,
    onToggleLive: () -> Unit,
    onStatsToggle: (Boolean) -> Unit,
    onBack: (() -> Unit)?
) {
    val buttonLabel = if (state.isStreaming) "结束视频直播" else "取消连接"
    val buttonColor = if (state.isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
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
                val statusLabel = when {
                    state.isStreaming -> "直播中"
                    state.isConnecting -> "连接中"
                    else -> "预览中"
                }
                Text(
                    text = statusLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }

            IconButton(
                onClick = { onStatsToggle(!state.showStats) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                val icon = if (state.showStats) Icons.Rounded.Info else Icons.Rounded.MoreHoriz
                Icon(imageVector = icon, contentDescription = "切换统计", tint = Color.White)
            }
        }

        Button(
            onClick = onToggleLive,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            val icon = if (state.isStreaming) Icons.Rounded.Stop else Icons.Rounded.Close
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = buttonLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PreviewStatusOverlay(visible: Boolean, message: String?, modifier: Modifier = Modifier) {
    val text = message ?: "等待摄像头预览启动..."
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LivePreview(onLiveViewReady: (AVLiveView) -> Unit, modifier: Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context -> AVLiveView(context).also(onLiveViewReady) },
        modifier = modifier
    )
}
