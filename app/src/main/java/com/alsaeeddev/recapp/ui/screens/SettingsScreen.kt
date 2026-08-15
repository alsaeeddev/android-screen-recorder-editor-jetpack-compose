package com.alsaeeddev.recapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alsaeeddev.recapp.data.model.AudioSourceOption
import com.alsaeeddev.recapp.data.model.BitrateOption
import com.alsaeeddev.recapp.data.model.EncoderOption
import com.alsaeeddev.recapp.data.model.FpsOption
import com.alsaeeddev.recapp.data.model.RecordingRegionOption
import com.alsaeeddev.recapp.data.model.RecordingSettings
import com.alsaeeddev.recapp.data.model.ResolutionOption
import com.alsaeeddev.recapp.ui.components.BentoCard
import com.alsaeeddev.recapp.ui.theme.BentoPrimary
import com.alsaeeddev.recapp.util.rememberOverlayPermissionState

@Composable
fun SettingsScreen(
    settings: RecordingSettings,
    onUpdateSettings: (RecordingSettings) -> Unit
) {
    var showRegionDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showEncoderDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showCountdownDialog by remember { mutableStateOf(false) }

    val overlayPermission = rememberOverlayPermissionState()

    LaunchedEffect(overlayPermission.hasPermission) {
        if (!overlayPermission.hasPermission && settings.showFloatingBubble) {
            onUpdateSettings(settings.copy(showFloatingBubble = false))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Top Title
        item {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Text(
                    text = "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Customize video, audio, overlay, and theme",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section: Video Settings
        item {
            SettingsSectionHeader(title = "VIDEO QUALITY & CODEC")
        }

        item {
            BentoCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsItemRow(
                        icon = Icons.Default.Crop,
                        title = "Recording Area",
                        subtitle = "${settings.recordingRegion.label} (${settings.recordingRegion.description})",
                        onClick = { showRegionDialog = true },
                        testTag = "setting_recording_region"
                    )

                    HorizontalDivider(
                        //  modifier = Modifier.padding(vertical = 4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    SettingsItemRow(
                        icon = Icons.Default.HighQuality,
                        title = "Resolution",
                        subtitle = settings.resolution.label,
                        onClick = { showResolutionDialog = true },
                        testTag = "setting_resolution"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    SettingsItemRow(
                        icon = Icons.Default.Speed,
                        title = "Frame Rate",
                        subtitle = settings.fps.label,
                        onClick = { showFpsDialog = true },
                        testTag = "setting_fps"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    SettingsItemRow(
                        icon = Icons.Default.DataUsage,
                        title = "Bitrate",
                        subtitle = settings.bitrate.label,
                        onClick = { showBitrateDialog = true },
                        testTag = "setting_bitrate"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    SettingsItemRow(
                        icon = Icons.Default.Memory,
                        title = "Video Encoder",
                        subtitle = settings.encoder.label,
                        onClick = { showEncoderDialog = true },
                        testTag = "setting_encoder"
                    )
                }
            }
        }

        // Section: Audio Settings
        item {
            SettingsSectionHeader(title = "AUDIO & RECORDING CONTROLS")
        }

        item {
            BentoCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsItemRow(
                        icon = Icons.Default.Mic,
                        title = "Audio Source",
                        subtitle = settings.audioSource.label,
                        onClick = { showAudioDialog = true },
                        testTag = "setting_audio_source"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    SettingsItemRow(
                        icon = Icons.Default.Timer,
                        title = "Countdown Timer",
                        subtitle = if (settings.countdownSeconds == 0) "Off (Instant)" else "${settings.countdownSeconds} Seconds",
                        onClick = { showCountdownDialog = true },
                        testTag = "setting_countdown"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    SettingsToggleRow(
                        icon = Icons.Default.BubbleChart,
                        title = "Floating Controls Bubble",
                        subtitle = if (overlayPermission.hasPermission) "Show draggable overlay head" else "Requires \"Appear on top\" permission",
                        checked = settings.showFloatingBubble && overlayPermission.hasPermission,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (overlayPermission.hasPermission) {
                                    onUpdateSettings(settings.copy(showFloatingBubble = true))
                                } else {
                                    overlayPermission.requestPermission()
                                }
                            } else {
                                onUpdateSettings(settings.copy(showFloatingBubble = false))
                            }
                        },
                        testTag = "setting_floating_bubble_switch"
                    )

                }
            }
        }

        // Section: Appearance & Theme
        item {
            SettingsSectionHeader(title = "APPEARANCE & ABOUT")
        }

        item {
            BentoCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    /*   SettingsToggleRow(
                           icon = Icons.Default.DarkMode,
                           title = "Dark Mode",
                           subtitle = "Enable dark bento theme",
                           checked = settings.isDarkMode,
                           onCheckedChange = { onUpdateSettings(settings.copy(isDarkMode = it)) },
                           testTag = "setting_dark_mode_switch"
                       )

                       HorizontalDivider(
                           modifier = Modifier.padding(vertical = 4.dp),
                           color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                       )*/

                    SettingsItemRow(
                        icon = Icons.Default.Info,
                        title = "About Screen Recorder",
                        subtitle = "Version 1.0.0 • Bento Edition",
                        onClick = {},
                        testTag = "setting_about",
                        showArrow = false,
                        clickable = false
                    )
                }
            }
        }
    }

    // Dialogs for configuration options
    if (showRegionDialog) {
        OptionSelectionDialog(
            title = "Select Recording Area",
            options = RecordingRegionOption.values().toList(),
            selectedOption = settings.recordingRegion,
            labelProvider = { "${it.label} - ${it.description}" },
            onSelect = {
                onUpdateSettings(settings.copy(recordingRegion = it))
                showRegionDialog = false
            },
            onDismiss = { showRegionDialog = false }
        )
    }

    if (showResolutionDialog) {
        OptionSelectionDialog(
            title = "Select Resolution",
            options = ResolutionOption.values().toList(),
            selectedOption = settings.resolution,
            labelProvider = { it.label },
            onSelect = {
                onUpdateSettings(settings.copy(resolution = it))
                showResolutionDialog = false
            },
            onDismiss = { showResolutionDialog = false }
        )
    }

    if (showFpsDialog) {
        OptionSelectionDialog(
            title = "Select Frame Rate",
            options = FpsOption.values().toList(),
            selectedOption = settings.fps,
            labelProvider = { it.label },
            onSelect = {
                onUpdateSettings(settings.copy(fps = it))
                showFpsDialog = false
            },
            onDismiss = { showFpsDialog = false }
        )
    }

    if (showBitrateDialog) {
        OptionSelectionDialog(
            title = "Select Bitrate",
            options = BitrateOption.values().toList(),
            selectedOption = settings.bitrate,
            labelProvider = { it.label },
            onSelect = {
                onUpdateSettings(settings.copy(bitrate = it))
                showBitrateDialog = false
            },
            onDismiss = { showBitrateDialog = false }
        )
    }

    if (showEncoderDialog) {
        OptionSelectionDialog(
            title = "Select Video Encoder",
            options = EncoderOption.values().toList(),
            selectedOption = settings.encoder,
            labelProvider = { it.label },
            onSelect = {
                onUpdateSettings(settings.copy(encoder = it))
                showEncoderDialog = false
            },
            onDismiss = { showEncoderDialog = false }
        )
    }

    if (showAudioDialog) {
        OptionSelectionDialog(
            title = "Select Audio Source",
            options = AudioSourceOption.values().toList(),
            selectedOption = settings.audioSource,
            labelProvider = { it.label },
            onSelect = {
                onUpdateSettings(settings.copy(audioSource = it))
                showAudioDialog = false
            },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showCountdownDialog) {
        val countdownOptions = listOf(0, 3, 5, 10)
        AlertDialog(
            onDismissRequest = { showCountdownDialog = false },
            title = { Text("Countdown Timer") },
            text = {
                Column {
                    countdownOptions.forEach { count ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(settings.copy(countdownSeconds = count))
                                    showCountdownDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.countdownSeconds == count,
                                onClick = {
                                    onUpdateSettings(settings.copy(countdownSeconds = count))
                                    showCountdownDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (count == 0) "Off" else "$count seconds")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String = "",
    showArrow: Boolean = true,
    clickable: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (clickable) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            //.clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            // .padding(vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BentoPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BentoPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showArrow) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            // .padding(vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BentoPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BentoPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BentoPrimary,
                checkedBorderColor = BentoPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
fun <T> OptionSelectionDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onSelect(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = labelProvider(option))
                    }
                }
            }
        },
        confirmButton = {}
    )
}
