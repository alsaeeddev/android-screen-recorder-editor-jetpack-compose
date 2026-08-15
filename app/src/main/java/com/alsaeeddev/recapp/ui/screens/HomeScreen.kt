package com.alsaeeddev.recapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alsaeeddev.recapp.data.model.MediaType
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.data.model.RecordingSettings
import com.alsaeeddev.recapp.data.model.RecordingState
import com.alsaeeddev.recapp.ui.components.BentoCard
import com.alsaeeddev.recapp.ui.components.QuickSettingBentoTile
import com.alsaeeddev.recapp.ui.components.RecordControlCard
import com.alsaeeddev.recapp.ui.theme.BentoPrimary
import com.alsaeeddev.recapp.util.FormatUtils
import com.alsaeeddev.recapp.util.rememberOverlayPermissionState

@Composable
fun HomeScreen(
    recordingState: RecordingState,
    settings: RecordingSettings,
    recentRecords: List<RecordItem>,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleFloatingBubble: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectRecordItem: (RecordItem) -> Unit,
    onTakeScreenshot: () -> Unit
) {

    val overlayPermission = rememberOverlayPermissionState()

    LaunchedEffect(overlayPermission.hasPermission) {
        if (!overlayPermission.hasPermission && settings.showFloatingBubble) {
            onToggleFloatingBubble(false)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Top App Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Capture",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Screen Recording Studio",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Instant Screenshot Quick Button
                    IconButton(
                        onClick = onTakeScreenshot,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("quick_screenshot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Take Screenshot",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                }
            }
        }

        // Hero Record Card (Bento Style)
        item {
            RecordControlCard(
                recordingState = recordingState,
                onStartRecord = onStartRecording,
                onPauseRecord = onPauseRecording,
                onResumeRecord = onResumeRecording,
                onStopRecord = onStopRecording
            )
        }

        // Bento Grid Quick Settings Section (2x2 Grid)
        item {
            Text(
                text = "QUICK CONFIGURATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Resolution Tile
                QuickSettingBentoTile(
                    modifier = Modifier.weight(1f),
                    title = "Resolution",
                    value = settings.resolution.label.split(" ").first(),
                    iconBadgeText = "HD",
                    onClick = onOpenSettings,
                    testTag = "tile_resolution"
                )

                // Frame Rate Tile
                QuickSettingBentoTile(
                    modifier = Modifier.weight(1f),
                    title = "Frame Rate",
                    value = "${settings.fps.fps} FPS",
                    iconBadgeText = "Hz",
                    onClick = onOpenSettings,
                    testTag = "tile_fps"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Audio Source Tile
                QuickSettingBentoTile(
                    modifier = Modifier.weight(1f),
                    title = "Audio Source",
                    value = settings.audioSource.label,
                    icon = Icons.Default.Mic,
                    onClick = onOpenSettings,
                    testTag = "tile_audio"
                )


                // Floating Overlay Tile
                QuickSettingBentoTile(
                    modifier = Modifier.weight(1f),
                    title = "Overlay",
                    value = if (settings.showFloatingBubble && overlayPermission.hasPermission) "Floating UI" else "Off",
                    icon = Icons.Default.BubbleChart,
                    isAccent = settings.showFloatingBubble && overlayPermission.hasPermission,
                    hasSwitch = true,
                    switchChecked = settings.showFloatingBubble && overlayPermission.hasPermission,
                    onSwitchChange = { checked ->
                        if (checked) {
                            if (overlayPermission.hasPermission) {
                                onToggleFloatingBubble(true)
                            } else {
                                overlayPermission.requestPermission()
                            }
                        } else {
                            onToggleFloatingBubble(false)
                        }
                    },
                    testTag = "tile_overlay"
                )
            }
        }

        // Recent Captures Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT CAPTURES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
            }
        }

        if (recentRecords.isEmpty()) {
            item {
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = BentoPrimary.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No recordings yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap Start Recording to capture your screen",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentRecords.take(4)) { item ->
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    onClick = { onSelectRecordItem(item) },
                    testTag = "recent_record_item_${item.id}"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (item.mediaType == MediaType.VIDEO) BentoPrimary else Color(
                                        0xFFE2A000
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.PlayArrow else Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "${FormatUtils.formatDate(item.timestamp)} • ${
                                    FormatUtils.formatFileSize(
                                        item.sizeBytes
                                    )
                                }",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (item.mediaType == MediaType.VIDEO) {
                            Text(
                                text = FormatUtils.formatDuration(item.durationMs),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
