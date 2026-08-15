package com.alsaeeddev.recapp.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.alsaeeddev.recapp.MainActivity
import com.alsaeeddev.recapp.data.model.RecordingState
import com.alsaeeddev.recapp.util.NotificationHelper

@RequiresApi(Build.VERSION_CODES.N)
class RecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = ScreenRecordService.recordingState.value
        if (currentState is RecordingState.Recording || currentState is RecordingState.Paused) {
            // Stop recording
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = NotificationHelper.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            // Launch app to start recording cleanly with permission prompt
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("START_RECORDING_DIRECT", true)
            }
            startActivityAndCollapse(intent)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentState = ScreenRecordService.recordingState.value
        val isRecording = currentState is RecordingState.Recording || currentState is RecordingState.Paused

        if (isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop Rec"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Screen Rec"
        }
        tile.updateTile()
    }
}
