package com.alsaeeddev.recapp.ui.components

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alsaeeddev.recapp.ui.theme.BentoPrimary
import com.alsaeeddev.recapp.ui.theme.BentoRecordRed
import kotlin.math.roundToInt

@Composable
fun SelectiveAreaDialog(
    onDismiss: () -> Unit,
    onConfirmRecording: (widthPx: Int, heightPx: Int, offsetX: Int, offsetY: Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        SelectiveAreaContent(
            onDismiss = onDismiss,
            onConfirmRecording = onConfirmRecording
        )
    }
}

@Composable
fun SelectiveAreaContent(
    onDismiss: () -> Unit,
    onConfirmRecording: (widthPx: Int, heightPx: Int, offsetX: Int, offsetY: Int) -> Unit
) {
    val context = LocalContext.current
    val displayMetrics = remember(context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        metrics
    }

    val screenWidthPx = displayMetrics.widthPixels.toFloat()
    val screenHeightPx = displayMetrics.heightPixels.toFloat()

    val density = LocalDensity.current
    val minBoxSizePx = with(density) { 150.dp.toPx() }
    val handleTouchRadiusPx = with(density) { 24.dp.toPx() }

    // Initial rectangle positioning (centered, 80% width, 50% height)
    var rectWidthPx by remember { mutableFloatStateOf(screenWidthPx * 0.8f) }
    var rectHeightPx by remember { mutableFloatStateOf(screenHeightPx * 0.5f) }
    var rectLeftPx by remember { mutableFloatStateOf((screenWidthPx - rectWidthPx) / 2f) }
    var rectTopPx by remember { mutableFloatStateOf((screenHeightPx - rectHeightPx) / 2f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .testTag("selective_area_fullscreen_overlay")
    ) {
            val maxW = constraints.maxWidth.toFloat().coerceAtLeast(screenWidthPx)
            val maxH = constraints.maxHeight.toFloat().coerceAtLeast(screenHeightPx)

            // Canvas drawing semi-transparent mask and crop window border with handles
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val touchX = change.position.x
                            val touchY = change.position.y

                            val rectRight = rectLeftPx + rectWidthPx
                            val rectBottom = rectTopPx + rectHeightPx

                            val nearLeft = Math.abs(touchX - rectLeftPx) < handleTouchRadiusPx
                            val nearRight = Math.abs(touchX - rectRight) < handleTouchRadiusPx
                            val nearTop = Math.abs(touchY - rectTopPx) < handleTouchRadiusPx
                            val nearBottom = Math.abs(touchY - rectBottom) < handleTouchRadiusPx

                            val withinX = touchX >= (rectLeftPx - handleTouchRadiusPx) && touchX <= (rectRight + handleTouchRadiusPx)
                            val withinY = touchY >= (rectTopPx - handleTouchRadiusPx) && touchY <= (rectBottom + handleTouchRadiusPx)

                            when {
                                // --- CORNER RESIZING ---
                                // Top-Left Corner
                                nearLeft && nearTop -> {
                                    val newLeft = (rectLeftPx + dragAmount.x).coerceIn(0f, rectRight - minBoxSizePx)
                                    val newTop = (rectTopPx + dragAmount.y).coerceIn(0f, rectBottom - minBoxSizePx)
                                    rectWidthPx = rectRight - newLeft
                                    rectHeightPx = rectBottom - newTop
                                    rectLeftPx = newLeft
                                    rectTopPx = newTop
                                }
                                // Top-Right Corner
                                nearRight && nearTop -> {
                                    val newRight = (rectRight + dragAmount.x).coerceIn(rectLeftPx + minBoxSizePx, maxW)
                                    val newTop = (rectTopPx + dragAmount.y).coerceIn(0f, rectBottom - minBoxSizePx)
                                    rectWidthPx = newRight - rectLeftPx
                                    rectHeightPx = rectBottom - newTop
                                    rectTopPx = newTop
                                }
                                // Bottom-Left Corner
                                nearLeft && nearBottom -> {
                                    val newLeft = (rectLeftPx + dragAmount.x).coerceIn(0f, rectRight - minBoxSizePx)
                                    val newBottom = (rectBottom + dragAmount.y).coerceIn(rectTopPx + minBoxSizePx, maxH)
                                    rectWidthPx = rectRight - newLeft
                                    rectHeightPx = newBottom - rectTopPx
                                    rectLeftPx = newLeft
                                }
                                // Bottom-Right Corner
                                nearRight && nearBottom -> {
                                    val newRight = (rectRight + dragAmount.x).coerceIn(rectLeftPx + minBoxSizePx, maxW)
                                    val newBottom = (rectBottom + dragAmount.y).coerceIn(rectTopPx + minBoxSizePx, maxH)
                                    rectWidthPx = newRight - rectLeftPx
                                    rectHeightPx = newBottom - rectTopPx
                                }

                                // --- EDGE-BASED RESIZING (Independent Edges) ---
                                // Left Edge Only
                                nearLeft && withinY -> {
                                    val newLeft = (rectLeftPx + dragAmount.x).coerceIn(0f, rectRight - minBoxSizePx)
                                    rectWidthPx = rectRight - newLeft
                                    rectLeftPx = newLeft
                                }
                                // Right Edge Only
                                nearRight && withinY -> {
                                    val newRight = (rectRight + dragAmount.x).coerceIn(rectLeftPx + minBoxSizePx, maxW)
                                    rectWidthPx = newRight - rectLeftPx
                                }
                                // Top Edge Only
                                nearTop && withinX -> {
                                    val newTop = (rectTopPx + dragAmount.y).coerceIn(0f, rectBottom - minBoxSizePx)
                                    rectHeightPx = rectBottom - newTop
                                    rectTopPx = newTop
                                }
                                // Bottom Edge Only
                                nearBottom && withinX -> {
                                    val newBottom = (rectBottom + dragAmount.y).coerceIn(rectTopPx + minBoxSizePx, maxH)
                                    rectHeightPx = newBottom - rectTopPx
                                }

                                // --- MOVE ENTIRE SELECTION SEAMLESSLY ---
                                else -> {
                                    val newLeft = (rectLeftPx + dragAmount.x).coerceIn(0f, maxW - rectWidthPx)
                                    val newTop = (rectTopPx + dragAmount.y).coerceIn(0f, maxH - rectHeightPx)
                                    rectLeftPx = newLeft
                                    rectTopPx = newTop
                                }
                            }
                        }
                    }
            ) {
                // Cutout hole for active area
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(rectLeftPx, rectTopPx),
                    size = Size(rectWidthPx, rectHeightPx)
                )

                // Draw rectangle outline
                drawRect(
                    color = BentoPrimary,
                    topLeft = Offset(rectLeftPx, rectTopPx),
                    size = Size(rectWidthPx, rectHeightPx),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                    )
                )

                // Corner Handles
                val handleRadius = 10.dp.toPx()
                val handleColor = Color.White
                val borderColor = BentoPrimary

                val corners = listOf(
                    Offset(rectLeftPx, rectTopPx),
                    Offset(rectLeftPx + rectWidthPx, rectTopPx),
                    Offset(rectLeftPx, rectTopPx + rectHeightPx),
                    Offset(rectLeftPx + rectWidthPx, rectTopPx + rectHeightPx)
                )

                corners.forEach { corner ->
                    drawCircle(borderColor, radius = handleRadius + 3.dp.toPx(), center = corner)
                    drawCircle(handleColor, radius = handleRadius, center = corner)
                }

                // Mid-Edge Handle Bars
                val barLen = 24.dp.toPx()
                val barThickness = 6.dp.toPx()

                // Top & Bottom Edge Bars (Horizontal)
                val topMidX = rectLeftPx + rectWidthPx / 2f
                val bottomMidX = rectLeftPx + rectWidthPx / 2f
                val leftMidY = rectTopPx + rectHeightPx / 2f
                val rightMidY = rectTopPx + rectHeightPx / 2f

                // Top Mid
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset(topMidX - barLen / 2f, rectTopPx - barThickness / 2f),
                    size = Size(barLen, barThickness),
                    cornerRadius = CornerRadius(barThickness / 2f)
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(topMidX - barLen / 2f, rectTopPx - barThickness / 2f),
                    size = Size(barLen, barThickness),
                    cornerRadius = CornerRadius(barThickness / 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Bottom Mid
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset(bottomMidX - barLen / 2f, rectTopPx + rectHeightPx - barThickness / 2f),
                    size = Size(barLen, barThickness),
                    cornerRadius = CornerRadius(barThickness / 2f)
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(bottomMidX - barLen / 2f, rectTopPx + rectHeightPx - barThickness / 2f),
                    size = Size(barLen, barThickness),
                    cornerRadius = CornerRadius(barThickness / 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Left Mid
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset(rectLeftPx - barThickness / 2f, leftMidY - barLen / 2f),
                    size = Size(barThickness, barLen),
                    cornerRadius = CornerRadius(barThickness / 2f)
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(rectLeftPx - barThickness / 2f, leftMidY - barLen / 2f),
                    size = Size(barThickness, barLen),
                    cornerRadius = CornerRadius(barThickness / 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Right Mid
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset(rectLeftPx + rectWidthPx - barThickness / 2f, rightMidY - barLen / 2f),
                    size = Size(barThickness, barLen),
                    cornerRadius = CornerRadius(barThickness / 2f)
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(rectLeftPx + rectWidthPx - barThickness / 2f, rightMidY - barLen / 2f),
                    size = Size(barThickness, barLen),
                    cornerRadius = CornerRadius(barThickness / 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Selection Window Header / Action Bar overlay
            Box(
                modifier = Modifier
                    .offset { IntOffset(rectLeftPx.roundToInt(), (rectTopPx - 56.dp.toPx()).roundToInt().coerceAtLeast(16)) }
                    .width(with(density) { rectWidthPx.toDp() }.coerceAtLeast(240.dp))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = Color(0xFF0F172A),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropFree,
                                contentDescription = null,
                                tint = BentoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${rectWidthPx.roundToInt()} × ${rectHeightPx.roundToInt()} px",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    onConfirmRecording(
                                        rectWidthPx.roundToInt(),
                                        rectHeightPx.roundToInt(),
                                        rectLeftPx.roundToInt(),
                                        rectTopPx.roundToInt()
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BentoRecordRed),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("confirm_selective_area_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Record",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Drag hint in center of selection box
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (rectLeftPx + (rectWidthPx / 2f) - 60.dp.toPx()).roundToInt(),
                            (rectTopPx + (rectHeightPx / 2f) - 18.dp.toPx()).roundToInt()
                        )
                    }
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenWith,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Drag & Resize",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
