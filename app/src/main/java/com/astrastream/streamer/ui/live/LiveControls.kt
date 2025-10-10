package com.astrastream.streamer.ui.live

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.astrastream.avpush.domain.config.VideoConfiguration

@Composable
fun CameraSwitchButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
    ) {
        Icon(imageVector = Icons.Outlined.Cameraswitch, contentDescription = "切换摄像头")
    }
}

@Composable
fun PanelToggleButton(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = if (expanded) "收起参数面板" else "展开参数面板"
        )
    }
}

@Composable
fun StreamingStatsOverlay(state: LiveUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    ) {
        val status = when {
            state.isConnecting -> "连接中"
            state.isStreaming -> "直播中"
            else -> "预览中"
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "状态: $status", style = MaterialTheme.typography.labelLarge)
            Text(text = "采集: ${state.captureResolution.label} @ ${state.videoFps}fps", style = MaterialTheme.typography.bodySmall)
            Text(text = "推流: ${state.streamResolution.label}", style = MaterialTheme.typography.bodySmall)
            Text(text = "当前码率: ${state.currentBitrate} kbps (目标 ${state.targetBitrate})", style = MaterialTheme.typography.bodySmall)
            Text(text = "实际帧率: ${state.currentFps} fps", style = MaterialTheme.typography.bodySmall)
            Text(text = "GOP: ${state.gop}", style = MaterialTheme.typography.bodySmall)
            Text(text = "编码: ${state.encoder.description}", style = MaterialTheme.typography.bodySmall)
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

@Composable
fun StreamUrlDialog(initialValue: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val text = remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "推流地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "请输入 RTMP 推流地址", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text.value,
                    onValueChange = { text.value = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.value) }) { Text(text = "确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        }
    )
}

@Composable
fun ParameterPanel(
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
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp.dp * 2f) / 3f

    val scrollState = rememberScrollState()

    Card(
        modifier = modifier.heightIn(max = maxHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "直播参数", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)

                OutlinedTextField(
                    value = state.streamUrl,
                    onValueChange = onStreamUrlChanged,
                    label = { Text("推流地址（可选）") },
                    placeholder = { Text("rtmp://host/app/stream") },
                    singleLine = false,
                    minLines = 1,
                    enabled = controlsEnabled,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri),
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

                EncoderDropdown(
                    options = encoderOptions,
                    selected = state.encoder,
                    onSelected = onEncoderSelected,
                    enabled = controlsEnabled
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "推流码率", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onBitrateChanged(state.targetBitrate - 100) }, enabled = controlsEnabled) {
                            Icon(Icons.Rounded.Remove, contentDescription = "降低码率")
                        }
                        OutlinedTextField(
                            value = state.targetBitrate.toString(),
                            onValueChange = onBitrateInput,
                            singleLine = true,
                            enabled = controlsEnabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp)
                        )
                        IconButton(onClick = { onBitrateChanged(state.targetBitrate + 100) }, enabled = controlsEnabled) {
                            Icon(Icons.Rounded.Add, contentDescription = "提升码率")
                        }
                    }
                }
                PullStreamList(
                    urls = state.pullUrls,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
private fun PullStreamList(urls: List<String>, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val hasUrl = urls.isNotEmpty()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "拉流地址",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            if (!hasUrl) {
                Text(
                    text = "暂无可用拉流地址",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    urls.forEachIndexed { index, url ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                SelectionContainer(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(url))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "复制地址")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParameterPanelOverlay(
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
    onDismiss: () -> Unit,
    controlsEnabled: Boolean
) {
    Box(Modifier.fillMaxSize()) {
        val overlayInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
                .clickable(indication = null, interactionSource = overlayInteraction) { onDismiss() }
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
                controlsEnabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionDropdown(
    label: String,
    options: List<ResolutionOption>,
    selected: ResolutionOption,
    onSelected: (ResolutionOption) -> Unit,
    enabled: Boolean
) {
    var expanded = remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded.value, onExpandedChange = { if (enabled) expanded.value = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) }
        )
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded.value = false
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
fun EncoderDropdown(
    options: List<EncoderOption>,
    selected: EncoderOption,
    onSelected: (EncoderOption) -> Unit,
    enabled: Boolean
) {
    var expanded = remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded.value, onExpandedChange = { if (enabled) expanded.value = it }) {
        OutlinedTextField(
            value = selected.description,
            onValueChange = {},
            label = { Text("编码器") },
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) }
        )
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = when {
                                    option.videoCodec == VideoConfiguration.VideoCodec.H265 -> "更高压缩率，更低带宽"
                                    option.videoCodec == VideoConfiguration.VideoCodec.H264 -> "兼容性更好，更稳定"
                                    else -> "CPU编码，功耗较高"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelected(option)
                        expanded.value = false
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
fun EncoderSegmentedControl(
    options: List<EncoderOption>,
    selected: EncoderOption,
    onSelected: (EncoderOption) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "编码器",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    selected = option == selected,
                    onClick = { if (enabled) onSelected(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
