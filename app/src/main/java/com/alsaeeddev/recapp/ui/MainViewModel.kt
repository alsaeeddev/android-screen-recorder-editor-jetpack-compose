package com.alsaeeddev.recapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.data.model.RecordingSettings
import com.alsaeeddev.recapp.data.model.RecordingState
import com.alsaeeddev.recapp.data.repository.RecordRepository
import com.alsaeeddev.recapp.service.ScreenRecordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordRepository(application.applicationContext)

    init {
        validateLibraryFiles()
    }

    fun validateLibraryFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.cleanupMissingRecords()
        }
    }

    val recordingState: StateFlow<RecordingState> = ScreenRecordService.Companion.recordingState

    val recordingSettings: StateFlow<RecordingSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecordingSettings()
    )

    val allActiveRecords: StateFlow<List<RecordItem>> = repository.allActiveRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val videoRecords: StateFlow<List<RecordItem>> = repository.videoRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val screenshotRecords: StateFlow<List<RecordItem>> = repository.screenshotRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favoriteRecords: StateFlow<List<RecordItem>> = repository.favoriteRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recycledRecords: StateFlow<List<RecordItem>> = repository.recycledRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSettings(settings: RecordingSettings) {
        viewModelScope.launch {
            repository.updateSettings(settings)
        }
    }

    fun toggleFavorite(item: RecordItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, item.isFavorite)
        }
    }

    fun moveToRecycleBin(item: RecordItem) {
        viewModelScope.launch {
            repository.moveToRecycleBin(item.id)
        }
    }

    fun restoreItem(item: RecordItem) {
        viewModelScope.launch {
            repository.restoreFromRecycleBin(item.id)
        }
    }

    fun deletePermanently(item: RecordItem) {
        viewModelScope.launch {
            repository.deletePermanently(item)
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            repository.emptyRecycleBin()
        }
    }

    fun renameItem(item: RecordItem, newTitle: String) {
        viewModelScope.launch {
            repository.renameRecord(item.id, newTitle)
        }
    }

    fun saveRecord(item: RecordItem) {
        viewModelScope.launch {
            repository.saveRecord(item)
        }
    }
}
