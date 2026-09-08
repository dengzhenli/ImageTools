package org.fatty.imagetools.presentation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fatty.imagetools.R
import org.fatty.imagetools.domain.StitchOptions
import org.fatty.imagetools.domain.stitchImages
import org.fatty.imagetools.domain.saveBitmapToGallery

/**
 * ViewModel 负责管理 State 并处理 Intent。
 *
 * 为什么用 AndroidViewModel？
 * 因为我们在处理图片拼接 (stitchImages) 和保存 (saveBitmapToGallery) 时需要用到 Context。
 * AndroidViewModel 默认持有一个 Application 级别的 Context，既满足需求，又不会像 Activity Context 那样容易引起内存泄漏。
 */
class ImageStitcherViewModel(application: Application) : AndroidViewModel(application) {

    // _state 是内部可变的 StateFlow，确保只有 ViewModel 能够修改状态
    private val _state = MutableStateFlow(ImageStitcherState())
    
    // state 是暴露给外部（UI层）的只读 StateFlow，UI 只能观察它，不能直接修改它
    val state: StateFlow<ImageStitcherState> = _state.asStateFlow()

    // _effect 用于发送一次性事件（如弹 Toast、分享）。SharedFlow 适合处理事件，因为它不会像 StateFlow 那样保留最新的值。
    private val _effect = MutableSharedFlow<ImageStitcherEffect>()
    val effect: SharedFlow<ImageStitcherEffect> = _effect.asSharedFlow()

    // Context 提供给内部耗时任务使用
    private val context get() = getApplication<Application>()

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
        }
    }

    private fun handleImagesPicked(uris: List<Uri>) {
        // 先回收旧的 Bitmap，防止内存泄漏
        recycleBitmap(_state.value.resultBitmap)
        
        if (uris.isEmpty()) {
            emitEffect(ImageStitcherEffect.ShowSnackbar(R.string.pick_empty))
            return
        }

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
                
                // 回收旧 Bitmap
                recycleBitmap(currentState.resultBitmap)
                
                _state.update { it.copy(resultBitmap = newBitmap, isGenerating = false) }
                
                emitEffect(ImageStitcherEffect.ShowSnackbar(
                    R.string.result_size, 
                    listOf(newBitmap.width, newBitmap.height)
                ))
            } catch (error: Exception) {
                _state.update { it.copy(isGenerating = false) }
                emitEffect(ImageStitcherEffect.ShowSnackbarText(error.message ?: context.getString(R.string.generating_result)))
            }
        }
    }

    private fun saveResult() {
        val currentState = _state.value
        val bitmap = currentState.resultBitmap ?: return
        if (currentState.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                saveBitmapToGallery(
                    context = context,
                    bitmap = bitmap,
                    exportFormat = currentState.exportFormat,
                    jpegQuality = currentState.appliedJpegQuality
                )
                emitEffect(ImageStitcherEffect.ShowSnackbar(R.string.save_success))
            } catch (error: Exception) {
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
        emitEffect(ImageStitcherEffect.ShareBitmap(bitmap, currentState.exportFormat, currentState.appliedJpegQuality))
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
