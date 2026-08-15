package com.alsaeeddev.recapp.data.local

import androidx.room.*
import com.alsaeeddev.recapp.data.model.MediaType
import com.alsaeeddev.recapp.data.model.RecordItem
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM record_items WHERE isRecycled = 0 ORDER BY timestamp DESC")
    fun getAllActiveRecords(): Flow<List<RecordItem>>

    @Query("SELECT * FROM record_items WHERE isRecycled = 0 AND mediaType = :type ORDER BY timestamp DESC")
    fun getActiveRecordsByType(type: MediaType): Flow<List<RecordItem>>

    @Query("SELECT * FROM record_items WHERE isRecycled = 0 AND isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteRecords(): Flow<List<RecordItem>>

    @Query("SELECT * FROM record_items WHERE isRecycled = 1 ORDER BY recycledTimestamp DESC")
    fun getRecycledRecords(): Flow<List<RecordItem>>

    @Query("SELECT * FROM record_items WHERE id = :id")
    suspend fun getRecordById(id: Long): RecordItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(item: RecordItem): Long

    @Update
    suspend fun updateRecord(item: RecordItem)

    @Delete
    suspend fun deleteRecord(item: RecordItem)

    @Query("SELECT * FROM record_items")
    suspend fun getAllRecordsDirect(): List<RecordItem>

    @Query("DELETE FROM record_items WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("SELECT * FROM record_items WHERE isRecycled = 1")
    suspend fun getRecycledRecordsList(): List<RecordItem>

    @Query("DELETE FROM record_items WHERE isRecycled = 1")
    suspend fun emptyRecycleBin()

    @Query("UPDATE record_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("UPDATE record_items SET isRecycled = :isRecycled, recycledTimestamp = :timestamp WHERE id = :id")
    suspend fun updateRecycleStatus(id: Long, isRecycled: Boolean, timestamp: Long)

    @Query("UPDATE record_items SET title = :newTitle WHERE id = :id")
    suspend fun renameRecord(id: Long, newTitle: String)
}
