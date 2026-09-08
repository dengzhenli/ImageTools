package org.fatty.imagetools.presentation

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.fatty.imagetools.domain.ExportFormat

/**
 * MVI 架构契约 (Contract)
 * 
 * 什么是 MVI？
 * MVI 是 Model-View-Intent 的缩写，是当前 Android 推荐的响应式架构。
 * - Model (State): 描述界面当前应该长什么样（即数据状态）。
 * - View: 界面（Compose），它只负责根据 State 渲染 UI，并在用户操作时发送 Intent。
 * - Intent: 用户的意图（点击按钮、输入文本等），它会发送给 ViewModel 处理。
 * - Effect (Side Effect): 一次性事件，比如弹出一个 Toast 或者跳转页面。与 State 不同，Effect 不需要持续保持。
 */

/**
 * 界面状态 (State / Model)
 *
 * 包含了界面上所有需要展示的数据。View 会观察这个状态，只要状态改变，View 就会自动重新渲染 (Recompose)。
 */
data class ImageStitcherState(
    val selectedUris: List<Uri> = emptyList(), // 已选择的图片路径列表
    val columnsInput: String = "2",            // 用户输入的列数
    val spacingInput: String = "0",            // 用户输入的间距
    val cornerRadiusInput: String = "0",       // 用户输入的圆角大小
    val selectedMaxWidth: Int = 4096,          // 用户选择的输出最大宽度
    val exportFormat: ExportFormat = ExportFormat.PNG, // 用户选择的导出格式
    val jpegQualityInput: String = "95",       // 用户输入的 JPEG 质量
    val selectedBackgroundArgb: Int = Color.White.toArgb(), // 用户选择的背景颜色 (ARGB)
    
    val resultBitmap: Bitmap? = null,          // 拼接完成后生成的图片
    val isGenerating: Boolean = false,         // 是否正在生成图片（用于显示 Loading）
    val isSaving: Boolean = false,             // 是否正在保存图片
    val isSharing: Boolean = false             // 是否正在分享图片
) {
    // 根据当前状态计算得出的一些辅助属性，方便 UI 直接使用
    val maxColumns: Int get() = selectedUris.size.coerceAtLeast(1)
    
    val parsedColumns: Int? get() = columnsInput.toIntOrNull()
    val isColumnValid: Boolean get() = parsedColumns != null && parsedColumns in 1..maxColumns
    val appliedColumns: Int get() = (parsedColumns ?: 1).coerceIn(1, maxColumns)
    
    val parsedSpacing: Int? get() = spacingInput.toIntOrNull()
    val isSpacingValid: Boolean get() = parsedSpacing != null && parsedSpacing in 0..64
    val appliedSpacingDp: Int get() = (parsedSpacing ?: 0).coerceIn(0, 64)
    
    val parsedCornerRadius: Int? get() = cornerRadiusInput.toIntOrNull()
    val isCornerRadiusValid: Boolean get() = parsedCornerRadius != null && parsedCornerRadius in 0..64
    val appliedCornerRadiusDp: Int get() = (parsedCornerRadius ?: 0).coerceIn(0, 64)
    
    val parsedJpegQuality: Int? get() = jpegQualityInput.toIntOrNull()
    val isJpegQualityValid: Boolean get() = exportFormat == ExportFormat.PNG || 
        (parsedJpegQuality != null && parsedJpegQuality in 50..100)
    val appliedJpegQuality: Int get() = (parsedJpegQuality ?: 95).coerceIn(50, 100)
    
    val canGenerate: Boolean get() = selectedUris.isNotEmpty() && 
        isColumnValid && isSpacingValid && isCornerRadiusValid && isJpegQualityValid && !isGenerating
}

/**
 * 用户意图 (Intent)
 *
 * 代表了用户在界面上执行的所有操作。ViewModel 会接收并处理这些意图。
 * 使用 sealed interface (密封接口) 可以限制子类的类型，确保处理时能够穷举所有情况。
 */
sealed interface ImageStitcherIntent {
    data class ImagesPicked(val uris: List<Uri>) : ImageStitcherIntent // 用户在图库中选择了一些图片
    object ClearImages : ImageStitcherIntent                           // 用户点击了"清空图片"
    data class UpdateColumns(val columns: String) : ImageStitcherIntent // 用户更新了列数输入框
    data class UpdateSpacing(val spacing: String) : ImageStitcherIntent // 用户更新了间距输入框
    data class UpdateCornerRadius(val radius: String) : ImageStitcherIntent // 用户更新了圆角输入框
    data class SelectBackground(val argb: Int) : ImageStitcherIntent   // 用户选择了背景颜色
    data class SelectMaxWidth(val width: Int) : ImageStitcherIntent    // 用户选择了最大宽度
    data class SelectExportFormat(val format: ExportFormat) : ImageStitcherIntent // 用户选择了导出格式
    data class UpdateJpegQuality(val quality: String) : ImageStitcherIntent // 用户更新了 JPEG 质量
    data class MoveImage(val fromIndex: Int, val toIndex: Int) : ImageStitcherIntent // 用户在预览中移动了图片的顺序
    
    // 执行耗时操作的意图
    object GenerateResult : ImageStitcherIntent // 用户点击"生成结果"
    object SaveResult : ImageStitcherIntent     // 用户点击"保存到相册"
    object ShareResult : ImageStitcherIntent    // 用户点击"分享"
}

/**
 * 一次性事件 (Effect / Side Effect)
 *
 * 与 State 不同，Effect 是一次性的。比如：提示一条信息 (Snackbar)、弹出一个系统分享框。
 * 它们不应该保存在 State 中，因为 State 恢复时（如屏幕旋转）不应该重新弹出 Toast。
 */
sealed interface ImageStitcherEffect {
    data class ShowSnackbar(val messageResId: Int, val formatArgs: List<Any> = emptyList()) : ImageStitcherEffect // 显示提示文本（使用资源ID）
    data class ShowSnackbarText(val message: String) : ImageStitcherEffect // 显示纯文本提示
    data class ShareBitmap(val bitmap: Bitmap, val format: ExportFormat, val quality: Int) : ImageStitcherEffect // 调起系统分享
}
