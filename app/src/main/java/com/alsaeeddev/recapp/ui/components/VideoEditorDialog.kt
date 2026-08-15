package com.alsaeeddev.recapp.ui.components

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.snapshotFlow
import com.alsaeeddev.recapp.data.model.MediaType
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.ui.theme.BentoPrimary
import com.alsaeeddev.recapp.util.BlurShape
import com.alsaeeddev.recapp.util.BlurType
import com.alsaeeddev.recapp.util.FormatUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import com.alsaeeddev.recapp.util.VideoEditUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.hypot

//  Studio Color Palette
private val StudioBg = Color(0xFF09090E)
private val StudioPanelBg = Color(0xFF14141F)
private val StudioCardBg = Color(0xFF1C1C2A)
private val StudioCyan = Color(0xFF00E5FF)
private val StudioCyanDark = Color(0xFF00838F)
private val StudioIndigo = Color(0xFF6366F1)
private val StudioRed = Color(0xFFFF3B30)
private val StudioTextSecondary = Color(0xFFA0A0B8)

enum class StudioEditorTab(val title: String, val icon: ImageVector) {
    TRIM_SPLIT("Trim & Split", Icons.Default.ContentCut),
    SPEED("Speed", Icons.Default.Speed),
    CROP("Crop & Format", Icons.Default.Crop),
    ROTATE("Rotate", Icons.Default.RotateRight),
    AUDIO("Audio", Icons.Default.VolumeUp),
    BLUR("Blur Region", Icons.Default.BlurCircular)
}

enum class StudioAspectRatio(val label: String, val ratioText: String, val ratio: Float?) {
    FREE("Free", "Custom", null),
    RATIO_16_9("16:9", "YouTube", 16f / 9f),
    RATIO_9_16("9:16", "Reels", 9f / 16f),
    RATIO_1_1("1:1", "Square", 1f),
    RATIO_4_3("4:3", "Standard", 4f / 3f),
    RATIO_3_4("3:4", "Portrait", 3f / 4f)
}

