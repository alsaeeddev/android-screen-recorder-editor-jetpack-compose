package com.alsaeeddev.recapp.util

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Holds live overlay ("Appear on top") permission status and exposes
 * a helper to launch the system settings screen for granting it.
 *
 * Automatically re-checks permission on ON_RESUME, since the user can
 * grant/revoke this permission from outside the app (system settings)
 * without the app process being killed.
 */
class OverlayPermissionState internal constructor(
    private val context: Context,
    initialValue: Boolean
) {
    var hasPermission by mutableStateOf(initialValue)
        internal set

    fun refresh() {
        hasPermission = AndroidSettings.canDrawOverlays(context)
    }

    fun requestPermission() {
        val intent = Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        )
        context.startActivity(intent)
    }
}

/**
 * Remembers an [OverlayPermissionState] tied to the current lifecycle owner.
 * Re-checks permission automatically every time the screen resumes.
 */
@Composable
fun rememberOverlayPermissionState(): OverlayPermissionState {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val state = remember {
        OverlayPermissionState(
            context = context,
            initialValue = AndroidSettings.canDrawOverlays(context)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}