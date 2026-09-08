package org.fatty.imagetools.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_records")
data class HistoryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,      // 本地缓存的图片路径
    val timestamp: Long,       // 生成时间
    val imageCount: Int,       // 拼图数量
    val columns: Int,          // 列数
    val format: String,        // 导出格式 (PNG/JPG)
    val width: Int,            // 导出宽度
    val height: Int,           // 导出高度
    val searchTags: String     // 搜索关键词 (例如："2列 1080px PNG 4张图片")，用于数据库 LIKE 搜索
)
