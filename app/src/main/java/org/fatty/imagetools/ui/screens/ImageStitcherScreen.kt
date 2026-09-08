package org.fatty.imagetools.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import org.fatty.imagetools.R
import org.fatty.imagetools.domain.ExportFormat
import org.fatty.imagetools.domain.MAX_IMAGE_SELECTION
import org.fatty.imagetools.presentation.ImageStitcherEffect
import org.fatty.imagetools.presentation.ImageStitcherIntent
import org.fatty.imagetools.presentation.ImageStitcherViewModel
import org.fatty.imagetools.ui.theme.ImageToolsTheme
import org.fatty.imagetools.utils.shareBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import kotlin.math.ceil

/** 
 * UI 层常量定义，避免硬编码 
 */
private val outputWidthOptions = listOf(1080, 1440, 2160, 4096)

private data class BackgroundOption(
    val label: String,
    val color: Color,
)

private val backgroundOptions = listOf(
    BackgroundOption(label = "白色", color = Color.White),
    BackgroundOption(label = "黑色", color = Color(0xFF111111)),
    BackgroundOption(label = "浅灰", color = Color(0xFFF2F2F2)),
    BackgroundOption(label = "米黄", color = Color(0xFFF8F0DE)),
    BackgroundOption(label = "浅蓝", color = Color(0xFFEAF4FF)),
    BackgroundOption(label = "浅粉", color = Color(0xFFFFEFF4)),
)

