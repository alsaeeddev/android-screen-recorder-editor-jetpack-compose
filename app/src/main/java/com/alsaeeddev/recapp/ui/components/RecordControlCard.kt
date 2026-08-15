package com.alsaeeddev.recapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alsaeeddev.recapp.data.model.RecordingState
import com.alsaeeddev.recapp.ui.theme.BentoOnPrimaryContainer
import com.alsaeeddev.recapp.ui.theme.BentoPrimary
import com.alsaeeddev.recapp.ui.theme.BentoPrimaryContainer
import com.alsaeeddev.recapp.ui.theme.BentoRecordRed
import com.alsaeeddev.recapp.util.FormatUtils

@Composable
fun RecordControlCard(
    modifier: Modifier = Modifier,
    recordingState: RecordingState,
    onStartRecord: () -> Unit,
    onPauseRecord: () -> Unit,
    onResumeRecord: () -> Unit,
    onStopRecord: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = BentoPrimaryContainer,
        borderColor = BentoPrimaryContainer,
        cornerRadius = 32.dp,
        testTag = "record_hero_card"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row (Status pill & Elapsed Timer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge
                val badgeText = when (recordingState) {
                    is RecordingState.Idle -> "READY"
                    is RecordingState.Countdown -> "COUNTDOWN ${recordingState.secondsRemaining}s"
                    is RecordingState.Recording -> "RECORDING"
                    is RecordingState.Paused -> "PAUSED"
                    is RecordingState.Processing -> "SAVING..."
                    is RecordingState.Error -> "ERROR"
                }

                val badgeBg = when (recordingState) {
                    is RecordingState.Recording -> BentoRecordRed
                    is RecordingState.Paused -> Color(0xFFE2A000)
                    else -> BentoPrimary
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    if (recordingState is RecordingState.Recording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = pulseAlpha))
                        )
                    }
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                // Timer string
                val durationMs = when (recordingState) {
                    is RecordingState.Recording -> recordingState.durationMs
                    is RecordingState.Paused -> recordingState.durationMs
                    else -> 0L
                }

                Text(
                    text = FormatUtils.formatDuration(durationMs),
                    color = BentoOnPrimaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("recording_timer_text")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Record / Action Button & Status Label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.height(76.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (recordingState) {
                        is RecordingState.Idle, is RecordingState.Error -> {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(BentoPrimary)
                                    .clickable { onStartRecord() }
                                    .testTag("start_recording_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BentoRecordRed)
                                )
                            }
                        }

                        is RecordingState.Countdown -> {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(BentoRecordRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${recordingState.secondsRemaining}",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        is RecordingState.Recording, is RecordingState.Paused -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pause/Resume button
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(BentoPrimary)
                                        .clickable {
                                            if (recordingState is RecordingState.Recording) onPauseRecord() else onResumeRecord()
                                        }
                                        .testTag("pause_resume_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (recordingState is RecordingState.Recording) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Pause/Resume",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                // Stop button
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(BentoRecordRed)
                                        .clickable { onStopRecord() }
                                        .testTag("stop_recording_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        is RecordingState.Processing -> {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(BentoPrimary.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "...",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                val (titleText, subtitleText) = when (recordingState) {
                    is RecordingState.Idle, is RecordingState.Error -> "Start Recording" to "Tap to begin screen capture"
                    is RecordingState.Countdown -> "Get Ready!" to "Starting capture..."
                    is RecordingState.Recording -> "Recording Active" to "Recording in progress..."
                    is RecordingState.Paused -> "Recording Paused" to "Tap Resume or Stop"
                    is RecordingState.Processing -> "Saving File" to "Finalizing video file..."
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = titleText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoOnPrimaryContainer
                    )
                    Text(
                        text = subtitleText,
                        fontSize = 13.sp,
                        color = BentoOnPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
