package com.alsaeeddev.recapp.service

import android.app.Activity
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import android.provider.Settings
import android.view.Gravity
import com.alsaeeddev.recapp.data.model.AudioSourceOption
import com.alsaeeddev.recapp.data.model.EncoderOption
import com.alsaeeddev.recapp.data.model.MediaType
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.data.model.RecordingRegionOption
import com.alsaeeddev.recapp.data.model.RecordingSettings
import com.alsaeeddev.recapp.data.model.RecordingState
import com.alsaeeddev.recapp.data.repository.RecordRepository
import com.alsaeeddev.recapp.ui.components.ActiveCropOverlayView
import com.alsaeeddev.recapp.util.FormatUtils
import com.alsaeeddev.recapp.util.GlCropHelper
import com.alsaeeddev.recapp.util.MediaStoreUtils
import com.alsaeeddev.recapp.util.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

class ScreenRecordService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var glCropHelper: GlCropHelper? = null

    private var isSelectiveMode = false
    private var cropX = 0
    private var cropY = 0
    private var cropW = 0
    private var cropH = 0
    private var displayRealW = 0
    private var displayRealH = 0

    private var windowManager: WindowManager? = null
    private var activeOverlayView: ActiveCropOverlayView? = null

    private var outputFile: File? = null
    private var outputUri: Uri? = null
    private var startTimeMs: Long = 0L
    private var pausedTimeMs: Long = 0L
    private var totalPausedDurationMs: Long = 0L

    private var timerJob: Job? = null

    private lateinit var repository: RecordRepository
    private var currentSettings = RecordingSettings()

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordService = this@ScreenRecordService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        repository = RecordRepository(applicationContext)
        NotificationHelper.createNotificationChannel(applicationContext)

        serviceScope.launch {
            repository.settingsFlow.collect { settings ->
                currentSettings = settings
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationHelper.ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (resultCode == Activity.RESULT_OK && data != null) {
                    val cropX = intent.getIntExtra(EXTRA_CROP_X, 0)
                    val cropY = intent.getIntExtra(EXTRA_CROP_Y, 0)
                    val cropW = intent.getIntExtra(EXTRA_CROP_W, 0)
                    val cropH = intent.getIntExtra(EXTRA_CROP_H, 0)
                    startRecordingFlow(resultCode, data, cropX, cropY, cropW, cropH)
                } else {
                    _recordingState.value = RecordingState.Error("MediaProjection permission denied")
                    stopSelf()
                }
            }
            NotificationHelper.ACTION_PAUSE -> pauseRecording()
            NotificationHelper.ACTION_RESUME -> resumeRecording()
            NotificationHelper.ACTION_STOP -> stopRecording()
            NotificationHelper.ACTION_SCREENSHOT -> takeScreenshot()
        }
        return START_NOT_STICKY
    }

    private fun startRecordingFlow(
        resultCode: Int,
        data: Intent,
        cropX: Int,
        cropY: Int,
        cropW: Int,
        cropH: Int
    ) {
        val notification = NotificationHelper.buildNotification(
            this, ScreenRecordService::class.java, "Preparing screen recording...", false, false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                // Ensure latest settings from DataStore are loaded instantly
                currentSettings = repository.settingsFlow.first()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load latest settings, using current fallback", e)
            }

            val countdown = currentSettings.countdownSeconds
            if (countdown > 0) {
                for (i in countdown downTo 1) {
                    _recordingState.value = RecordingState.Countdown(i)
                    updateNotification("Starting in $i seconds...")
                    delay(1000)
                }
            }

            try {
                initMediaProjectionAndRecorder(resultCode, data, cropX, cropY, cropW, cropH)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                _recordingState.value = RecordingState.Error(e.message ?: "Failed to start recorder")
                stopSelf()
            }
        }
    }

    private fun initMediaProjectionAndRecorder(
        resultCode: Int,
        data: Intent,
        cropX: Int,
        cropY: Int,
        cropW: Int,
        cropH: Int
    ) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val screenDensity = metrics.densityDpi
        val realWidth = metrics.widthPixels
        val realHeight = metrics.heightPixels

        val maxTarget = currentSettings.resolution.maxDimension
        val currentMax = maxOf(realWidth, realHeight)

        val scale = if (maxTarget > 0 && currentMax > maxTarget) {
            maxTarget.toFloat() / currentMax.toFloat()
        } else {
            1.0f
        }

        var screenWidth = (realWidth * scale).toInt()
        var screenHeight = (realHeight * scale).toInt()

        if (screenWidth % 2 != 0) screenWidth--
        if (screenHeight % 2 != 0) screenHeight--

        val isSelectiveArea = currentSettings.recordingRegion == RecordingRegionOption.CUSTOM_AREA && cropW > 0 && cropH > 0
        isSelectiveMode = isSelectiveArea
        displayRealW = realWidth
        displayRealH = realHeight

        var cX = cropX.coerceIn(0, (realWidth - 32).coerceAtLeast(0))
        var cY = cropY.coerceIn(0, (realHeight - 32).coerceAtLeast(0))
        var cW = cropW.coerceIn(160, realWidth - cX)
        var cH = cropH.coerceIn(160, realHeight - cY)

        if (cW % 2 != 0) cW--
        if (cH % 2 != 0) cH--
        if (cW < 160) cW = 160
        if (cH < 160) cH = 160

        this.cropX = cX
        this.cropY = cY
        this.cropW = cW
        this.cropH = cH

        var videoWidth = if (isSelectiveArea) cW else screenWidth
        var videoHeight = if (isSelectiveArea) cH else screenHeight

        if (videoWidth % 2 != 0) videoWidth--
        if (videoHeight % 2 != 0) videoHeight--

        val (file, uri) = MediaStoreUtils.createVideoFile(
            this,
            currentSettings.filenamePrefix,
            currentSettings.format.extension
        )
        outputFile = file
        outputUri = uri

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            val hasAudio = currentSettings.audioSource != AudioSourceOption.MUTE
            if (hasAudio) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }

            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.absolutePath)

            setVideoSize(videoWidth, videoHeight)
            setVideoFrameRate(currentSettings.fps.fps)
            setVideoEncodingBitRate(currentSettings.bitrate.bps)

            if (currentSettings.encoder == EncoderOption.HEVC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
            } else {
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            }

            if (hasAudio) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
            }

            prepare()
        }

        val projectionTargetSurface: Surface? = if (isSelectiveArea) {
            val recorderSurface = mediaRecorder?.surface
                ?: throw IllegalStateException("MediaRecorder surface is null")

            val helper = GlCropHelper(
                outputSurface = recorderSurface,
                realWidth = realWidth,
                realHeight = realHeight,
                cropX = cX,
                cropY = cY,
                cropW = cW,
                cropH = cH
            )
            helper.start()
            glCropHelper = helper
            helper.inputSurface
        } else {
            mediaRecorder?.surface
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecordDisplay",
            realWidth,
            realHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            projectionTargetSurface,
            null,
            null
        )

        mediaRecorder?.start()
        startTimeMs = System.currentTimeMillis()
        totalPausedDurationMs = 0L

        if (isSelectiveArea) {
            showActiveCropOverlay(cX, cY, cW, cH)
        }

        startTimer()
        updateNotification("Recording screen... 00:00")
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTimeMs - totalPausedDurationMs
                _recordingState.value = RecordingState.Recording(elapsed)
                val durationText = FormatUtils.formatDuration(elapsed)
                updateNotification("Recording: $durationText")
                activeOverlayView?.let {
                    it.isPaused = false
                    it.durationText = durationText
                }
                delay(1000)
            }
        }
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                timerJob?.cancel()
                pausedTimeMs = System.currentTimeMillis()
                val elapsed = pausedTimeMs - startTimeMs - totalPausedDurationMs
                _recordingState.value = RecordingState.Paused(elapsed)
                updateNotification("Paused: ${FormatUtils.formatDuration(elapsed)}")
                activeOverlayView?.let {
                    it.isPaused = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pause failed", e)
            }
        }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                if (pausedTimeMs > 0) {
                    totalPausedDurationMs += (System.currentTimeMillis() - pausedTimeMs)
                }
                startTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Resume failed", e)
            }
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        _recordingState.value = RecordingState.Processing

        serviceScope.launch(Dispatchers.IO) {
            hideActiveCropOverlay()

            // Stop the projection feeding new frames first, then tear down the GL
            // renderer (if any), and only then stop/release MediaRecorder. This avoids
            // the GL thread trying to draw onto a surface that MediaRecorder already released.
            virtualDisplay?.release()
            virtualDisplay = null

            glCropHelper?.release()
            glCropHelper = null

            try {
                mediaRecorder?.apply {
                    stop()
                    reset()
                    release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaRecorder", e)
            } finally {
                mediaRecorder = null
            }

            mediaProjection?.stop()
            mediaProjection = null

            val file = outputFile
            if (file != null && file.exists() && file.length() > 0) {
                // Selective-area crop now happens live during capture via GlCropHelper
                // (see initMediaProjectionAndRecorder), so no post-processing step is needed here.

                val duration = System.currentTimeMillis() - startTimeMs - totalPausedDurationMs
                val resLabel = if (isSelectiveMode) "${cropW}x${cropH}" else currentSettings.resolution.label
                val item = RecordItem(
                    title = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    uriString = outputUri?.toString() ?: Uri.fromFile(file).toString(),
                    mediaType = MediaType.VIDEO,
                    durationMs = duration,
                    sizeBytes = file.length(),
                    resolution = resLabel,
                    fps = currentSettings.fps.fps
                )
                repository.saveRecord(item)
            }

            _recordingState.value = RecordingState.Idle
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun showActiveCropOverlay(x: Int, y: Int, w: Int, h: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        Handler(Looper.getMainLooper()).post {
            try {
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

                val density = resources.displayMetrics.density
                val padX = (12 * density).toInt()
                val padYTop = (36 * density).toInt()
                val padYBottom = (12 * density).toInt()

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    w + padX * 2,
                    h + padYTop + padYBottom,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.x = x - padX
                    this.y = y - padYTop
                }

                val overlayView = ActiveCropOverlayView(this)
                windowManager?.addView(overlayView, params)
                activeOverlayView = overlayView
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show active crop overlay", e)
            }
        }
    }

    private fun hideActiveCropOverlay() {
        Handler(Looper.getMainLooper()).post {
            try {
                activeOverlayView?.let {
                    windowManager?.removeView(it)
                    activeOverlayView = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide active crop overlay", e)
            }
        }
    }

    fun takeScreenshot() {
        serviceScope.launch(Dispatchers.IO) {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val projection = mediaProjection

            if (projection != null) {
                val tempVD = projection.createVirtualDisplay(
                    "ScreenshotDisplay",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface,
                    null,
                    null
                )

                // Give frame a moment to draw
                delay(300)
                val image = imageReader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                    val (shotFile, shotUri) = MediaStoreUtils.createScreenshotFile(this@ScreenRecordService)
                    FileOutputStream(shotFile).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    val recordItem = RecordItem(
                        title = shotFile.nameWithoutExtension,
                        filePath = shotFile.absolutePath,
                        uriString = shotUri?.toString() ?: Uri.fromFile(shotFile).toString(),
                        mediaType = MediaType.SCREENSHOT,
                        durationMs = 0L,
                        sizeBytes = shotFile.length(),
                        resolution = "${width}x${height}"
                    )
                    repository.saveRecord(recordItem)

                    tempVD?.release()
                    imageReader.close()
                } else {
                    tempVD?.release()
                    imageReader.close()
                }
            }
        }
    }

    private fun updateNotification(statusText: String) {
        val state = _recordingState.value
        val isRec = state is RecordingState.Recording || state is RecordingState.Paused
        val isPaused = state is RecordingState.Paused
        val notif = NotificationHelper.buildNotification(
            this, ScreenRecordService::class.java, statusText, isRec, isPaused
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideActiveCropOverlay()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_CROP_X = "extra_crop_x"
        const val EXTRA_CROP_Y = "extra_crop_y"
        const val EXTRA_CROP_W = "extra_crop_w"
        const val EXTRA_CROP_H = "extra_crop_h"

        private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    }
}