/**
 * 什么是 @Composable？
 * 它是 Jetpack Compose 的核心注解。被它标记的函数可以把数据转化为 UI。
 * Compose 的工作原理是：当输入的数据（State）发生变化时，它会自动重新执行这个函数，
 * 以便刷新 UI。这个过程叫作重组 (Recomposition)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStitcherScreen(
    viewModel: ImageStitcherViewModel = viewModel() // 获取 ViewModel 实例，它与 Activity 生命周期绑定
) {
    // -------------------------------------------------------------------------
    // 1. 获取上下文和协程作用域
    // -------------------------------------------------------------------------
    
    // LocalContext.current 用于获取 Compose 树中当前节点所属的 Context (通常是 Activity)
    val context = LocalContext.current 
    
    // SnackbarHostState 用于控制和显示屏幕底部的 Snackbar (提示条)
    val snackbarHostState = remember { SnackbarHostState() }

    // -------------------------------------------------------------------------
    // 2. 观察 State 和 Effect (MVI 的核心)
    // -------------------------------------------------------------------------
    
    // collectAsState() 会把 ViewModel 里的 StateFlow 转换成 Compose 认识的 State。
    // 这样，只要 viewModel.state 里面的数据有任何改动，这个 Composable 就会自动刷新对应部分的 UI。
    val state by viewModel.state.collectAsState()

    // LaunchedEffect 是一个在 Composable 生命周期内启动协程的副作用 API。
    // 这里我们用它来收集 (collect) 一次性的 Effect 事件，比如弹 Toast 或分享。
    // 参数传 viewModel.effect 表示只要 effect 对象不变，这个协程就一直活着监听。
    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ImageStitcherEffect.ShowSnackbar -> {
                    val message = context.getString(effect.messageResId, *effect.formatArgs.toTypedArray())
                    snackbarHostState.showSnackbar(message)
                }
                is ImageStitcherEffect.ShowSnackbarText -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is ImageStitcherEffect.ShareBitmap -> {
                    // MVI 中，UI 负责处理与 Activity/Context 强相关的路由和分享操作
                    try {
                        shareBitmap(
                            context = context,
                            bitmap = effect.bitmap,
                            chooserTitle = context.getString(R.string.share_chooser_title),
                            exportFormat = effect.format,
                            jpegQuality = effect.quality
                        )
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(e.message ?: "分享失败")
                    }
                }
                is ImageStitcherEffect.ShareFile -> {
                    // 分享历史记录文件
                    // 这里可以复用 ShareUtils 中的逻辑或直接构建 Intent，为了简单可以暂时显示 Toast，
                    // 实际业务中应该读取文件并分享。
                    snackbarHostState.showSnackbar("文件准备分享: ${effect.filePath}")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. UI 交互发射器 (Launcher)
    // -------------------------------------------------------------------------
    
    // rememberLauncherForActivityResult 也是 Compose 的 API，用来替代传统的 onActivityResult。
    // 当用户选完图片回来后，闭包里面的代码就会执行，并将结果（uris）通过 Intent 传给 ViewModel。
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE_SELECTION),
    ) { uris ->
        viewModel.processIntent(ImageStitcherIntent.ImagesPicked(uris.take(MAX_IMAGE_SELECTION)))
    }

    // -------------------------------------------------------------------------
    // 4. 构建 UI 层级 (View)
    // -------------------------------------------------------------------------
    
    if (state.isHistoryVisible) {
        HistoryScreen(
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onBack = { viewModel.processIntent(ImageStitcherIntent.ToggleHistory(false)) }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(R.string.title_image_stitcher)) },
                    actions = {
                        IconButton(onClick = { viewModel.processIntent(ImageStitcherIntent.ToggleHistory(true)) }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "History")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
        
        // Column 就是垂直线性布局，里面的子元素从上到下排列
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // 给顶部和底部留出系统状态栏/导航栏的空间
                .verticalScroll(rememberScrollState()) // 让这个 Column 可以垂直滚动
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), // 子元素之间固定间隔 16dp
        ) {
            // 标题
            Text(
                text = stringResource(R.string.subtitle_image_stitcher),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 卡片一：图片选择区域
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.selected_count,
                            state.selectedUris.size,
                            MAX_IMAGE_SELECTION,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    // Row 就是水平线性布局，里面的子元素从左到右排列
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                // 点击按钮时，启动图片选择器
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !state.isGenerating && !state.isSaving && !state.isSharing,
                        ) {
                            Text(text = stringResource(R.string.pick_images))
                        }

                        OutlinedButton(
                            onClick = {
                                // 发送清空图片的 Intent
                                viewModel.processIntent(ImageStitcherIntent.ClearImages)
                            },
                            enabled = state.selectedUris.isNotEmpty() && !state.isGenerating && !state.isSaving && !state.isSharing,
                        ) {
                            Text(text = stringResource(R.string.clear_images))
                        }
                    }
                }
            }

            // 卡片二：拼图参数设置
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.column_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.column_hint, state.maxColumns),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // 列数调节控件
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val newCols = (state.appliedColumns - 1).coerceAtLeast(1).toString()
                                viewModel.processIntent(ImageStitcherIntent.UpdateColumns(newCols))
                            },
                            enabled = state.appliedColumns > 1 && !state.isGenerating && !state.isSaving && !state.isSharing,
                        ) {
                            Text(text = "-")
                        }

                        OutlinedTextField(
                            value = state.columnsInput,
                            onValueChange = { value ->
                                // 限制只能输入数字，最多 3 位，然后通过 Intent 更新 State
                                val filtered = value.filter(Char::isDigit).take(3)
                                viewModel.processIntent(ImageStitcherIntent.UpdateColumns(filtered))
                            },
                            modifier = Modifier.weight(1f), // weight(1f) 会占满剩余的水平空间
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.column_label)) },
                            isError = !state.isColumnValid,
                            supportingText = {
                                if (!state.isColumnValid) {
                                    Text(text = stringResource(R.string.invalid_columns))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        FilledTonalButton(
                            onClick = {
                                val newCols = (state.appliedColumns + 1).coerceAtMost(state.maxColumns).toString()
                                viewModel.processIntent(ImageStitcherIntent.UpdateColumns(newCols))
                            },
                            enabled = state.appliedColumns < state.maxColumns && !state.isGenerating && !state.isSaving && !state.isSharing,
                        ) {
                            Text(text = "+")
                        }
                    }

                    // 间距输入框
                    OutlinedTextField(
                        value = state.spacingInput,
                        onValueChange = { value ->
                            val filtered = value.filter(Char::isDigit).take(2)
                            viewModel.processIntent(ImageStitcherIntent.UpdateSpacing(filtered))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.spacing_label)) },
                        isError = !state.isSpacingValid,
                        supportingText = {
                            Text(
                                text = if (state.isSpacingValid) {
                                    stringResource(R.string.spacing_hint)
                                } else {
                                    stringResource(R.string.spacing_invalid)
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    // 圆角输入框
                    OutlinedTextField(
                        value = state.cornerRadiusInput,
                        onValueChange = { value ->
                            val filtered = value.filter(Char::isDigit).take(2)
                            viewModel.processIntent(ImageStitcherIntent.UpdateCornerRadius(filtered))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.corner_radius_label)) },
                        isError = !state.isCornerRadiusValid,
                        supportingText = {
                            Text(
                                text = if (state.isCornerRadiusValid) {
                                    stringResource(R.string.corner_radius_hint)
                                } else {
                                    stringResource(R.string.corner_radius_invalid)
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    // 背景颜色选择
                    Text(
                        text = stringResource(R.string.background_label),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()), // 让颜色块可以左右滑动
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        backgroundOptions.forEach { option ->
                            val isSelected = option.color.toArgb() == state.selectedBackgroundArgb
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable { 
                                        viewModel.processIntent(ImageStitcherIntent.SelectBackground(option.color.toArgb()))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(option.color)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(6.dp),
                                            ),
                                    )
                                    Text(text = option.label)
                                }
                            }
                        }
                    }

                    // 导出设置
                    Text(
                        text = stringResource(R.string.export_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = stringResource(
                            R.string.export_summary,
                            state.exportFormat.name,
                            state.selectedMaxWidth,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // 输出宽度
                    Text(
                        text = stringResource(R.string.output_width_label),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        outputWidthOptions.forEach { width ->
                            val isSelected = state.selectedMaxWidth == width
                            OutlinedButton(
                                onClick = { viewModel.processIntent(ImageStitcherIntent.SelectMaxWidth(width)) },
                                enabled = !state.isGenerating && !state.isSaving && !state.isSharing,
                            ) {
                                Text(
                                    text = if (isSelected) "${width}px *" else "${width}px",
                                )
                            }
                        }
                    }

                    // 输出格式
                    Text(
                        text = stringResource(R.string.output_format_label),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.processIntent(ImageStitcherIntent.SelectExportFormat(ExportFormat.PNG)) },
                            enabled = !state.isGenerating && !state.isSaving && !state.isSharing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (state.exportFormat == ExportFormat.PNG) {
                                    "${context.getString(R.string.format_png)} *"
                                } else {
                                    context.getString(R.string.format_png)
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.processIntent(ImageStitcherIntent.SelectExportFormat(ExportFormat.JPG)) },
                            enabled = !state.isGenerating && !state.isSaving && !state.isSharing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (state.exportFormat == ExportFormat.JPG) {
                                    "${context.getString(R.string.format_jpg)} *"
                                } else {
                                    context.getString(R.string.format_jpg)
                                },
                            )
                        }
                    }

                    // JPEG 质量，只有在选择了 JPG 格式时才显示
                    if (state.exportFormat == ExportFormat.JPG) {
                        OutlinedTextField(
                            value = state.jpegQualityInput,
                            onValueChange = { value ->
                                val filtered = value.filter(Char::isDigit).take(3)
                                viewModel.processIntent(ImageStitcherIntent.UpdateJpegQuality(filtered))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.jpg_quality_label)) },
                            isError = !state.isJpegQualityValid,
                            supportingText = {
                                Text(
                                    text = if (state.isJpegQualityValid) {
                                        stringResource(R.string.jpg_quality_hint)
                                    } else {
                                        stringResource(R.string.jpg_quality_invalid)
                                    },
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    // 核心按钮：生成结果
                    Button(
                        onClick = { viewModel.processIntent(ImageStitcherIntent.GenerateResult) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canGenerate, // 当各项参数合法且没在生成中时才可点击
                    ) {
                        // 如果正在生成，按钮内部显示一个小菊花 (Loading)
                        if (state.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(
                                if (state.isGenerating) {
                                    R.string.generating_result
                                } else {
                                    R.string.generate_result
                                },
                            ),
                        )
                    }
                }
            }

            // 卡片三：已选图片预览排序区
            if (state.selectedUris.isNotEmpty()) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.selected_images),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.selected_order_tip),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val previewRows = ceil(state.selectedUris.size / 2f).toInt().coerceAtLeast(1)
                        // LazyVerticalGrid 相当于以前的 RecyclerView 的 GridLayoutManager，用于渲染网格列表
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2), // 固定 2 列
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((previewRows * 188).dp),
                            userScrollEnabled = false,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.selectedUris.size) { index ->
                                val uri = state.selectedUris[index]
                                Card {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        // AsyncImage (来自 Coil 库) 负责异步加载和缓存图片
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = stringResource(R.string.preview_image),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .fillMaxWidth()
                                                .height(100.dp),
                                            contentScale = ContentScale.Crop, // 居中裁剪，铺满区域
                                        )
                                        Text(
                                            text = stringResource(R.string.position_label, index + 1),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.processIntent(ImageStitcherIntent.MoveImage(index, index - 1))
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = index > 0 && !state.isGenerating && !state.isSaving && !state.isSharing,
                                            ) {
                                                Text(text = stringResource(R.string.reorder_left))
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.processIntent(ImageStitcherIntent.MoveImage(index, index + 1))
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = index < state.selectedUris.lastIndex && !state.isGenerating && !state.isSaving && !state.isSharing,
                                            ) {
                                                Text(text = stringResource(R.string.reorder_right))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 如果还没选图片，显示一个空状态提示
                Card {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.empty_state),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 卡片四：生成后的结果预览及保存分享区
            // state.resultBitmap?.let { bitmap -> 表示只有当 resultBitmap 不为 null 时，才渲染这部分 UI
            state.resultBitmap?.let { bitmap ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.result_preview),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.result_size, bitmap.width, bitmap.height),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // 预览生成的 Bitmap 图片
                        Image(
                            bitmap = bitmap.asImageBitmap(), // Compose 使用的图片格式是 ImageBitmap，需要通过 asImageBitmap() 转换
                            contentDescription = stringResource(R.string.preview_result),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 640.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Fit, // 适应大小，不裁剪
                        )

                        Button(
                            onClick = { viewModel.processIntent(ImageStitcherIntent.SaveResult) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving && !state.isGenerating && !state.isSharing,
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(
                                    if (state.isSaving) R.string.saving_result else R.string.save_result
                                ),
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.processIntent(ImageStitcherIntent.ShareResult) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving && !state.isGenerating && !state.isSharing,
                        ) {
                            if (state.isSharing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(
                                    if (state.isSharing) R.string.sharing_result else R.string.share_result
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImageStitcherScreenPreview() {
    ImageToolsTheme {
        ImageStitcherScreen()
    }
}

// 最后补充HistoryScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: ImageStitcherViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.historySearchQuery,
                onValueChange = { viewModel.processIntent(ImageStitcherIntent.UpdateHistorySearchQuery(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索关键词 (如: PNG, 1080px)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            if (state.historyRecords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.historyRecords.size) { index ->
                        val record = state.historyRecords[index]
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                AsyncImage(
                                    model = File(record.filePath),
                                    contentDescription = "历史图片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = dateFormatter.format(Date(record.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${record.width}x${record.height} ${record.format}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(onClick = {
                                        viewModel.processIntent(ImageStitcherIntent.DeleteHistoryRecord(record))
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    IconButton(onClick = {
                                        viewModel.processIntent(
                                            ImageStitcherIntent.ShareResult
                                        )
                                    }) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Share",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
