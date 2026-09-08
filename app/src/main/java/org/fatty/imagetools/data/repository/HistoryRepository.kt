package org.fatty.imagetools.data.repository

import kotlinx.coroutines.flow.Flow
import org.fatty.imagetools.data.db.HistoryDao
import org.fatty.imagetools.data.db.HistoryRecord

class HistoryRepository(private val historyDao: HistoryDao) {

    // 插入新记录
    suspend fun insertRecord(record: HistoryRecord) {
        historyDao.insertRecord(record)
    }

    // 删除记录
    suspend fun deleteRecord(record: HistoryRecord) {
        historyDao.deleteRecord(record)
    }

    // 获取历史记录流，如果 keyword 为空则获取所有，否则获取搜索结果
    fun getHistoryFlow(keyword: String): Flow<List<HistoryRecord>> {
        return if (keyword.isBlank()) {
            historyDao.getAllHistory()
        } else {
            historyDao.searchHistory(keyword)
        }
    }
}
