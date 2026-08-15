package com.alsaeeddev.recapp.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object VideoEditUtils {

    private const val TAG = "VideoEditUtils"

    fun processVideo(
        context: Context,
        inputPath: String,
        outputPath: String,
        startMs: Long = 0L,
        endMs: Long = -1L,
        cutStartMs: Long = -1L,
        cutEndMs: Long = -1L,
        muteAudio: Boolean = false,
        speedRatio: Float = 1.0f,
        rotationDegrees: Int = 0,
        cropLeftNorm: Float = 0f,
        cropTopNorm: Float = 0f,
        cropRightNorm: Float = 1f,
        cropBottomNorm: Float = 1f,
        enableBlur: Boolean = false,
        blurLeftNorm: Float = 0.25f,
        blurTopNorm: Float = 0.25f,
        blurWidthNorm: Float = 0.5f,
        blurHeightNorm: Float = 0.5f,
        blurRadius: Float = 12f,
        blurType: BlurType = BlurType.GAUSSIAN,
        blurShape: BlurShape = BlurShape.RECTANGLE,
        blurStartMs: Long = -1L,
        blurEndMs: Long = -1L
    ): Boolean {
        val inputFile = File(inputPath)
        if (!inputFile.exists() || inputFile.length() == 0L) {
            Log.e(TAG, "Input file does not exist or is empty: $inputPath")
            return false
        }

        val outputFile = File(outputPath)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val audioProcessors = mutableListOf<AudioProcessor>()
        val videoEffects = mutableListOf<Effect>()

        // 1. Rotation Effect
        if (rotationDegrees != 0) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(-rotationDegrees.toFloat())
                    .build()
            )
        }

        // 2. Crop Effect
        val hasCrop = cropLeftNorm > 0.001f || cropTopNorm > 0.001f || cropRightNorm < 0.999f || cropBottomNorm < 0.999f
        if (hasCrop) {
            val leftNDCS = (-1.0f + 2.0f * cropLeftNorm.coerceIn(0f, 1f)).coerceIn(-1f, 1f)
            val rightNDCS = (-1.0f + 2.0f * cropRightNorm.coerceIn(0f, 1f)).coerceIn(-1f, 1f)
            val topNDCS = (1.0f - 2.0f * cropTopNorm.coerceIn(0f, 1f)).coerceIn(-1f, 1f)
            val bottomNDCS = (1.0f - 2.0f * cropBottomNorm.coerceIn(0f, 1f)).coerceIn(-1f, 1f)

            if (rightNDCS > leftNDCS && topNDCS > bottomNDCS) {
                videoEffects.add(Crop(leftNDCS, rightNDCS, bottomNDCS, topNDCS))
            }
        }

        // 3. Speed Effect
        if (speedRatio != 1.0f && speedRatio > 0.1f) {
            val speedEffect = SpeedChangeEffect(speedRatio)
            videoEffects.add(speedEffect)
            val sonic = SonicAudioProcessor()
            sonic.setSpeed(speedRatio)
            audioProcessors.add(sonic)
        }

        // 4. Area Blur / Pixelate Effect
        if (enableBlur && blurWidthNorm > 0.01f && blurHeightNorm > 0.01f) {
            videoEffects.add(
                AreaBlurEffect(
                    blurXNorm = blurLeftNorm,
                    blurYNorm = blurTopNorm,
                    blurWidthNorm = blurWidthNorm,
                    blurHeightNorm = blurHeightNorm,
                    blurRadius = blurRadius,
                    blurType = blurType,
                    blurShape = blurShape,
                    startMs = blurStartMs,
                    endMs = blurEndMs
                )
            )
        }

        val effects = Effects(audioProcessors, videoEffects)

        val hasCut = cutStartMs >= 0 && cutEndMs > cutStartMs

        val latch = CountDownLatch(1)
        var exportSuccess = false

        val handler = Handler(Looper.getMainLooper())

        handler.post {
            try {
                val transformer = Transformer.Builder(context.applicationContext)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            Log.d(TAG, "Media3 Transformation completed successfully")
                            exportSuccess = true
                            latch.countDown()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.e(TAG, "Media3 Transformation error", exportException)
                            exportSuccess = false
                            latch.countDown()
                        }
                    })
                    .build()

                if (hasCut) {
                    val items = mutableListOf<EditedMediaItem>()

                    // Segment 1: startMs -> cutStartMs
                    if (cutStartMs > startMs) {
                        val clip1 = ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs.coerceAtLeast(0L))
                            .setEndPositionMs(cutStartMs)
                            .build()
                        val mediaItem1 = MediaItem.Builder()
                            .setUri(inputPath)
                            .setClippingConfiguration(clip1)
                            .build()
                        val editedItem1 = EditedMediaItem.Builder(mediaItem1)
                            .setRemoveAudio(muteAudio)
                            .setEffects(effects)
                            .build()
                        items.add(editedItem1)
                    }

                    // Segment 2: cutEndMs -> endMs (or end of file)
                    val clip2Builder = ClippingConfiguration.Builder()
                        .setStartPositionMs(cutEndMs)
                    if (endMs > cutEndMs) {
                        clip2Builder.setEndPositionMs(endMs)
                    }
                    val mediaItem2 = MediaItem.Builder()
                        .setUri(inputPath)
                        .setClippingConfiguration(clip2Builder.build())
                        .build()
                    val editedItem2 = EditedMediaItem.Builder(mediaItem2)
                        .setRemoveAudio(muteAudio)
                        .setEffects(effects)
                        .build()
                    items.add(editedItem2)

                    if (items.isNotEmpty()) {
                        val sequence = EditedMediaItemSequence(items)
                        val composition = Composition.Builder(listOf(sequence)).build()
                        transformer.start(composition, outputPath)
                    } else {
                        latch.countDown()
                    }
                } else {
                    val clipBuilder = ClippingConfiguration.Builder()
                    if (startMs > 0L) {
                        clipBuilder.setStartPositionMs(startMs)
                    }
                    if (endMs > 0L) {
                        clipBuilder.setEndPositionMs(endMs)
                    }

                    val mediaItem = MediaItem.Builder()
                        .setUri(inputPath)
                        .setClippingConfiguration(clipBuilder.build())
                        .build()

                    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                        .setRemoveAudio(muteAudio)
                        .setEffects(effects)
                        .build()

                    transformer.start(editedMediaItem, outputPath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Media3 Transformer", e)
                exportSuccess = false
                latch.countDown()
            }
        }

        try {
            latch.await(3, TimeUnit.MINUTES)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Media3 Transformation interrupted", e)
            return false
        }

        return exportSuccess && outputFile.exists() && outputFile.length() > 0
    }
}
