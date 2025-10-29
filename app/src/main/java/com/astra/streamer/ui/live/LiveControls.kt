package com.astra.streamer.ui.live

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.astra.avpush.domain.config.VideoConfiguration

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
fun StreamingStatsOverlay(
    state: LiveUiState, 
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .widthIn(max = 280.dp)
            .heightIn(min = 180.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        val status = when {
            state.isConnecting -> "Connecting"
            state.isStreaming -> "Live"
            else -> "Preview"
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 添加标题行，包含状态和关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "直播中", style = MaterialTheme.typography.labelLarge)
                onClose?.let { closeAction ->
                    IconButton(
                        onClick = closeAction,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭统计面板",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            Text(text = "状态：$status", style = MaterialTheme.typography.bodySmall)
            Text(text = "采集：${state.captureResolution.label} @ ${state.videoFps}fps", style = MaterialTheme.typography.bodySmall)
            Text(text = "推流：${state.streamResolution.label}", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "码率：${state.currentBitrate} kbps / 目标 ${state.targetBitrate}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(text = "实际帧率：${state.currentFps}", style = MaterialTheme.typography.bodySmall)
            Text(text = "GOP：${state.gop}", style = MaterialTheme.typography.bodySmall)
            Text(text = "编码：${state.encoder.description}", style = MaterialTheme.typography.bodySmall)
            if (state.streamUrl.isNotBlank()) {
                Text(
                    text = "URL：${state.streamUrl}",
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
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Publish URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Enter an RTMP publish URL", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text.value,
                    onValueChange = { text.value = it },
                    singleLine = true,
                    trailingIcon = {
                        if (text.value.isNotBlank()) {
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(text.value))
                                Toast.makeText(context, "已复制推流地址", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "复制推流地址")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.value) }) { Text(text = "Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
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
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
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
                Text(text = "Live parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = state.streamUrl,
                    onValueChange = onStreamUrlChanged,
                    label = { Text("Publish URL (optional)") },
                    placeholder = { Text("rtmp://host/app/stream") },
                    singleLine = false,
                    minLines = 1,
                    enabled = controlsEnabled,
                    trailingIcon = {
                        if (state.streamUrl.isNotBlank()) {
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(state.streamUrl))
                                Toast.makeText(context, "已复制推流地址", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "复制推流地址"
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                ResolutionDropdown(
                    label = "Capture resolution",
                    options = captureOptions,
                    selected = state.captureResolution,
                    onSelected = onCaptureResolutionSelected,
                    enabled = controlsEnabled
                )

                ResolutionDropdown(
                    label = "Stream resolution",
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
                    Text(text = "Target bitrate", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onBitrateChanged(state.targetBitrate - 100) }, enabled = controlsEnabled) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease bitrate")
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
                            Icon(Icons.Rounded.Add, contentDescription = "Increase bitrate")
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
                Text(text = "Show live statistics", style = MaterialTheme.typography.bodyMedium)
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
                text = "Playback URLs",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (!hasUrl) {
                Text(
                    text = "No playback URL available",
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
                                    fontWeight = FontWeight.Medium
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
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy address")
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
                DropdownMenuItem(
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
            label = { Text("Encoder") },
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
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = when {
                                    option.videoCodec == VideoConfiguration.VideoCodec.H265 -> "High compression, lower bandwidth"
                                    option.videoCodec == VideoConfiguration.VideoCodec.H264 -> "Highest compatibility"
                                    else -> "Software codec, higher power usage"
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
            text = "Encoder",
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
