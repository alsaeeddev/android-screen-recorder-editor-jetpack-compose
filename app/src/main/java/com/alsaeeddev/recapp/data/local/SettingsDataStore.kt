package com.alsaeeddev.recapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.alsaeeddev.recapp.data.model.AudioSourceOption
import com.alsaeeddev.recapp.data.model.BitrateOption
import com.alsaeeddev.recapp.data.model.EncoderOption
import com.alsaeeddev.recapp.data.model.FpsOption
import com.alsaeeddev.recapp.data.model.OrientationOption
import com.alsaeeddev.recapp.data.model.RecordingRegionOption
import com.alsaeeddev.recapp.data.model.RecordingSettings
import com.alsaeeddev.recapp.data.model.ResolutionOption
import com.alsaeeddev.recapp.data.model.VideoFormatOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "recording_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val RECORDING_REGION = stringPreferencesKey("recording_region")
        val RESOLUTION = stringPreferencesKey("resolution")
        val FPS = intPreferencesKey("fps")
        val BITRATE = stringPreferencesKey("bitrate")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val ORIENTATION = stringPreferencesKey("orientation")
        val ENCODER = stringPreferencesKey("encoder")
        val FORMAT = stringPreferencesKey("format")
        val COUNTDOWN = intPreferencesKey("countdown_seconds")
        val FLOATING_BUBBLE = booleanPreferencesKey("show_floating_bubble")
        val NOTIF_CONTROLS = booleanPreferencesKey("show_notification_controls")
        val FILENAME_PREFIX = stringPreferencesKey("filename_prefix")
        val MAX_DURATION = intPreferencesKey("max_duration_minutes")
        val DARK_MODE = booleanPreferencesKey("is_dark_mode")
    }

    val settingsFlow: Flow<RecordingSettings> = context.dataStore.data.map { prefs ->
        val regionStr = prefs[Keys.RECORDING_REGION] ?: RecordingRegionOption.FULL_SCREEN.name
        val region = try { RecordingRegionOption.valueOf(regionStr) } catch (e: Exception) { RecordingRegionOption.FULL_SCREEN }

        val resStr = prefs[Keys.RESOLUTION] ?: ResolutionOption.RES_1080P.name
        val res = try { ResolutionOption.valueOf(resStr) } catch (e: Exception) { ResolutionOption.RES_1080P }

        val fpsInt = prefs[Keys.FPS] ?: 60
        val fps = FpsOption.values().firstOrNull { it.fps == fpsInt } ?: FpsOption.FPS_60

        val bitrateStr = prefs[Keys.BITRATE] ?: BitrateOption.AUTO.name
        val bitrate = try { BitrateOption.valueOf(bitrateStr) } catch (e: Exception) { BitrateOption.AUTO }

        val audioStr = prefs[Keys.AUDIO_SOURCE] ?: AudioSourceOption.MIC_ONLY.name
        val audio = try { AudioSourceOption.valueOf(audioStr) } catch (e: Exception) { AudioSourceOption.MIC_ONLY }

        val orientStr = prefs[Keys.ORIENTATION] ?: OrientationOption.AUTO.name
        val orient = try { OrientationOption.valueOf(orientStr) } catch (e: Exception) { OrientationOption.AUTO }

        val encStr = prefs[Keys.ENCODER] ?: EncoderOption.HEVC.name
        val enc = try { EncoderOption.valueOf(encStr) } catch (e: Exception) { EncoderOption.HEVC }

        val fmtStr = prefs[Keys.FORMAT] ?: VideoFormatOption.MP4.name
        val fmt = try { VideoFormatOption.valueOf(fmtStr) } catch (e: Exception) { VideoFormatOption.MP4 }

        RecordingSettings(
            recordingRegion = region,
            resolution = res,
            fps = fps,
            bitrate = bitrate,
            audioSource = audio,
            orientation = orient,
            encoder = enc,
            format = fmt,
            countdownSeconds = prefs[Keys.COUNTDOWN] ?: 3,
            showFloatingBubble = prefs[Keys.FLOATING_BUBBLE] ?: true,
            showNotificationControls = prefs[Keys.NOTIF_CONTROLS] ?: true,
            filenamePrefix = prefs[Keys.FILENAME_PREFIX] ?: "REC",
            maxDurationMinutes = prefs[Keys.MAX_DURATION] ?: 0,
            isDarkMode = prefs[Keys.DARK_MODE] ?: false
        )
    }

    suspend fun updateSettings(settings: RecordingSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RECORDING_REGION] = settings.recordingRegion.name
            prefs[Keys.RESOLUTION] = settings.resolution.name
            prefs[Keys.FPS] = settings.fps.fps
            prefs[Keys.BITRATE] = settings.bitrate.name
            prefs[Keys.AUDIO_SOURCE] = settings.audioSource.name
            prefs[Keys.ORIENTATION] = settings.orientation.name
            prefs[Keys.ENCODER] = settings.encoder.name
            prefs[Keys.FORMAT] = settings.format.name
            prefs[Keys.COUNTDOWN] = settings.countdownSeconds
            prefs[Keys.FLOATING_BUBBLE] = settings.showFloatingBubble
            prefs[Keys.NOTIF_CONTROLS] = settings.showNotificationControls
            prefs[Keys.FILENAME_PREFIX] = settings.filenamePrefix
            prefs[Keys.MAX_DURATION] = settings.maxDurationMinutes
            prefs[Keys.DARK_MODE] = settings.isDarkMode
        }
    }
}
