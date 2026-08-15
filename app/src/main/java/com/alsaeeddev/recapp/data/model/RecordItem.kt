package com.alsaeeddev.recapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    VIDEO,
    SCREENSHOT
}

@Entity(tableName = "record_items")
data class RecordItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val uriString: String,
    val mediaType: MediaType,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val resolution: String = "1080p",
    val fps: Int = 60,
    val isFavorite: Boolean = false,
    val isRecycled: Boolean = false,
    val recycledTimestamp: Long = 0L
)
