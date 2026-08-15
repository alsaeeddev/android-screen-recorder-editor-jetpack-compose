package com.alsaeeddev.recapp.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import com.alsaeeddev.recapp.data.local.RecordDao
import com.alsaeeddev.recapp.data.local.RecordDatabase
import com.alsaeeddev.recapp.data.local.SettingsDataStore
import com.alsaeeddev.recapp.data.model.MediaType
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.data.model.RecordingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class RecordRepository(
    private val context: Context,
    private val dao: RecordDao = RecordDatabase.Companion.getInstance(context).recordDao(),
    private val settingsDataStore: SettingsDataStore = SettingsDataStore(context)
) {
    val allActiveRecords: Flow<List<RecordItem>> = dao.getAllActiveRecords().map { filterAndCleanMissing(it) }
    val videoRecords: Flow<List<RecordItem>> = dao.getActiveRecordsByType(MediaType.VIDEO).map { filterAndCleanMissing(it) }
    val screenshotRecords: Flow<List<RecordItem>> = dao.getActiveRecordsByType(MediaType.SCREENSHOT).map { filterAndCleanMissing(it) }
    val favoriteRecords: Flow<List<RecordItem>> = dao.getFavoriteRecords().map { filterAndCleanMissing(it) }
    val recycledRecords: Flow<List<RecordItem>> = dao.getRecycledRecords().map { filterAndCleanMissing(it) }
    val settingsFlow: Flow<RecordingSettings> = settingsDataStore.settingsFlow

    private suspend fun filterAndCleanMissing(items: List<RecordItem>): List<RecordItem> {
        val valid = mutableListOf<RecordItem>()
        val missing = mutableListOf<RecordItem>()

        for (item in items) {
            if (isRecordFileAvailable(context, item)) {
                valid.add(item)
            } else {
                missing.add(item)
            }
        }

        if (missing.isNotEmpty()) {
            missing.forEach { item ->
                try {
                    dao.deleteRecord(item)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return valid
    }

    suspend fun cleanupMissingRecords() {
        try {
            val allRecords = dao.getAllRecordsDirect()
            for (item in allRecords) {
                if (!isRecordFileAvailable(context, item)) {
                    dao.deleteRecord(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun isRecordFileAvailable(context: Context, item: RecordItem): Boolean {
            if (item.filePath.isNotEmpty()) {
                val file = File(item.filePath)
                if (file.exists() && file.length() > 0) {
                    return true
                }
            }
            if (item.uriString.isNotEmpty()) {
                val uri = Uri.parse(item.uriString)
                if (uri.scheme == "content") {
                    return try {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                    } catch (e: Exception) {
                        false
                    }
                } else if (uri.scheme == "file") {
                    val file = uri.path?.let { File(it) }
                    return file != null && file.exists() && file.length() > 0
                }
            }
            return false
        }
    }

    suspend fun saveRecord(item: RecordItem): Long {
        return dao.insertRecord(item)
    }

    suspend fun toggleFavorite(id: Long, currentFavorite: Boolean) {
        dao.updateFavoriteStatus(id, !currentFavorite)
    }

    suspend fun moveToRecycleBin(id: Long) {
        dao.updateRecycleStatus(id, true, System.currentTimeMillis())
    }

    suspend fun restoreFromRecycleBin(id: Long) {
        dao.updateRecycleStatus(id, false, 0L)
    }

    private fun deletePhysicalFile(item: RecordItem) {
        // 1. Delete content URI if present
        if (item.uriString.isNotEmpty()) {
            try {
                val uri = Uri.parse(item.uriString)
                if (uri.scheme == "content") {
                    context.contentResolver.delete(uri, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Delete file path from storage
        if (item.filePath.isNotEmpty()) {
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }

                // Delete entry from MediaStore by file path if contentResolver.delete didn't purge it
                try {
                    context.contentResolver.delete(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Video.Media.DATA}=?",
                        arrayOf(file.absolutePath)
                    )
                    context.contentResolver.delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Images.Media.DATA}=?",
                        arrayOf(file.absolutePath)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Notify MediaScannerConnection so device gallery refreshes instantly
                try {
                    MediaScannerConnection.scanFile(
                        context.applicationContext,
                        arrayOf(file.absolutePath),
                        null,
                        null
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun deletePermanently(item: RecordItem) {
        deletePhysicalFile(item)
        dao.deleteRecord(item)
    }

    suspend fun emptyRecycleBin() {
        try {
            val recycledList = dao.getRecycledRecordsList()
            for (item in recycledList) {
                deletePhysicalFile(item)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        dao.emptyRecycleBin()
    }

    suspend fun renameRecord(id: Long, newTitle: String) {
        dao.renameRecord(id, newTitle)
    }

    suspend fun updateSettings(settings: RecordingSettings) {
        settingsDataStore.updateSettings(settings)
    }
}