@Composable
fun VideoEditorDialog(
    item: RecordItem,
    onDismiss: () -> Unit,
    onSaveSuccess: (RecordItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Working video path (starts with original file path, updates when effects are applied)
    var workingFilePath by remember { mutableStateOf(item.filePath) }
    val isEditedFromOriginal = remember(workingFilePath) { workingFilePath != item.filePath }

    // Metadata
    var durationMs by remember { mutableStateOf(0L) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Player instances & states
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerInstance by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }

    // Active Editor Tab
    var activeTab by remember { mutableStateOf(StudioEditorTab.TRIM_SPLIT) }

    // Unapplied tab adjustments
    var startTrimMs by remember { mutableStateOf(0L) }
    var endTrimMs by remember { mutableStateOf(0L) }

    var enableCutOut by remember { mutableStateOf(false) }
    var cutStartMs by remember { mutableStateOf(0L) }
    var cutEndMs by remember { mutableStateOf(0L) }

    var speedRatio by remember { mutableStateOf(1.0f) }
    var isAudioMuted by remember { mutableStateOf(false) }
    var rotationDegrees by remember { mutableStateOf(0) }
    var lastRotateDirection by remember { mutableStateOf<String?>(null) }

    // Effective Video Duration & Effective Playhead Position accounting for speed preview
    val effectiveDurationMs = remember(durationMs, speedRatio) {
        if (speedRatio > 0f) (durationMs.toDouble() / speedRatio.toDouble()).toLong() else durationMs
    }
    val effectivePositionMs = remember(currentPositionMs, speedRatio) {
        if (speedRatio > 0f) (currentPositionMs.toDouble() / speedRatio.toDouble()).toLong() else currentPositionMs
    }

    // Crop settings (Normalized 0f..1f)
    var cropLeftNorm by remember { mutableStateOf(0f) }
    var cropTopNorm by remember { mutableStateOf(0f) }
    var cropRightNorm by remember { mutableStateOf(1f) }
    var cropBottomNorm by remember { mutableStateOf(1f) }
    var selectedAspectRatio by remember { mutableStateOf(StudioAspectRatio.FREE) }
    var activeCropHandle by remember { mutableStateOf(0) }

    // Blur & Pixelate settings
    var enableBlur by remember { mutableStateOf(false) }
    var blurLeftNorm by remember { mutableStateOf(0.25f) }
    var blurTopNorm by remember { mutableStateOf(0.25f) }
    var blurWidthNorm by remember { mutableStateOf(0.50f) }
    var blurHeightNorm by remember { mutableStateOf(0.30f) }
    var blurRadius by remember { mutableStateOf(12f) }
    var blurType by remember { mutableStateOf(BlurType.GAUSSIAN) }
    var blurShape by remember { mutableStateOf(BlurShape.RECTANGLE) }
    var enableBlurTimeRange by remember { mutableStateOf(false) }
    var blurStartMs by remember { mutableStateOf(0L) }
    var blurEndMs by remember { mutableStateOf(0L) }

    // Live Aspect Ratio for Preview Viewport
    val currentCropRatio = remember(
        cropLeftNorm,
        cropRightNorm,
        cropTopNorm,
        cropBottomNorm,
        videoWidth,
        videoHeight,
        selectedAspectRatio
    ) {
        if (selectedAspectRatio.ratio != null) {
            selectedAspectRatio.ratio!!
        } else if (cropLeftNorm > 0.001f || cropTopNorm > 0.001f || cropRightNorm < 0.999f || cropBottomNorm < 0.999f) {
            val effectiveW = if (videoWidth > 0) videoWidth.toFloat() else 1080f
            val effectiveH = if (videoHeight > 0) videoHeight.toFloat() else 1920f
            val cropW = (cropRightNorm - cropLeftNorm) * effectiveW
            val cropH = (cropBottomNorm - cropTopNorm) * effectiveH
            if (cropH > 0f) cropW / cropH else null
        } else {
            null
        }
    }

    // Processing State
    var isProcessing by remember { mutableStateOf(false) }
    var processingStatusText by remember { mutableStateOf("") }

    // Function to reload video metadata for current working file
    suspend fun reloadWorkingMetadata(path: String) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (path.isNotEmpty() && File(path).exists()) {
                    retriever.setDataSource(path)
                    val durStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val wStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val hStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val rotStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

                    val newDur = durStr?.toLongOrNull() ?: 0L
                    durationMs = newDur
                    var w = wStr?.toIntOrNull() ?: 0
                    var h = hStr?.toIntOrNull() ?: 0
                    val rot = rotStr?.toIntOrNull() ?: 0
                    if (rot == 90 || rot == 270) {
                        val temp = w
                        w = h
                        h = temp
                    }
                    videoWidth = w
                    videoHeight = h

                    startTrimMs = 0L
                    endTrimMs = newDur
                    cutStartMs = 0L
                    cutEndMs = 0L
                    enableCutOut = false
                    speedRatio = 1.0f
                    isAudioMuted = false
                    rotationDegrees = 0
                    lastRotateDirection = null
                    cropLeftNorm = 0f
                    cropTopNorm = 0f
                    cropRightNorm = 1f
                    cropBottomNorm = 1f
                    selectedAspectRatio = StudioAspectRatio.FREE
                    enableBlur = false
                    blurLeftNorm = 0.25f
                    blurTopNorm = 0.25f
                    blurWidthNorm = 0.50f
                    blurHeightNorm = 0.30f
                    blurRadius = 12f
                    blurType = BlurType.GAUSSIAN
                    blurShape = BlurShape.RECTANGLE
                    enableBlurTimeRange = false
                    blurStartMs = 0L
                    blurEndMs = newDur

                    retriever.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(workingFilePath) {
        reloadWorkingMetadata(workingFilePath)
    }

    // Apply Live Preview Volume
    LaunchedEffect(isAudioMuted) {
        mediaPlayerInstance?.let { mp ->
            try {
                val vol = if (isAudioMuted) 0f else 1f
                mp.setVolume(vol, vol)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Debounce and safely apply PlaybackParams speed changes
    @OptIn(FlowPreview::class)
    LaunchedEffect(speedRatio) {
        snapshotFlow { speedRatio }
            .debounce(250L)
            .collectLatest { speed ->
                mediaPlayerInstance?.let { mp ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val wasPlaying = mp.isPlaying
                            if (wasPlaying) {
                                mp.pause()
                            }
                            val p = mp.playbackParams
                            p.speed = speed
                            mp.playbackParams = p

                            // Re-seek to current position to flush and refresh decoder smoothly
                            videoViewInstance?.let { vv ->
                                val curPos = vv.currentPosition
                                vv.seekTo(curPos)
                            }

                            if (wasPlaying) {
                                mp.start()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
    }

    // Playhead position tracking loop using direct VideoView current position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewInstance?.let { vv ->
                if (vv.isPlaying) {
                    val pos = vv.currentPosition.toLong()
                    currentPositionMs = pos.coerceIn(0L, durationMs)

                    // Loop playback within trim bounds
                    if (endTrimMs > startTrimMs && endTrimMs < durationMs && currentPositionMs >= endTrimMs) {
                        vv.seekTo(startTrimMs.toInt())
                        currentPositionMs = startTrimMs
                    }
                    // Skip cut region if enabled
                    if (enableCutOut && cutEndMs > cutStartMs && currentPositionMs in cutStartMs..cutEndMs) {
                        vv.seekTo(cutEndMs.toInt())
                        currentPositionMs = cutEndMs
                    }
                }
            }
            delay(33)
        }
    }

    // Function to apply effect to working video file
    fun applyEffectToVideo(
        effectTitle: String,
        tTrimStart: Long = startTrimMs,
        tTrimEnd: Long = endTrimMs,
        tCutStart: Long = if (enableCutOut) cutStartMs else -1L,
        tCutEnd: Long = if (enableCutOut) cutEndMs else -1L,
        tMuteAudio: Boolean = isAudioMuted,
        tSpeed: Float = speedRatio,
        tRotation: Int = rotationDegrees,
        tCropL: Float = cropLeftNorm,
        tCropT: Float = cropTopNorm,
        tCropR: Float = cropRightNorm,
        tCropB: Float = cropBottomNorm,
        tEnableBlur: Boolean = enableBlur,
        tBlurLeft: Float = blurLeftNorm,
        tBlurTop: Float = blurTopNorm,
        tBlurWidth: Float = blurWidthNorm,
        tBlurHeight: Float = blurHeightNorm,
        tBlurRadius: Float = blurRadius,
        tBlurType: BlurType = blurType,
        tBlurShape: BlurShape = blurShape,
        tBlurStartMs: Long = if (enableBlurTimeRange) blurStartMs else -1L,
        tBlurEndMs: Long = if (enableBlurTimeRange) blurEndMs else -1L
    ) {
        if (isProcessing) return
        isProcessing = true
        processingStatusText = "Applying $effectTitle..."

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val tempOut =
                    File(context.cacheDir, "vr_edit_${System.currentTimeMillis()}.mp4")
                val success = VideoEditUtils.processVideo(
                    context = context,
                    inputPath = workingFilePath,
                    outputPath = tempOut.absolutePath,
                    startMs = tTrimStart,
                    endMs = tTrimEnd,
                    cutStartMs = tCutStart,
                    cutEndMs = tCutEnd,
                    muteAudio = tMuteAudio,
                    speedRatio = tSpeed,
                    rotationDegrees = tRotation,
                    cropLeftNorm = tCropL,
                    cropTopNorm = tCropT,
                    cropRightNorm = tCropR,
                    cropBottomNorm = tCropB,
                    enableBlur = tEnableBlur,
                    blurLeftNorm = tBlurLeft,
                    blurTopNorm = tBlurTop,
                    blurWidthNorm = tBlurWidth,
                    blurHeightNorm = tBlurHeight,
                    blurRadius = tBlurRadius,
                    blurType = tBlurType,
                    blurShape = tBlurShape,
                    blurStartMs = tBlurStartMs,
                    blurEndMs = tBlurEndMs
                )

                withContext(Dispatchers.Main) {
                    isProcessing = false
                    if (success && tempOut.exists() && tempOut.length() > 0) {
                        workingFilePath = tempOut.absolutePath
                        reloadWorkingMetadata(tempOut.absolutePath)
                        speedRatio = 1.0f
                        startTrimMs = 0L
                        endTrimMs = durationMs
                        enableCutOut = false
                        cropLeftNorm = 0f
                        cropTopNorm = 0f
                        cropRightNorm = 1f
                        cropBottomNorm = 1f
                        selectedAspectRatio = StudioAspectRatio.FREE
                        currentPositionMs = 0L
                        videoViewInstance?.seekTo(0)
                        Toast.makeText(
                            context,
                            "✅ $effectTitle Applied to Video!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "❌ Failed to apply $effectTitle. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = StudioBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // ==================== TOP STUDIO TOOLBAR ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioPanelBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Video Studio",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = StudioCyan.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (videoWidth > 0 && videoHeight > 0) "${videoWidth}x${videoHeight}" else "1080P",
                                        color = StudioCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = 4.dp,
                                            vertical = 2.dp
                                        )
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatDuration(effectiveDurationMs),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (speedRatio != 1.0f) {
                                    Surface(
                                        color = StudioCyan.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "⚡ ${speedRatio}x",
                                            color = StudioCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(
                                                horizontal = 4.dp,
                                                vertical = 1.dp
                                            )
                                        )
                                    }
                                    Text(
                                        text = "(Orig: ${FormatUtils.formatDuration(durationMs)})",
                                        color = StudioTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                if (isEditedFromOriginal) {
                                    Text(
                                        text = "• Edited",
                                        color = StudioCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditedFromOriginal) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        workingFilePath = item.filePath
                                        reloadWorkingMetadata(item.filePath)
                                        currentPositionMs = 0L
                                        videoViewInstance?.seekTo(0)
                                        Toast.makeText(
                                            context,
                                            "Reverted to original video",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioRed),
                                border = BorderStroke(1.dp, StudioRed)
                            ) {
                                Icon(
                                    Icons.Default.Undo,
                                    contentDescription = null,
                                    tint = StudioRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Revert",
                                    fontSize = 12.sp,
                                    color = StudioRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isProcessing) return@Button
                                isProcessing = true
                                processingStatusText = "Exporting Video..."

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val timeStamp = SimpleDateFormat(
                                            "yyyyMMdd_HHmmss",
                                            Locale.getDefault()
                                        ).format(Date())
                                        val exportName = "VideoStudio_EDIT_$timeStamp.mp4"
                                        val moviesDir =
                                            File(context.getExternalFilesDir(null), "Recordings")
                                        if (!moviesDir.exists()) moviesDir.mkdirs()

                                        val outputFile = File(moviesDir, exportName)

                                        val success = if (
                                            startTrimMs > 0 || endTrimMs < durationMs || enableCutOut ||
                                            speedRatio != 1.0f || isAudioMuted || rotationDegrees != 0 ||
                                            cropLeftNorm > 0f || cropTopNorm > 0f || cropRightNorm < 1f || cropBottomNorm < 1f ||
                                            enableBlur
                                        ) {
                                            VideoEditUtils.processVideo(
                                                context = context,
                                                inputPath = workingFilePath,
                                                outputPath = outputFile.absolutePath,
                                                startMs = startTrimMs,
                                                endMs = endTrimMs,
                                                cutStartMs = if (enableCutOut) cutStartMs else -1L,
                                                cutEndMs = if (enableCutOut) cutEndMs else -1L,
                                                muteAudio = isAudioMuted,
                                                speedRatio = speedRatio,
                                                rotationDegrees = rotationDegrees,
                                                cropLeftNorm = cropLeftNorm,
                                                cropTopNorm = cropTopNorm,
                                                cropRightNorm = cropRightNorm,
                                                cropBottomNorm = cropBottomNorm,
                                                enableBlur = enableBlur,
                                                blurLeftNorm = blurLeftNorm,
                                                blurTopNorm = blurTopNorm,
                                                blurWidthNorm = blurWidthNorm,
                                                blurHeightNorm = blurHeightNorm,
                                                blurRadius = blurRadius,
                                                blurType = blurType,
                                                blurShape = blurShape,
                                                blurStartMs = if (enableBlurTimeRange) blurStartMs else -1L,
                                                blurEndMs = if (enableBlurTimeRange) blurEndMs else -1L
                                            )
                                        } else {
                                            File(workingFilePath).copyTo(
                                                outputFile,
                                                overwrite = true
                                            )
                                            true
                                        }

                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            if (success && outputFile.exists() && outputFile.length() > 0) {
                                                val retriever = MediaMetadataRetriever()
                                                retriever.setDataSource(outputFile.absolutePath)
                                                val finalDur =
                                                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                                        ?.toLongOrNull() ?: effectiveDurationMs
                                                retriever.release()

                                                val newItem = RecordItem(
                                                    title = "Edited ${item.title}",
                                                    filePath = outputFile.absolutePath,
                                                    uriString = Uri.fromFile(outputFile).toString(),
                                                    mediaType = MediaType.VIDEO,
                                                    timestamp = System.currentTimeMillis(),
                                                    sizeBytes = outputFile.length(),
                                                    durationMs = finalDur.coerceAtLeast(1000L)
                                                )
                                                onSaveSuccess(newItem)
                                                Toast.makeText(
                                                    context,
                                                    "🎉 Video Exported & Saved to Library!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onDismiss()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Export failed. Please try again.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            Toast.makeText(
                                                context,
                                                "Export error: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isProcessing,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("videostudio_export_button")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Export",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // ==================== MAIN VIEWPORT PLAYER CANVAS ====================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .border(1.dp, StudioCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val videoAspectRatio = remember(videoWidth, videoHeight) {
                        if (videoWidth > 0 && videoHeight > 0) {
                            videoWidth.toFloat() / videoHeight.toFloat()
                        } else {
                            9f / 16f
                        }
                    }

                    val previewBoxModifier = Modifier
                        .aspectRatio(videoAspectRatio)
                        .clip(RoundedCornerShape(12.dp))

                    Box(
                        modifier = previewBoxModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        key(workingFilePath) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val uri = Uri.fromFile(File(workingFilePath))
                                        setVideoURI(uri)
                                        setOnPreparedListener { mp ->
                                            mediaPlayerInstance = mp
                                            if (videoWidth <= 0 || videoHeight <= 0) {
                                                if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                                    videoWidth = mp.videoWidth
                                                    videoHeight = mp.videoHeight
                                                }
                                            }
                                            mp.isLooping = true
                                            try {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    val p = mp.playbackParams
                                                    p.speed = speedRatio
                                                    mp.playbackParams = p
                                                }
                                                val vol = if (isAudioMuted) 0f else 1f
                                                mp.setVolume(vol, vol)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            mp.start()
                                            isPlaying = true
                                        }
                                        videoViewInstance = this
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Crop Grid Overlay with Drag-to-Resize Handles
                        if (activeTab == StudioEditorTab.CROP) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val w = size.width.toFloat()
                                                val h = size.height.toFloat()
                                                if (w > 0 && h > 0) {
                                                    val leftPx = cropLeftNorm * w
                                                    val topPx = cropTopNorm * h
                                                    val rightPx = cropRightNorm * w
                                                    val bottomPx = cropBottomNorm * h

                                                    val handleRadius = 60f // Touch hit threshold

                                                    val distTL = hypot(
                                                        offset.x - leftPx,
                                                        offset.y - topPx
                                                    )
                                                    val distTR = hypot(
                                                        offset.x - rightPx,
                                                        offset.y - topPx
                                                    )
                                                    val distBL = hypot(
                                                        offset.x - leftPx,
                                                        offset.y - bottomPx
                                                    )
                                                    val distBR = hypot(
                                                        offset.x - rightPx,
                                                        offset.y - bottomPx
                                                    )

                                                    val midX = (leftPx + rightPx) / 2f
                                                    val midY = (topPx + bottomPx) / 2f
                                                    val distL = hypot(
                                                        offset.x - leftPx,
                                                        offset.y - midY
                                                    )
                                                    val distT = hypot(
                                                        offset.x - midX,
                                                        offset.y - topPx
                                                    )
                                                    val distR = hypot(
                                                        offset.x - rightPx,
                                                        offset.y - midY
                                                    )
                                                    val distB = hypot(
                                                        offset.x - midX,
                                                        offset.y - bottomPx
                                                    )

                                                    activeCropHandle = when {
                                                        distTL < handleRadius -> 1 // TOP_LEFT
                                                        distTR < handleRadius -> 2 // TOP_RIGHT
                                                        distBL < handleRadius -> 3 // BOTTOM_LEFT
                                                        distBR < handleRadius -> 4 // BOTTOM_RIGHT
                                                        distL < handleRadius -> 5 // LEFT_EDGE
                                                        distT < handleRadius -> 6 // TOP_EDGE
                                                        distR < handleRadius -> 7 // RIGHT_EDGE
                                                        distB < handleRadius -> 8 // BOTTOM_EDGE
                                                        offset.x in leftPx..rightPx && offset.y in topPx..bottomPx -> 9 // MOVE BOX
                                                        else -> 0
                                                    }
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val w = size.width.toFloat()
                                                val h = size.height.toFloat()
                                                if (w > 0 && h > 0) {
                                                    val deltaX = dragAmount.x / w
                                                    val deltaY = dragAmount.y / h
                                                    val minSizeNorm = 0.08f

                                                    when (activeCropHandle) {
                                                        1 -> { // TOP_LEFT
                                                            cropLeftNorm =
                                                                (cropLeftNorm + deltaX).coerceIn(
                                                                    0f,
                                                                    cropRightNorm - minSizeNorm
                                                                )
                                                            cropTopNorm =
                                                                (cropTopNorm + deltaY).coerceIn(
                                                                    0f,
                                                                    cropBottomNorm - minSizeNorm
                                                                )
                                                            selectedAspectRatio =
                                                                StudioAspectRatio.FREE
                                                        }

                                                        2 -> { // TOP_RIGHT
                                                            cropRightNorm =
                                                                (cropRightNorm + deltaX).coerceIn(
                                                                    cropLeftNorm + minSizeNorm,
                                                                    1f
                                                                )
                                                            cropTopNorm =
                                                                (cropTopNorm + deltaY).coerceIn(
                                                                    0f,
                                                                    cropBottomNorm - minSizeNorm
                                                                )
                                                            selectedAspectRatio =
                                                                StudioAspectRatio.FREE
                                                        }

                                                        3 -> { // BOTTOM_LEFT
                                                            cropLeftNorm =
                                                                (cropLeftNorm + deltaX).coerceIn(
                                                                    0f,
                                                                    cropRightNorm - minSizeNorm
                                                                )
                                                            cropBottomNorm =
                                                                (cropBottomNorm + deltaY).coerceIn(
                                                                    cropTopNorm + minSizeNorm,
                                                                    1f
                                                                )
                                                            selectedAspectRatio =
                                                                StudioAspectRatio.FREE
                                                        }

                                                        4 -> { // BOTTOM_RIGHT
                                                            cropRightNorm =
                                                                (cropRightNorm + deltaX).coerceIn(
                                                                    cropLeftNorm + minSizeNorm,
                                                                    1f
                                                                )
                                                            cropBottomNorm =
                                                                (cropBottomNorm + deltaY).coerceIn(
                                                                    cropTopNorm + minSizeNorm,
                                                                    1f
                                                                )
                                                            selectedAspectRatio =
                                                                StudioAspectRatio.FREE
                                                        }

                                                        5 -> { // LEFT_EDGE
                                                            cropLeftNorm =
                                                                (cropLeftNorm + deltaX).coerceIn(
                                                                    0f,
                                                                    cropRightNorm - minSizeNorm
                                                                )
                                                            selectedAspectRatio =
                                                                StudioAspectRatio.FREE
                                                        }

                                                        6 -> { // TOP_EDGE
                                                            cropTopNorm =
                                                                (cropTopNorm + deltaY).coerceIn(
                                                                    0f,
                                                                    cropBottomNorm - minSizeNorm
                                                                )
                                                            selectedAspectRatio =
                                                                StudioAspectRatio.FREE
                                                        }

                                                        7 -> { // RIGHT_EDGE
                                                            cropRightNorm =
                                                                (cropRightNorm + deltaX).coerceIn(
                                                                    cropLeftNorm + minSizeNorm,
                                                                    1f
                                                                )
                                                            selectedAspectRatio =
                                                                StudioAspectRatio.FREE
                                                        }

                                                        8 -> { // BOTTOM_EDGE
                                                            cropBottomNorm =
                                                                (cropBottomNorm + deltaY).coerceIn(
                                                                    cropTopNorm + minSizeNorm,
                                                                    1f
                                                                )
                                                            selectedAspectRatio =
                                                                StudioAspectRatio.FREE
                                                        }

                                                        9 -> { // MOVE WHOLE BOX
                                                            val cropW = cropRightNorm - cropLeftNorm
                                                            val cropH = cropBottomNorm - cropTopNorm
                                                            val newLeft =
                                                                (cropLeftNorm + deltaX).coerceIn(
                                                                    0f,
                                                                    1f - cropW
                                                                )
                                                            val newTop =
                                                                (cropTopNorm + deltaY).coerceIn(
                                                                    0f,
                                                                    1f - cropH
                                                                )
                                                            cropLeftNorm = newLeft
                                                            cropTopNorm = newTop
                                                            cropRightNorm = newLeft + cropW
                                                            cropBottomNorm = newTop + cropH
                                                        }
                                                    }
                                                }
                                            },
                                            onDragEnd = { activeCropHandle = 0 },
                                            onDragCancel = { activeCropHandle = 0 }
                                        )
                                    }
                            ) {
                                val w = size.width
                                val h = size.height

                                val leftPx = cropLeftNorm * w
                                val topPx = cropTopNorm * h
                                val rightPx = cropRightNorm * w
                                val bottomPx = cropBottomNorm * h
                                val cropW = rightPx - leftPx
                                val cropH = bottomPx - topPx

                                val dimColor = Color.Black.copy(alpha = 0.65f)
                                if (topPx > 0f) drawRect(
                                    color = dimColor,
                                    topLeft = Offset(0f, 0f),
                                    size = Size(w, topPx)
                                )
                                if (h - bottomPx > 0f) drawRect(
                                    color = dimColor,
                                    topLeft = Offset(0f, bottomPx),
                                    size = Size(w, h - bottomPx)
                                )
                                if (leftPx > 0f && cropH > 0f) drawRect(
                                    color = dimColor,
                                    topLeft = Offset(0f, topPx),
                                    size = Size(leftPx, cropH)
                                )
                                if (w - rightPx > 0f && cropH > 0f) drawRect(
                                    color = dimColor,
                                    topLeft = Offset(rightPx, topPx),
                                    size = Size(w - rightPx, cropH)
                                )

                                drawRect(
                                    color = StudioCyan,
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(cropW, cropH),
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                val thirdW = cropW / 3f
                                val thirdH = cropH / 3f
                                val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)

                                drawLine(
                                    StudioCyan.copy(alpha = 0.6f),
                                    Offset(leftPx + thirdW, topPx),
                                    Offset(leftPx + thirdW, bottomPx),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dash
                                )
                                drawLine(
                                    StudioCyan.copy(alpha = 0.6f),
                                    Offset(leftPx + thirdW * 2, topPx),
                                    Offset(leftPx + thirdW * 2, bottomPx),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dash
                                )
                                drawLine(
                                    StudioCyan.copy(alpha = 0.6f),
                                    Offset(leftPx, topPx + thirdH),
                                    Offset(rightPx, topPx + thirdH),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dash
                                )
                                drawLine(
                                    StudioCyan.copy(alpha = 0.6f),
                                    Offset(leftPx, topPx + thirdH * 2),
                                    Offset(rightPx, topPx + thirdH * 2),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dash
                                )

                                // Corner L-Bracket Accents
                                val bracketLen = 16.dp.toPx()
                                val bracketStroke = 4.dp.toPx()

                                // TL
                                drawLine(
                                    StudioCyan,
                                    Offset(leftPx, topPx),
                                    Offset(leftPx + bracketLen, topPx),
                                    strokeWidth = bracketStroke
                                )
                                drawLine(
                                    StudioCyan,
                                    Offset(leftPx, topPx),
                                    Offset(leftPx, topPx + bracketLen),
                                    strokeWidth = bracketStroke
                                )
                                // TR
                                drawLine(
                                    StudioCyan,
                                    Offset(rightPx, topPx),
                                    Offset(rightPx - bracketLen, topPx),
                                    strokeWidth = bracketStroke
                                )
                                drawLine(
                                    StudioCyan,
                                    Offset(rightPx, topPx),
                                    Offset(rightPx, topPx + bracketLen),
                                    strokeWidth = bracketStroke
                                )
                                // BL
                                drawLine(
                                    StudioCyan,
                                    Offset(leftPx, bottomPx),
                                    Offset(leftPx + bracketLen, bottomPx),
                                    strokeWidth = bracketStroke
                                )
                                drawLine(
                                    StudioCyan,
                                    Offset(leftPx, bottomPx),
                                    Offset(leftPx, bottomPx - bracketLen),
                                    strokeWidth = bracketStroke
                                )
                                // BR
                                drawLine(
                                    StudioCyan,
                                    Offset(rightPx, bottomPx),
                                    Offset(rightPx - bracketLen, bottomPx),
                                    strokeWidth = bracketStroke
                                )
                                drawLine(
                                    StudioCyan,
                                    Offset(rightPx, bottomPx),
                                    Offset(rightPx, bottomPx - bracketLen),
                                    strokeWidth = bracketStroke
                                )

                                // Touch handles at corners and edges
                                val handleRadiusPx = 7.dp.toPx()
                                val midX = (leftPx + rightPx) / 2f
                                val midY = (topPx + bottomPx) / 2f

                                val handlePoints = listOf(
                                    Offset(leftPx, topPx),
                                    Offset(rightPx, topPx),
                                    Offset(leftPx, bottomPx),
                                    Offset(rightPx, bottomPx),
                                    Offset(leftPx, midY),
                                    Offset(rightPx, midY),
                                    Offset(midX, topPx),
                                    Offset(midX, bottomPx)
                                )

                                handlePoints.forEach { pt ->
                                    drawCircle(
                                        color = Color.White,
                                        radius = handleRadiusPx,
                                        center = pt
                                    )
                                    drawCircle(
                                        color = StudioCyan,
                                        radius = handleRadiusPx,
                                        center = pt,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }

                        // Blur Region Canvas Overlay
                        val isBlurActiveNow = remember(
                            enableBlur,
                            enableBlurTimeRange,
                            blurStartMs,
                            blurEndMs,
                            currentPositionMs
                        ) {
                            if (!enableBlur) false
                            else if (!enableBlurTimeRange) true
                            else currentPositionMs in blurStartMs..blurEndMs
                        }

                        if (activeTab == StudioEditorTab.BLUR || (enableBlur && isBlurActiveNow)) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { centroid, pan, _, _ ->
                                            val widthPx = size.width.toFloat()
                                            val heightPx = size.height.toFloat()
                                            if (widthPx > 0 && heightPx > 0) {
                                                val leftPx = blurLeftNorm * widthPx
                                                val topPx = blurTopNorm * heightPx
                                                val blurW = blurWidthNorm * widthPx
                                                val blurH = blurHeightNorm * heightPx
                                                val cornerX = leftPx + blurW
                                                val cornerY = topPx + blurH

                                                // Check if drag gesture is near bottom-right handle
                                                val distToCorner = hypot(
                                                    centroid.x - cornerX,
                                                    centroid.y - cornerY
                                                )
                                                if (distToCorner < 120f) {
                                                    // Resize
                                                    blurWidthNorm =
                                                        (blurWidthNorm + pan.x / widthPx).coerceIn(
                                                            0.1f,
                                                            (1f - blurLeftNorm).coerceAtLeast(0.1f)
                                                        )
                                                    blurHeightNorm =
                                                        (blurHeightNorm + pan.y / heightPx).coerceIn(
                                                            0.1f,
                                                            (1f - blurTopNorm).coerceAtLeast(0.1f)
                                                        )
                                                } else {
                                                    // Move box
                                                    val deltaX = pan.x / widthPx
                                                    val deltaY = pan.y / heightPx
                                                    blurLeftNorm = (blurLeftNorm + deltaX).coerceIn(
                                                        0f,
                                                        (1f - blurWidthNorm).coerceAtLeast(0f)
                                                    )
                                                    blurTopNorm = (blurTopNorm + deltaY).coerceIn(
                                                        0f,
                                                        (1f - blurHeightNorm).coerceAtLeast(0f)
                                                    )
                                                }
                                                if (!enableBlur) enableBlur = true
                                            }
                                        }
                                    }
                            ) {
                                val w = size.width
                                val h = size.height

                                val leftPx = blurLeftNorm * w
                                val topPx = blurTopNorm * h
                                val blurW = blurWidthNorm * w
                                val blurH = blurHeightNorm * h

                                val blurColor =
                                    if (blurType == BlurType.GAUSSIAN) StudioCyan else StudioIndigo

                                if (isBlurActiveNow) {
                                    if (blurShape == BlurShape.RECTANGLE) {
                                        if (blurType == BlurType.PIXELATE) {
                                            // Draw Mosaic Pixel Tiles to completely obscure preview content
                                            val tileSize = 24f
                                            val cols = (blurW / tileSize).toInt().coerceAtLeast(1)
                                            val rows = (blurH / tileSize).toInt().coerceAtLeast(1)
                                            val actualW = blurW / cols
                                            val actualH = blurH / rows
                                            for (r in 0 until rows) {
                                                for (c in 0 until cols) {
                                                    val tileX = leftPx + c * actualW
                                                    val tileY = topPx + r * actualH
                                                    val colorIdx = (r * 7 + c * 13) % 4
                                                    val tileColor = when (colorIdx) {
                                                        0 -> Color(0xFF2B2D3A)
                                                        1 -> Color(0xFF3F4257)
                                                        2 -> Color(0xFF1E202B)
                                                        else -> Color(0xFF4A4E69)
                                                    }
                                                    drawRect(
                                                        color = tileColor,
                                                        topLeft = Offset(tileX, tileY),
                                                        size = Size(actualW - 0.5f, actualH - 0.5f)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Frosted Glass Censor Cover
                                            drawRect(
                                                color = Color(0xED1A1C23),
                                                topLeft = Offset(leftPx, topPx),
                                                size = Size(blurW, blurH)
                                            )
                                            var yStep = 8f
                                            while (yStep < blurH) {
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.12f),
                                                    start = Offset(leftPx, topPx + yStep),
                                                    end = Offset(leftPx + blurW, topPx + yStep),
                                                    strokeWidth = 1f
                                                )
                                                yStep += 8f
                                            }
                                        }

                                        // Outer Selection Border
                                        drawRect(
                                            color = blurColor,
                                            topLeft = Offset(leftPx, topPx),
                                            size = Size(blurW, blurH),
                                            style = Stroke(width = 3.dp.toPx())
                                        )

                                        // Bottom-Right Corner Resize Handle
                                        val handleRadius = 10.dp.toPx()
                                        val cornerCenter = Offset(leftPx + blurW, topPx + blurH)
                                        drawCircle(
                                            color = blurColor,
                                            radius = handleRadius,
                                            center = cornerCenter
                                        )
                                        drawCircle(
                                            color = Color.White,
                                            radius = handleRadius * 0.5f,
                                            center = cornerCenter
                                        )
                                    } else {
                                        // Oval Shape
                                        val path = Path().apply {
                                            addOval(Rect(Offset(leftPx, topPx), Size(blurW, blurH)))
                                        }
                                        clipPath(path) {
                                            if (blurType == BlurType.PIXELATE) {
                                                val tileSize = 24f
                                                val cols =
                                                    (blurW / tileSize).toInt().coerceAtLeast(1)
                                                val rows =
                                                    (blurH / tileSize).toInt().coerceAtLeast(1)
                                                val actualW = blurW / cols
                                                val actualH = blurH / rows
                                                for (r in 0 until rows) {
                                                    for (c in 0 until cols) {
                                                        val tileX = leftPx + c * actualW
                                                        val tileY = topPx + r * actualH
                                                        val colorIdx = (r * 7 + c * 13) % 4
                                                        val tileColor = when (colorIdx) {
                                                            0 -> Color(0xFF2B2D3A)
                                                            1 -> Color(0xFF3F4257)
                                                            2 -> Color(0xFF1E202B)
                                                            else -> Color(0xFF4A4E69)
                                                        }
                                                        drawRect(
                                                            color = tileColor,
                                                            topLeft = Offset(tileX, tileY),
                                                            size = Size(
                                                                actualW - 0.5f,
                                                                actualH - 0.5f
                                                            )
                                                        )
                                                    }
                                                }
                                            } else {
                                                drawRect(
                                                    color = Color(0xED1A1C23),
                                                    topLeft = Offset(leftPx, topPx),
                                                    size = Size(blurW, blurH)
                                                )
                                            }
                                        }

                                        drawOval(
                                            color = blurColor,
                                            topLeft = Offset(leftPx, topPx),
                                            size = Size(blurW, blurH),
                                            style = Stroke(width = 3.dp.toPx())
                                        )

                                        val handleRadius = 10.dp.toPx()
                                        val cornerCenter = Offset(leftPx + blurW, topPx + blurH)
                                        drawCircle(
                                            color = blurColor,
                                            radius = handleRadius,
                                            center = cornerCenter
                                        )
                                        drawCircle(
                                            color = Color.White,
                                            radius = handleRadius * 0.5f,
                                            center = cornerCenter
                                        )
                                    }
                                } else if (activeTab == StudioEditorTab.BLUR) {
                                    val dashEffect =
                                        PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                    if (blurShape == BlurShape.RECTANGLE) {
                                        drawRect(
                                            color = StudioCyan.copy(alpha = 0.6f),
                                            topLeft = Offset(leftPx, topPx),
                                            size = Size(blurW, blurH),
                                            style = Stroke(
                                                width = 2.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                        )
                                    } else {
                                        drawOval(
                                            color = StudioCyan.copy(alpha = 0.6f),
                                            topLeft = Offset(leftPx, topPx),
                                            size = Size(blurW, blurH),
                                            style = Stroke(
                                                width = 2.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                        )
                                    }

                                    val handleRadius = 10.dp.toPx()
                                    val cornerCenter = Offset(leftPx + blurW, topPx + blurH)
                                    drawCircle(
                                        color = StudioCyan.copy(alpha = 0.5f),
                                        radius = handleRadius,
                                        center = cornerCenter
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.7f),
                                        radius = handleRadius * 0.5f,
                                        center = cornerCenter
                                    )
                                }
                            }
                        }
                    }

                    // Processing HUD Overlay
                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(color = StudioCyan, strokeWidth = 3.dp)
                                Text(
                                    text = processingStatusText,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Video Studio engine processing video smoothly...",
                                    color = StudioTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Floating Play / Pause Control
                    if (!isProcessing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = CircleShape,
                                modifier = Modifier.clickable {
                                    videoViewInstance?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp,
                                        vertical = 8.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = StudioCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "${FormatUtils.formatDuration(effectivePositionMs)} / ${
                                            FormatUtils.formatDuration(
                                                effectiveDurationMs
                                            )
                                        }",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== Video Studio MULTI-TRACK TIMELINE ENGINE ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioPanelBg)
                        .padding(10.dp)
                ) {
                    // Timeline Top Bar with Split Clip Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                tint = StudioCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Timeline (${FormatUtils.formatDuration(effectiveDurationMs)})",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Timeline Quick Split Actions
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    if (currentPositionMs > 0 && currentPositionMs < durationMs) {
                                        applyEffectToVideo(
                                            effectTitle = "Split & Keep Left",
                                            tTrimStart = 0L,
                                            tTrimEnd = currentPositionMs
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Move playhead to split position first",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioIndigo),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCut,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "Keep Left",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (currentPositionMs > 0 && currentPositionMs < durationMs) {
                                        applyEffectToVideo(
                                            effectTitle = "Split & Keep Right",
                                            tTrimStart = currentPositionMs,
                                            tTrimEnd = durationMs
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Move playhead to split position first",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioIndigo),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCut,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "Keep Right",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Interactive Timeline Track Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioCardBg)
                            .pointerInput(durationMs) {
                                detectTapGestures { offset ->
                                    val trackW = size.width.toFloat()
                                    if (trackW > 0 && durationMs > 0) {
                                        val fraction = (offset.x / trackW).coerceIn(0f, 1f)
                                        val targetMs = (fraction * durationMs).toLong()
                                        currentPositionMs = targetMs
                                        videoViewInstance?.seekTo(targetMs.toInt())
                                    }
                                }
                            }
                            .pointerInput(durationMs) {
                                detectDragGestures { change, _ ->
                                    val trackW = size.width.toFloat()
                                    if (trackW > 0 && durationMs > 0) {
                                        val fraction = (change.position.x / trackW).coerceIn(0f, 1f)
                                        val targetMs = (fraction * durationMs).toLong()
                                        currentPositionMs = targetMs
                                        videoViewInstance?.seekTo(targetMs.toInt())
                                    }
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Draw time ruler ticks
                            val ticksCount = 10
                            for (i in 0..ticksCount) {
                                val x = (i.toFloat() / ticksCount) * w
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.4f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, 12f),
                                    strokeWidth = 2f
                                )
                            }

                            // Draw video track block
                            val trimL =
                                if (durationMs > 0) (startTrimMs.toFloat() / durationMs) * w else 0f
                            val trimR =
                                if (durationMs > 0) (endTrimMs.toFloat() / durationMs) * w else w
                            val trackBoxW = (trimR - trimL).coerceAtLeast(10f)

                            drawRect(
                                color = StudioIndigo.copy(alpha = 0.4f),
                                topLeft = Offset(trimL, 16f),
                                size = Size(trackBoxW, h - 20f)
                            )
                            drawRect(
                                color = StudioCyan,
                                topLeft = Offset(trimL, 16f),
                                size = Size(trackBoxW, h - 20f),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Cut out section if enabled
                            if (enableCutOut && cutEndMs > cutStartMs && durationMs > 0) {
                                val cutL = (cutStartMs.toFloat() / durationMs) * w
                                val cutR = (cutEndMs.toFloat() / durationMs) * w
                                drawRect(
                                    color = StudioRed.copy(alpha = 0.6f),
                                    topLeft = Offset(cutL, 16f),
                                    size = Size(cutR - cutL, h - 20f)
                                )
                            }

                            // Video Studio Playhead Line (Red Needle with top knob)
                            val playheadX =
                                if (durationMs > 0) (currentPositionMs.toFloat() / durationMs) * w else 0f
                            drawLine(
                                color = StudioRed,
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, h),
                                strokeWidth = 3.dp.toPx()
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 6.dp.toPx(),
                                center = Offset(playheadX, 6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ==================== Video Studio BOTTOM TOOL TABS ROW ====================
                ScrollableTabRow(
                    selectedTabIndex = activeTab.ordinal,
                    edgePadding = 12.dp,
                    divider = {},
                    containerColor = StudioBg,
                    indicator = {}
                ) {
                    StudioEditorTab.values().forEach { tab ->
                        val selected = activeTab == tab
                        Tab(
                            selected = selected,
                            onClick = { activeTab = tab },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) StudioCyan else StudioPanelBg)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (selected) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = tab.title,
                                    color = if (selected) Color.Black else Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ==================== TOOL PARAMETERS & REAL-TIME APPLY PANEL ====================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(StudioPanelBg)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (activeTab) {
                            StudioEditorTab.TRIM_SPLIT -> {
                                Text(
                                    "⚡ Quick Trim & Split at Playhead (${
                                        FormatUtils.formatDuration(
                                            effectivePositionMs
                                        )
                                    })",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                // Quick Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (currentPositionMs > 0 && currentPositionMs < durationMs) {
                                                applyEffectToVideo(
                                                    effectTitle = "Trim Left",
                                                    tTrimStart = currentPositionMs,
                                                    tTrimEnd = durationMs
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Move playhead first to trim left",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = StudioIndigo),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            "✂️ Trim Left",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (currentPositionMs > 0 && currentPositionMs < durationMs) {
                                                applyEffectToVideo(
                                                    effectTitle = "Trim Right",
                                                    tTrimStart = 0L,
                                                    tTrimEnd = currentPositionMs
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Move playhead first to trim right",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = StudioIndigo),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            "✂️ Trim Right",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                Text(
                                    "✂️ Custom Range Trim",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Start: ${FormatUtils.formatDuration((startTrimMs / speedRatio).toLong())}",
                                        color = StudioCyan,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "End: ${FormatUtils.formatDuration((endTrimMs / speedRatio).toLong())}",
                                        color = StudioCyan,
                                        fontSize = 12.sp
                                    )
                                }

                                RangeSlider(
                                    value = startTrimMs.toFloat()..endTrimMs.toFloat()
                                        .coerceAtLeast(startTrimMs.toFloat() + 1f),
                                    onValueChange = { range ->
                                        val newS = range.start.toLong()
                                        val newE = range.endInclusive.toLong()
                                        if (newS != startTrimMs) {
                                            videoViewInstance?.seekTo(newS.toInt())
                                            currentPositionMs = newS
                                        } else if (newE != endTrimMs) {
                                            videoViewInstance?.seekTo(newE.toInt())
                                            currentPositionMs = newE
                                        }
                                        startTrimMs = newS
                                        endTrimMs = newE
                                    },
                                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = StudioCyan,
                                        activeTrackColor = StudioCyan,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { startTrimMs = currentPositionMs },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            "Set Start at Playhead",
                                            fontSize = 11.sp,
                                            color = StudioCyan
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { endTrimMs = currentPositionMs },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            "Set End at Playhead",
                                            fontSize = 11.sp,
                                            color = StudioCyan
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Cut Out Middle Segment",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "Delete selected center chunk",
                                            color = StudioTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Switch(
                                        checked = enableCutOut,
                                        onCheckedChange = {
                                            enableCutOut = it
                                            if (it && cutEndMs <= cutStartMs) {
                                                cutStartMs = (durationMs * 0.25f).toLong()
                                                cutEndMs = (durationMs * 0.75f).toLong()
                                            }
                                        },
                                        //colors = SwitchDefaults.colors(checkedThumbColor = VideoStudioRed)
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = StudioRed,
                                            checkedTrackColor = BentoPrimary,
                                            checkedBorderColor = BentoPrimary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(
                                                alpha = 0.25f
                                            ),
                                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.4f
                                            )
                                        )
                                    )
                                }

                                if (enableCutOut) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { cutStartMs = currentPositionMs },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                "Set Cut Start",
                                                fontSize = 11.sp,
                                                color = StudioRed
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { cutEndMs = currentPositionMs },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Set Cut End", fontSize = 11.sp, color = StudioRed)
                                        }
                                    }

                                    RangeSlider(
                                        value = cutStartMs.toFloat()..cutEndMs.toFloat()
                                            .coerceAtLeast(cutStartMs.toFloat() + 1f),
                                        onValueChange = { range ->
                                            cutStartMs = range.start.toLong()
                                            cutEndMs = range.endInclusive.toLong()
                                        },
                                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = StudioRed,
                                            activeTrackColor = StudioRed,
                                            inactiveTrackColor = Color.DarkGray
                                        )
                                    )
                                }

                                Button(
                                    onClick = { applyEffectToVideo("Trim & Cut") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isProcessing && (startTrimMs > 0 || endTrimMs < durationMs || (enableCutOut && cutEndMs > cutStartMs)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.ContentCut,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "⚡ Apply Trim & Cut to Video",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            StudioEditorTab.SPEED -> {
                                Text(
                                    "⚡ Playback Speed Control",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Surface(
                                    color = StudioCardBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Selected Speed Multiplier:",
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                "${speedRatio}x",
                                                color = StudioCyan,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Output Duration:",
                                                color = StudioTextSecondary,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "${
                                                    FormatUtils.formatDuration(
                                                        effectiveDurationMs
                                                    )
                                                }  (Original: ${
                                                    FormatUtils.formatDuration(
                                                        durationMs
                                                    )
                                                })",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Text(
                                    "Preset Speed Choices:",
                                    color = StudioTextSecondary,
                                    fontSize = 12.sp
                                )

                                val speeds =
                                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        speeds.take(4).forEach { spd ->
                                            val isSel = speedRatio == spd
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSel) StudioCyan else StudioCardBg)
                                                    .clickable {
                                                        speedRatio = spd
                                                        Toast.makeText(
                                                            context,
                                                            "Previewing at ${spd}x speed",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    if (spd == 1.0f) "1.0x Normal" else "${spd}x",
                                                    color = if (isSel) Color.Black else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        speeds.drop(4).forEach { spd ->
                                            val isSel = speedRatio == spd
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSel) StudioCyan else StudioCardBg)
                                                    .clickable {
                                                        speedRatio = spd
                                                        Toast.makeText(
                                                            context,
                                                            "Previewing at ${spd}x speed",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "${spd}x",
                                                    color = if (isSel) Color.Black else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    "Fine-Tune Speed Slider:",
                                    color = StudioTextSecondary,
                                    fontSize = 12.sp
                                )
                                Slider(
                                    value = speedRatio,
                                    onValueChange = {
                                        speedRatio =
                                            (Math.round(it * 20) / 20.0f).coerceIn(0.25f, 3.0f)
                                    },
                                    valueRange = 0.25f..3.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = StudioCyan,
                                        activeTrackColor = StudioCyan,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )

                                Surface(
                                    color = StudioCyan.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        StudioCyan.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = StudioCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Speed is live previewing in the player above. Click 'Apply Speed to Video' to render this speed permanently into the video.",
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = { applyEffectToVideo("Speed ${speedRatio}x") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isProcessing && speedRatio != 1.0f,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "⚡ Apply Speed (${speedRatio}x) to Video",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            StudioEditorTab.CROP -> {
                                Text(
                                    "📐 Spatial Crop & Framing Presets",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Select a preset ratio or drag handles on the video frame:",
                                    color = StudioTextSecondary,
                                    fontSize = 12.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StudioAspectRatio.values().forEach { preset ->
                                        val isSel = selectedAspectRatio == preset
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) StudioCyan else StudioCardBg)
                                                .clickable {
                                                    selectedAspectRatio = preset
                                                    val targetRatio = preset.ratio
                                                    if (targetRatio == null) {
                                                        cropLeftNorm = 0f
                                                        cropTopNorm = 0f
                                                        cropRightNorm = 1f
                                                        cropBottomNorm = 1f
                                                    } else {
                                                        val effW =
                                                            if (videoWidth > 0) videoWidth.toFloat() else 1080f
                                                        val effH =
                                                            if (videoHeight > 0) videoHeight.toFloat() else 1920f
                                                        val videoRatio = effW / effH
                                                        if (targetRatio > videoRatio) {
                                                            val normH =
                                                                (videoRatio / targetRatio).coerceAtMost(
                                                                    1f
                                                                )
                                                            cropLeftNorm = 0f
                                                            cropRightNorm = 1f
                                                            cropTopNorm =
                                                                ((1f - normH) / 2f).coerceAtLeast(0f)
                                                            cropBottomNorm = cropTopNorm + normH
                                                        } else {
                                                            val normW =
                                                                (targetRatio / videoRatio).coerceAtMost(
                                                                    1f
                                                                )
                                                            cropTopNorm = 0f
                                                            cropBottomNorm = 1f
                                                            cropLeftNorm =
                                                                ((1f - normW) / 2f).coerceAtLeast(0f)
                                                            cropRightNorm = cropLeftNorm + normW
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                preset.label,
                                                color = if (isSel) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                preset.ratioText,
                                                color = if (isSel) Color.DarkGray else Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = { applyEffectToVideo("Crop") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isProcessing && (selectedAspectRatio != StudioAspectRatio.FREE || cropLeftNorm > 0.001f || cropTopNorm > 0.001f || cropRightNorm < 0.999f || cropBottomNorm < 0.999f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Crop,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "⚡ Apply Crop to Video",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            StudioEditorTab.ROTATE -> {
                                Text(
                                    "🔄 Rotation & Orientation",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                val isLeftSelected = lastRotateDirection == "LEFT"
                                val isRightSelected = lastRotateDirection == "RIGHT"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            rotationDegrees = (rotationDegrees + 270) % 360
                                            lastRotateDirection = "LEFT"
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isLeftSelected) StudioCyan else StudioCardBg,
                                            contentColor = if (isLeftSelected) Color.Black else Color.White
                                        ),
                                        border = if (isLeftSelected) null else BorderStroke(
                                            1.dp,
                                            StudioCyan.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.RotateLeft,
                                            contentDescription = null,
                                            tint = if (isLeftSelected) Color.Black else StudioCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Rotate Left",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            rotationDegrees = (rotationDegrees + 90) % 360
                                            lastRotateDirection = "RIGHT"
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isRightSelected) StudioCyan else StudioCardBg,
                                            contentColor = if (isRightSelected) Color.Black else Color.White
                                        ),
                                        border = if (isRightSelected) null else BorderStroke(
                                            1.dp,
                                            StudioCyan.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.RotateRight,
                                            contentDescription = null,
                                            tint = if (isRightSelected) Color.Black else StudioCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Rotate Right",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Button(
                                    onClick = { applyEffectToVideo("Rotation ${rotationDegrees}°") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isProcessing && rotationDegrees != 0,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        if (isLeftSelected) Icons.AutoMirrored.Filled.RotateLeft else Icons.Default.RotateRight,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "⚡ Apply Rotation to Video",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            StudioEditorTab.AUDIO -> {
                                Text(
                                    "🔊 Audio & Volume Track",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(StudioCardBg)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = if (isAudioMuted) StudioRed else StudioCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                if (isAudioMuted) "Audio Muted" else "Audio Track Active",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                if (isAudioMuted) "Video will be silent" else "Original soundtrack enabled",
                                                color = StudioTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isAudioMuted,
                                        onCheckedChange = { isAudioMuted = it },
                                        //  colors = SwitchDefaults.colors(checkedThumbColor = VideoStudioRed, checkedTrackColor = VideoStudioRed.copy(alpha = 0.3f))
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = StudioRed,
                                            checkedTrackColor = BentoPrimary,
                                            checkedBorderColor = BentoPrimary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(
                                                alpha = 0.25f
                                            ),
                                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.4f
                                            )
                                        )
                                    )
                                }

                                Button(
                                    onClick = { applyEffectToVideo(if (isAudioMuted) "Mute Audio" else "Unmute Audio") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isProcessing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "⚡ Apply Audio Settings to Video",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            StudioEditorTab.BLUR -> {
                                Text(
                                    "🔍 Blur & Pixelate Region",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Select blur type, shape, or drag the box on video preview above:",
                                    color = StudioTextSecondary,
                                    fontSize = 12.sp
                                )

                                // Enable Blur Switch Card
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(StudioCardBg)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BlurCircular,
                                            contentDescription = null,
                                            tint = if (enableBlur) StudioCyan else Color.Gray,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                if (enableBlur) "Blur Region Active" else "Enable Blur Area",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                "Censorship/privacy overlay for video",
                                                color = StudioTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = enableBlur,
                                        onCheckedChange = { enableBlur = it },
                                        //  colors = SwitchDefaults.colors(checkedThumbColor = StudioCyan, checkedTrackColor = StudioCyan.copy(alpha = 0.3f))
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = StudioCyan,
                                            checkedTrackColor = BentoPrimary,
                                            checkedBorderColor = BentoPrimary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(
                                                alpha = 0.25f
                                            ),
                                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.4f
                                            )
                                        )
                                    )
                                }

                                if (enableBlur) {
                                    // Blur Mode (Gaussian vs Pixelate)
                                    Text(
                                        "Blur Effect Mode:",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isGauss = blurType == BlurType.GAUSSIAN
                                        val isPixel = blurType == BlurType.PIXELATE

                                        OutlinedButton(
                                            onClick = { blurType = BlurType.GAUSSIAN },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isGauss) StudioCyan else StudioCardBg,
                                                contentColor = if (isGauss) Color.Black else Color.White
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.BlurOn,
                                                contentDescription = null,
                                                tint = if (isGauss) Color.Black else StudioCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Gaussian Blur",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { blurType = BlurType.PIXELATE },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isPixel) StudioIndigo else StudioCardBg,
                                                contentColor = if (isPixel) Color.White else Color.White
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.GridOn,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Pixelate / Mosaic",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Blur Shape (Rectangle vs Oval)
                                    Text(
                                        "Blur Region Shape:",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isRect = blurShape == BlurShape.RECTANGLE
                                        val isOval = blurShape == BlurShape.OVAL

                                        OutlinedButton(
                                            onClick = { blurShape = BlurShape.RECTANGLE },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isRect) StudioCyan else StudioCardBg,
                                                contentColor = if (isRect) Color.Black else Color.White
                                            )
                                        ) {
                                            Text(
                                                "Rectangle (Box)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { blurShape = BlurShape.OVAL },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isOval) StudioCyan else StudioCardBg,
                                                contentColor = if (isOval) Color.Black else Color.White
                                            )
                                        ) {
                                            Text(
                                                "Oval (Face / Circular)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Quick Location Presets
                                    Text(
                                        "Quick Area Presets:",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    blurLeftNorm = 0.60f
                                                    blurTopNorm = 0.05f
                                                    blurWidthNorm = 0.35f
                                                    blurHeightNorm = 0.20f
                                                    blurShape = BlurShape.RECTANGLE
                                                    Toast.makeText(
                                                        context,
                                                        "Preset: Watermark Top-Right",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = StudioCardBg),
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text(
                                                    "Watermark ↗",
                                                    fontSize = 10.sp,
                                                    color = StudioCyan,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    blurLeftNorm = 0.30f
                                                    blurTopNorm = 0.25f
                                                    blurWidthNorm = 0.40f
                                                    blurHeightNorm = 0.35f
                                                    blurShape = BlurShape.OVAL
                                                    Toast.makeText(
                                                        context,
                                                        "Preset: Face Center",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = StudioCardBg),
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text(
                                                    "Face Center 👤",
                                                    fontSize = 10.sp,
                                                    color = StudioCyan,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    blurLeftNorm = 0.10f
                                                    blurTopNorm = 0.75f
                                                    blurWidthNorm = 0.80f
                                                    blurHeightNorm = 0.20f
                                                    blurShape = BlurShape.RECTANGLE
                                                    Toast.makeText(
                                                        context,
                                                        "Preset: Subtitles Bottom",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = StudioCardBg),
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text(
                                                    "Subtitles 💬",
                                                    fontSize = 10.sp,
                                                    color = StudioCyan,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Intensity Radius Slider
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Blur Intensity / Pixel Size:",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "${blurRadius.toInt()} px",
                                            color = StudioCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Slider(
                                        value = blurRadius,
                                        onValueChange = { blurRadius = it },
                                        valueRange = 2f..25f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = StudioCyan,
                                            activeTrackColor = StudioCyan
                                        )
                                    )

                                    // Width & Height Sliders
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Area Width: ${(blurWidthNorm * 100).toInt()}%",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "Area Height: ${(blurHeightNorm * 100).toInt()}%",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Slider(
                                            value = blurWidthNorm,
                                            onValueChange = {
                                                blurWidthNorm = it.coerceIn(0.1f, 1.0f)
                                            },
                                            valueRange = 0.1f..1.0f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = StudioCyan,
                                                activeTrackColor = StudioCyan
                                            )
                                        )
                                        Slider(
                                            value = blurHeightNorm,
                                            onValueChange = {
                                                blurHeightNorm = it.coerceIn(0.1f, 1.0f)
                                            },
                                            valueRange = 0.1f..1.0f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = StudioCyan,
                                                activeTrackColor = StudioCyan
                                            )
                                        )
                                    }

                                    HorizontalDivider(
                                        color = Color.DarkGray,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    // Time Range for Blur
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Apply Blur to Specific Time Segment",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                if (enableBlurTimeRange) "Blur active between timestamps" else "Blur active for full video",
                                                color = StudioTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Switch(
                                            checked = enableBlurTimeRange,
                                            onCheckedChange = {
                                                enableBlurTimeRange = it
                                                if (it && blurEndMs <= blurStartMs) {
                                                    blurStartMs = 0L
                                                    blurEndMs = durationMs
                                                }
                                            },
                                          //  colors = SwitchDefaults.colors(checkedThumbColor = StudioCyan)
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = StudioCyan,
                                                checkedTrackColor = BentoPrimary,
                                                checkedBorderColor = BentoPrimary,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        )
                                    }

                                    if (enableBlurTimeRange) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { blurStartMs = currentPositionMs },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    "Start at Playhead",
                                                    fontSize = 11.sp,
                                                    color = StudioCyan
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { blurEndMs = currentPositionMs },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    "End at Playhead",
                                                    fontSize = 11.sp,
                                                    color = StudioCyan
                                                )
                                            }
                                        }

                                        RangeSlider(
                                            value = blurStartMs.toFloat()..blurEndMs.toFloat()
                                                .coerceAtLeast(blurStartMs.toFloat() + 1f),
                                            onValueChange = { range ->
                                                blurStartMs = range.start.toLong()
                                                blurEndMs = range.endInclusive.toLong()
                                            },
                                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = StudioCyan,
                                                activeTrackColor = StudioCyan,
                                                inactiveTrackColor = Color.DarkGray
                                            )
                                        )
                                    }
                                }

                                Button(
                                    onClick = { applyEffectToVideo("Blur Region") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isProcessing && enableBlur,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.BlurCircular,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "⚡ Apply Blur to Video",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
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
