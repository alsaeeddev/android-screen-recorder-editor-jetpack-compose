package com.alsaeeddev.recapp.data.local

import androidx.room.TypeConverter
import com.alsaeeddev.recapp.data.model.MediaType

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = try {
        MediaType.valueOf(value)
    } catch (e: Exception) {
        MediaType.VIDEO
    }
}
