package org.fatty.imagetools.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HistoryRecord)

    @Delete
    suspend fun deleteRecord(record: HistoryRecord)

    // 获取所有记录，按时间倒序排列。返回 Flow 实现冷流监听
    @Query("SELECT * FROM history_records ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryRecord>>

    // 根据关键词搜索记录。同样返回 Flow
    @Query("SELECT * FROM history_records WHERE searchTags LIKE '%' || :keyword || '%' ORDER BY timestamp DESC")
    fun searchHistory(keyword: String): Flow<List<HistoryRecord>>
}
