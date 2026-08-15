package com.alsaeeddev.recapp.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alsaeeddev.recapp.MainActivity
import com.alsaeeddev.recapp.ui.components.SelectiveAreaContent
import com.alsaeeddev.recapp.ui.theme.ScreenRecorderTheme

class SelectiveAreaOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                stopSelf()
                return
            }
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        } catch (e: Exception) {
            Log.e("SelectiveOverlayService", "Error in onCreate", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            if (overlayView == null) {
                setupOverlayView()
            }
        } catch (e: Exception) {
            Log.e("SelectiveOverlayService", "Error in onStartCommand", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun setupOverlayView() {
        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@SelectiveAreaOverlayService)
                setViewTreeSavedStateRegistryOwner(this@SelectiveAreaOverlayService)
                setViewTreeViewModelStoreOwner(this@SelectiveAreaOverlayService)
                setContent {
                    ScreenRecorderTheme {
                        SelectiveAreaContent(
                            onDismiss = {
                                stopSelf()
                            },
                            onConfirmRecording = { w, h, x, y ->
                                startRecordingFlowFromService(w, h, x, y)
                                stopSelf()
                            }
                        )
                    }
                }
            }

            windowManager?.addView(composeView, params)
            overlayView = composeView
        } catch (e: Exception) {
            Log.e("SelectiveOverlayService", "Failed adding overlay view", e)
            stopSelf()
        }
    }

    private fun startRecordingFlowFromService(w: Int, h: Int, x: Int, y: Int) {
        try {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_START_CROP_RECORDING, true)
                putExtra(ScreenRecordService.EXTRA_CROP_X, x)
                putExtra(ScreenRecordService.EXTRA_CROP_Y, y)
                putExtra(ScreenRecordService.EXTRA_CROP_W, w)
                putExtra(ScreenRecordService.EXTRA_CROP_H, h)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
        } catch (e: Exception) {
            Log.e("SelectiveOverlayService", "Error launching activity from service", e)
            try {
                val fallbackIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(EXTRA_START_CROP_RECORDING, true)
                    putExtra(ScreenRecordService.EXTRA_CROP_X, x)
                    putExtra(ScreenRecordService.EXTRA_CROP_Y, y)
                    putExtra(ScreenRecordService.EXTRA_CROP_W, w)
                    putExtra(ScreenRecordService.EXTRA_CROP_H, h)
                }
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Log.e("SelectiveOverlayService", "Fallback startActivity also failed", ex)
            }
        }
    }

    override fun onDestroy() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                overlayView = null
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        } catch (e: Exception) {
            Log.e("SelectiveOverlayService", "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_CROP_RECORDING = "extra_start_crop_recording"
    }
}
