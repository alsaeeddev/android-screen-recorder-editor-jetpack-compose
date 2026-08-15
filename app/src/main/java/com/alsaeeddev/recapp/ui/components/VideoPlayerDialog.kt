package com.alsaeeddev.recapp.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.util.FormatUtils
import com.alsaeeddev.recapp.util.ShareUtils
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoPlayerDialog(
    item: RecordItem,
    onDismiss: () -> Unit,
    onDelete: (RecordItem) -> Unit,
    onRename: (RecordItem, String) -> Unit,
    onSaveEditedVideo: (RecordItem) -> Unit = {}
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showVideoEditor by remember { mutableStateOf(false) }
    var newTitleText by remember { mutableStateOf(item.title) }

    if (showVideoEditor) {
        VideoEditorDialog(
            item = item,
            onDismiss = { showVideoEditor = false },
            onSaveSuccess = { newItem ->
                onSaveEditedVideo(newItem)
                showVideoEditor = false
                onDismiss()
            }
        )
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val exoPlayer = remember(context, item.filePath, item.uriString) {
                ExoPlayer.Builder(context).build().apply {
                    val uri = if (item.uriString.isNotEmpty()) Uri.parse(item.uriString) else Uri.fromFile(File(item.filePath))
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = true
                }
            }

            var isPlaying by remember { mutableStateOf(true) }
            var isEnded by remember { mutableStateOf(false) }
            var showControls by remember { mutableStateOf(true) }
            var currentPositionMs by remember { mutableLongStateOf(0L) }
            var durationMs by remember { mutableLongStateOf(1L) }
            var isUserSeeking by remember { mutableStateOf(false) }
            var userSeekPositionMs by remember { mutableLongStateOf(0L) }

            DisposableEffect(exoPlayer) {
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            isPlaying = false
                            isEnded = true
                            showControls = true
                        } else if (playbackState == Player.STATE_READY) {
                            durationMs = exoPlayer.duration.coerceAtLeast(1L)
                        }
                    }
                }
                exoPlayer.addListener(listener)
                onDispose {
                    exoPlayer.removeListener(listener)
                    exoPlayer.release()
                }
            }

            // Sync playback position for seekbar
            LaunchedEffect(isPlaying, isUserSeeking) {
                while (isPlaying && !isUserSeeking) {
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    durationMs = exoPlayer.duration.coerceAtLeast(1L)
                    delay(150)
                }
            }

            // Auto hide controls after 3.5 seconds if playing
            LaunchedEffect(showControls, isPlaying) {
                if (showControls && isPlaying && !isUserSeeking) {
                    delay(3500)
                    showControls = false
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                color = Color.Black
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showControls = !showControls
                        }
                ) {
                    // Video Render View
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient overlays and controls
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Top Bar Gradient Backdrop
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.85f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            // Top Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .align(Alignment.TopCenter),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.3f))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${FormatUtils.formatDate(item.timestamp)}  •  ${FormatUtils.formatFileSize(item.sizeBytes)}",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 12.sp
                                    )
                                }

                              
                            }

                            // Center Play / Pause / Replay Controls
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 10s Rewind
                                IconButton(
                                    onClick = {
                                        val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                        exoPlayer.seekTo(newPos)
                                        currentPositionMs = newPos
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = "Rewind 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // Play / Pause Main Button
                                Surface(
                                    onClick = {
                                        if (isEnded) {
                                            exoPlayer.seekTo(0)
                                            exoPlayer.play()
                                            isEnded = false
                                        } else if (isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                    },
                                    shape = CircleShape,
                                    color = Color(0xFF3B82F6),
                                    contentColor = Color.White,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isEnded -> Icons.Default.Replay
                                                isPlaying -> Icons.Default.Pause
                                                else -> Icons.Default.PlayArrow
                                            },
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                // 10s Fast Forward
                                IconButton(
                                    onClick = {
                                        val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                                        exoPlayer.seekTo(newPos)
                                        currentPositionMs = newPos
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forward10,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Bottom Controls Container (Seekbar + Samsung Gallery Bottom Action Bar)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.9f),
                                                Color.Black
                                            )
                                        )
                                    )
                                    .navigationBarsPadding()
                                    .padding(bottom = 12.dp)
                            ) {
                                // Progress Seekbar Section
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val displayPos = if (isUserSeeking) userSeekPositionMs else currentPositionMs
                                    Text(
                                        text = FormatUtils.formatDuration(displayPos),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Slider(
                                        value = displayPos.toFloat().coerceIn(0f, durationMs.toFloat()),
                                        onValueChange = { newValue ->
                                            isUserSeeking = true
                                            userSeekPositionMs = newValue.toLong()
                                        },
                                        onValueChangeFinished = {
                                            exoPlayer.seekTo(userSeekPositionMs)
                                            currentPositionMs = userSeekPositionMs
                                            isUserSeeking = false
                                            if (isEnded) isEnded = false
                                        },
                                        valueRange = 0f..durationMs.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color(0xFF3B82F6),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )

                                    Text(
                                        text = FormatUtils.formatDuration(durationMs),
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Samsung Gallery Style Bottom Action Toolbar
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color(0xFF1E1E28)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Edit Button (Samsung Pencil style)
                                        SamsungActionButton(
                                            icon = Icons.Default.Edit,
                                            label = "Edit",
                                            tint = Color(0xFF3B82F6),
                                            onClick = {
                                                exoPlayer.pause()
                                                showVideoEditor = true
                                            }
                                        )

                                        // Share Button
                                        SamsungActionButton(
                                            icon = Icons.Default.Share,
                                            label = "Share",
                                            tint = Color.White,
                                            onClick = {
                                                ShareUtils.shareRecordItem(context, item)
                                            }
                                        )

                                        // Rename Button
                                        SamsungActionButton(
                                            icon = Icons.Default.TextFields,
                                            label = "Rename",
                                            tint = Color.White,
                                            onClick = {
                                                showRenameDialog = true
                                            }
                                        )

                                        // Delete Button
                                        SamsungActionButton(
                                            icon = Icons.Default.Delete,
                                            label = "Delete",
                                            tint = Color(0xFFEF4444),
                                            onClick = {
                                                showDeleteConfirmDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Recording", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitleText,
                    onValueChange = { newTitleText = it },
                    label = { Text("Video Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitleText.isNotBlank()) {
                            onRename(item, newTitleText)
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Recording", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to move this recording to the recycle bin?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(item)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SamsungActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = tint.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
