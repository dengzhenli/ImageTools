package org.fatty.imagetools.presentation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fatty.imagetools.R
import org.fatty.imagetools.data.db.AppDatabase
import org.fatty.imagetools.data.db.HistoryRecord
import org.fatty.imagetools.data.repository.HistoryRepository
import org.fatty.imagetools.domain.StitchOptions
import org.fatty.imagetools.domain.stitchImages
import org.fatty.imagetools.domain.saveBitmapToGallery
import org.fatty.imagetools.log.ALog
import java.io.File
import java.io.FileOutputStream

/**
 * ViewModel 负责管理 State 并处理 Intent。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ImageStitcherViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "ImageStitcherViewModel"
    }

    // _state 是内部可变的 StateFlow，确保只有 ViewModel 能够修改状态
    private val _state = MutableStateFlow(ImageStitcherState())
    val state: StateFlow<ImageStitcherState> = _state.asStateFlow()

    // _effect 用于发送一次性事件
    private val _effect = MutableSharedFlow<ImageStitcherEffect>()
    val effect: SharedFlow<ImageStitcherEffect> = _effect.asSharedFlow()

    private val context get() = getApplication<Application>()

    // 历史记录存储库
    private val historyRepository: HistoryRepository

    // 用于冷流防抖搜索的 Flow
    private val _searchQueryFlow = MutableStateFlow("")

    init {
        val database = AppDatabase.getDatabase(context)
        historyRepository = HistoryRepository(database.historyDao())
        ALog.i(TAG, "历史记录数据库已初始化")

        // 收集搜索流，并更新到 State 中
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300) // 冷流防抖：300ms内没有新输入才执行搜索
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    historyRepository.getHistoryFlow(query)
                }
                .collect { records ->
                    _state.update { it.copy(historyRecords = records) }
                }
        }
    }

    /**
     * 接收并处理来自 UI 的所有 Intent
     */
    fun processIntent(intent: ImageStitcherIntent) {
        when (intent) {
            is ImageStitcherIntent.ImagesPicked -> handleImagesPicked(intent.uris)
            is ImageStitcherIntent.ClearImages -> handleClearImages()
            is ImageStitcherIntent.UpdateColumns -> _state.update { it.copy(columnsInput = intent.columns) }
            is ImageStitcherIntent.UpdateSpacing -> _state.update { it.copy(spacingInput = intent.spacing) }
            is ImageStitcherIntent.UpdateCornerRadius -> _state.update { it.copy(cornerRadiusInput = intent.radius) }
            is ImageStitcherIntent.SelectBackground -> _state.update { it.copy(selectedBackgroundArgb = intent.argb) }
            is ImageStitcherIntent.SelectMaxWidth -> _state.update { it.copy(selectedMaxWidth = intent.width) }
            is ImageStitcherIntent.SelectExportFormat -> _state.update { it.copy(exportFormat = intent.format) }
            is ImageStitcherIntent.UpdateJpegQuality -> _state.update { it.copy(jpegQualityInput = intent.quality) }
            is ImageStitcherIntent.MoveImage -> handleMoveImage(intent.fromIndex, intent.toIndex)
            
            is ImageStitcherIntent.GenerateResult -> generateResult()
            is ImageStitcherIntent.SaveResult -> saveResult()
            is ImageStitcherIntent.ShareResult -> shareResult()

            is ImageStitcherIntent.ToggleHistory -> _state.update { it.copy(isHistoryVisible = intent.visible) }
            is ImageStitcherIntent.UpdateHistorySearchQuery -> handleSearchQueryUpdate(intent.query)
            is ImageStitcherIntent.DeleteHistoryRecord -> deleteHistoryRecord(intent.record)
        }
    }

    private fun handleSearchQueryUpdate(query: String) {
        _state.update { it.copy(historySearchQuery = query) }
        _searchQueryFlow.value = query // 触发冷流防抖搜索
    }

    private fun deleteHistoryRecord(record: HistoryRecord) {
        viewModelScope.launch {
            try {
                ALog.i(TAG, "开始删除历史记录 id=${record.id}")
                val file = File(record.filePath)
                if (file.exists()) {
                    file.delete()
                }
                historyRepository.deleteRecord(record)
                ALog.i(TAG, "历史记录删除成功 id=${record.id}")
                emitEffect(ImageStitcherEffect.ShowSnackbarText("删除成功"))
            } catch (e: Exception) {
                logError("删除历史记录 id=${record.id}", e)
                emitEffect(ImageStitcherEffect.ShowSnackbarText("删除失败: ${e.message}"))
            }
        }
    }

    private fun handleImagesPicked(uris: List<Uri>) {
        // 先回收旧的 Bitmap，防止内存泄漏
        recycleBitmap(_state.value.resultBitmap)
        
        if (uris.isEmpty()) {
            ALog.w(TAG, "图片选择结果为空")
            emitEffect(ImageStitcherEffect.ShowSnackbar(R.string.pick_empty))
            return
        }

        ALog.i(TAG, "已选择 ${uris.size} 张图片")

        _state.update { currentState ->
            // 如果当前设置的列数大于选中的图片数量，自动调整列数为图片数量
            val newColumnsInput = if (currentState.appliedColumns > uris.size) {
                uris.size.toString()
            } else {
                currentState.columnsInput
            }
            
            currentState.copy(
                selectedUris = uris,
                resultBitmap = null, // 选择了新图片，清空旧结果
                columnsInput = newColumnsInput
            )
        }
        
        emitEffect(ImageStitcherEffect.ShowSnackbar(R.string.pick_success, listOf(uris.size)))
    }

    private fun handleClearImages() {
        ALog.i(TAG, "清空已选图片，数量=${_state.value.selectedUris.size}")
        recycleBitmap(_state.value.resultBitmap)
        _state.update { it.copy(selectedUris = emptyList(), resultBitmap = null) }
    }

    private fun handleMoveImage(fromIndex: Int, toIndex: Int) {
        _state.update { currentState ->
            val uris = currentState.selectedUris
            if (fromIndex !in uris.indices || toIndex !in uris.indices) return@update currentState

            val mutableList = uris.toMutableList()
            val item = mutableList.removeAt(fromIndex)
            mutableList.add(toIndex, item)
            
            currentState.copy(selectedUris = mutableList)
        }
    }

    private fun generateResult() {
        val currentState = _state.value
        if (!currentState.canGenerate) return

        // viewModelScope 会在 ViewModel 销毁时自动取消所有内部协程，避免内存泄漏
        viewModelScope.launch {
            val startTime = SystemClock.elapsedRealtime()
            ALog.i(
                TAG,
                "开始拼接：图片=${currentState.selectedUris.size}，列=${currentState.appliedColumns}，最大宽度=${currentState.selectedMaxWidth}px，格式=${currentState.exportFormat}",
            )
            _state.update { it.copy(isGenerating = true) }
            try {
                // dp 转 px 的逻辑
                val density = context.resources.displayMetrics.density
                val spacingPx = (currentState.appliedSpacingDp * density).toInt()
                val cornerRadiusPx = (currentState.appliedCornerRadiusDp * density).toInt()

                val options = StitchOptions(
                    columns = currentState.appliedColumns,
                    spacingPx = spacingPx,
                    backgroundColor = currentState.selectedBackgroundArgb,
                    maxOutputWidth = currentState.selectedMaxWidth,
                    cornerRadiusPx = cornerRadiusPx,
                    exportFormat = currentState.exportFormat,
                    jpegQuality = currentState.appliedJpegQuality
                )

                val newBitmap = stitchImages(context, currentState.selectedUris, options)
                
                // 异步保存一份本地历史记录
                saveHistoryRecord(newBitmap, currentState)

                // 回收旧 Bitmap
                recycleBitmap(currentState.resultBitmap)
                
                _state.update { it.copy(resultBitmap = newBitmap, isGenerating = false) }
                ALog.i(
                    TAG,
                    "拼接完成：${newBitmap.width}x${newBitmap.height}，耗时=${SystemClock.elapsedRealtime() - startTime}ms",
                )
                
                emitEffect(ImageStitcherEffect.ShowSnackbar(
                    R.string.result_size, 
                    listOf(newBitmap.width, newBitmap.height)
                ))
            } catch (error: Exception) {
                _state.update { it.copy(isGenerating = false) }
                logError("拼接图片", error)
                emitEffect(ImageStitcherEffect.ShowSnackbarText(error.message ?: context.getString(R.string.generating_result)))
            }
        }
    }

    private suspend fun saveHistoryRecord(bitmap: Bitmap, state: ImageStitcherState) {
        try {
            ALog.d(TAG, "开始写入历史记录：${bitmap.width}x${bitmap.height}")
            val historyDir = File(context.filesDir, "history_images").apply { mkdirs() }
            val fileName = "history_${System.currentTimeMillis()}.${state.exportFormat.extension}"
            val outputFile = File(historyDir, fileName)

            FileOutputStream(outputFile).use { outputStream ->
                bitmap.compress(state.exportFormat.compressFormat, 80, outputStream) // 压缩80%存储作为历史
            }

            val tags = "${state.appliedColumns}列 ${state.selectedMaxWidth}px ${state.exportFormat.name} ${state.selectedUris.size}张"
            
            val record = HistoryRecord(
                filePath = outputFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                imageCount = state.selectedUris.size,
                columns = state.appliedColumns,
                format = state.exportFormat.name,
                width = bitmap.width,
                height = bitmap.height,
                searchTags = tags
            )
            historyRepository.insertRecord(record)
            ALog.d(TAG, "历史记录写入成功")
        } catch (e: Exception) {
            logError("保存历史记录", e)
        }
    }

    private fun saveResult() {
        val currentState = _state.value
        val bitmap = currentState.resultBitmap ?: return
        if (currentState.isSaving) return

        viewModelScope.launch {
            ALog.i(TAG, "开始保存到相册：${bitmap.width}x${bitmap.height}，格式=${currentState.exportFormat}")
            _state.update { it.copy(isSaving = true) }
            try {
                saveBitmapToGallery(
                    context = context,
                    bitmap = bitmap,
                    exportFormat = currentState.exportFormat,
                    jpegQuality = currentState.appliedJpegQuality
                )
                ALog.i(TAG, "保存到相册成功")
                emitEffect(ImageStitcherEffect.ShowSnackbar(R.string.save_success))
            } catch (error: Exception) {
                logError("保存到相册", error)
                emitEffect(ImageStitcherEffect.ShowSnackbarText(error.message ?: context.getString(R.string.saving_result)))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun shareResult() {
        val currentState = _state.value
        val bitmap = currentState.resultBitmap ?: return
        if (currentState.isSharing) return

        // 触发 Share 效果，让 UI 层去启动分享 Intent，因为这通常涉及 Activity Context 和生命周期
        // 虽然直接在 ViewModel 启动 Intent 也可以，但交给 UI 处理更符合 MVI 职责分离
        ALog.i(TAG, "发起分享：${bitmap.width}x${bitmap.height}，格式=${currentState.exportFormat}")
        emitEffect(ImageStitcherEffect.ShareBitmap(bitmap, currentState.exportFormat, currentState.appliedJpegQuality))
    }

    private fun logError(action: String, error: Throwable) {
        ALog.e(TAG, "$action 失败：${error.message}\n${error.stackTraceToString()}")
    }

    /**
     * 发送 Effect
     */
    private fun emitEffect(effect: ImageStitcherEffect) {
        viewModelScope.launch {
            _effect.emit(effect)
        }
    }

    /**
     * 回收 Bitmap 内存
     */
    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时，确保回收最后的 Bitmap
        recycleBitmap(_state.value.resultBitmap)
    }
}
