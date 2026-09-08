package org.fatty.imagetools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.fatty.imagetools.ui.theme.ImageToolsTheme
import kotlin.math.ceil

private const val MAX_SPACING_DP = 64
private const val MAX_CORNER_RADIUS_DP = 64

private val outputWidthOptions = listOf(1080, 1440, 2160, 4096)

private val UriListSaver = listSaver<List<Uri>, String>(
    save = { uris -> uris.map(Uri::toString) },
    restore = { values -> values.map(Uri::parse) },
)

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageToolsTheme {
                ImageStitcherScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStitcherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedUris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(emptyList()) }
    var columnsInput by rememberSaveable { mutableStateOf("2") }
    var spacingInput by rememberSaveable { mutableStateOf("0") }
    var cornerRadiusInput by rememberSaveable { mutableStateOf("0") }
    var selectedMaxWidth by rememberSaveable { mutableStateOf(outputWidthOptions.last()) }
    var exportFormatName by rememberSaveable { mutableStateOf(ExportFormat.PNG.name) }
    var jpegQualityInput by rememberSaveable { mutableStateOf("95") }
    var selectedBackgroundArgb by rememberSaveable {
        mutableStateOf(backgroundOptions.first().color.toArgb())
    }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            recycleBitmap(resultBitmap)
        }
    }

    val maxColumns = selectedUris.size.coerceAtLeast(1)
    val parsedColumns = columnsInput.toIntOrNull()
    val isColumnValid = parsedColumns != null && parsedColumns in 1..maxColumns
    val appliedColumns = (parsedColumns ?: 1).coerceIn(1, maxColumns)
    val parsedSpacing = spacingInput.toIntOrNull()
    val isSpacingValid = parsedSpacing != null && parsedSpacing in 0..MAX_SPACING_DP
    val appliedSpacingDp = (parsedSpacing ?: 0).coerceIn(0, MAX_SPACING_DP)
    val parsedCornerRadius = cornerRadiusInput.toIntOrNull()
    val isCornerRadiusValid = parsedCornerRadius != null && parsedCornerRadius in 0..MAX_CORNER_RADIUS_DP
    val appliedCornerRadiusDp = (parsedCornerRadius ?: 0).coerceIn(0, MAX_CORNER_RADIUS_DP)
    val exportFormat = ExportFormat.valueOf(exportFormatName)
    val parsedJpegQuality = jpegQualityInput.toIntOrNull()
    val isJpegQualityValid = exportFormat == ExportFormat.PNG ||
        (parsedJpegQuality != null && parsedJpegQuality in 50..100)
    val appliedJpegQuality = (parsedJpegQuality ?: 95).coerceIn(50, 100)
    val canGenerate = selectedUris.isNotEmpty() &&
        isColumnValid &&
        isSpacingValid &&
        isCornerRadiusValid &&
        isJpegQualityValid &&
        !isGenerating

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE_SELECTION),
    ) { uris ->
        recycleBitmap(resultBitmap)
        resultBitmap = null
        selectedUris = uris.take(MAX_IMAGE_SELECTION)

        if (selectedUris.isEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.pick_empty))
            }
            return@rememberLauncherForActivityResult
        }

        if (appliedColumns > selectedUris.size) {
            columnsInput = selectedUris.size.toString()
        }

        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.pick_success, selectedUris.size),
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.title_image_stitcher),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.subtitle_image_stitcher),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                            selectedUris.size,
                            MAX_IMAGE_SELECTION,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !isGenerating && !isSaving && !isSharing,
                        ) {
                            Text(text = stringResource(R.string.pick_images))
                        }

                        OutlinedButton(
                            onClick = {
                                recycleBitmap(resultBitmap)
                                resultBitmap = null
                                selectedUris = emptyList()
                            },
                            enabled = selectedUris.isNotEmpty() && !isGenerating && !isSaving && !isSharing,
                        ) {
                            Text(text = stringResource(R.string.clear_images))
                        }
                    }
                }
            }

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
                        text = stringResource(R.string.column_hint, maxColumns),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                columnsInput = (appliedColumns - 1).coerceAtLeast(1).toString()
                            },
                            enabled = appliedColumns > 1 && !isGenerating && !isSaving && !isSharing,
                        ) {
                            Text(text = "-")
                        }

                        OutlinedTextField(
                            value = columnsInput,
                            onValueChange = { value ->
                                columnsInput = value.filter(Char::isDigit).take(3)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.column_label)) },
                            isError = !isColumnValid,
                            supportingText = {
                                if (!isColumnValid) {
                                    Text(text = stringResource(R.string.invalid_columns))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        FilledTonalButton(
                            onClick = {
                                columnsInput = (appliedColumns + 1).coerceAtMost(maxColumns).toString()
                            },
                            enabled = appliedColumns < maxColumns && !isGenerating && !isSaving && !isSharing,
                        ) {
                            Text(text = "+")
                        }
                    }

                    OutlinedTextField(
                        value = spacingInput,
                        onValueChange = { value ->
                            spacingInput = value.filter(Char::isDigit).take(2)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.spacing_label)) },
                        isError = !isSpacingValid,
                        supportingText = {
                            Text(
                                text = if (isSpacingValid) {
                                    stringResource(R.string.spacing_hint)
                                } else {
                                    stringResource(R.string.spacing_invalid)
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    OutlinedTextField(
                        value = cornerRadiusInput,
                        onValueChange = { value ->
                            cornerRadiusInput = value.filter(Char::isDigit).take(2)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.corner_radius_label)) },
                        isError = !isCornerRadiusValid,
                        supportingText = {
                            Text(
                                text = if (isCornerRadiusValid) {
                                    stringResource(R.string.corner_radius_hint)
                                } else {
                                    stringResource(R.string.corner_radius_invalid)
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    Text(
                        text = stringResource(R.string.background_label),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        backgroundOptions.forEach { option ->
                            val isSelected = option.color.toArgb() == selectedBackgroundArgb
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
                                    .clickable { selectedBackgroundArgb = option.color.toArgb() }
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

                    Text(
                        text = stringResource(R.string.export_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = stringResource(
                            R.string.export_summary,
                            exportFormat.name,
                            selectedMaxWidth,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

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
                            val isSelected = selectedMaxWidth == width
                            OutlinedButton(
                                onClick = { selectedMaxWidth = width },
                                enabled = !isGenerating && !isSaving && !isSharing,
                            ) {
                                Text(
                                    text = if (isSelected) "${width}px *" else "${width}px",
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.output_format_label),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { exportFormatName = ExportFormat.PNG.name },
                            enabled = !isGenerating && !isSaving && !isSharing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (exportFormat == ExportFormat.PNG) {
                                    "${context.getString(R.string.format_png)} *"
                                } else {
                                    context.getString(R.string.format_png)
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = { exportFormatName = ExportFormat.JPG.name },
                            enabled = !isGenerating && !isSaving && !isSharing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (exportFormat == ExportFormat.JPG) {
                                    "${context.getString(R.string.format_jpg)} *"
                                } else {
                                    context.getString(R.string.format_jpg)
                                },
                            )
                        }
                    }

                    if (exportFormat == ExportFormat.JPG) {
                        OutlinedTextField(
                            value = jpegQualityInput,
                            onValueChange = { value ->
                                jpegQualityInput = value.filter(Char::isDigit).take(3)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.jpg_quality_label)) },
                            isError = !isJpegQualityValid,
                            supportingText = {
                                Text(
                                    text = if (isJpegQualityValid) {
                                        stringResource(R.string.jpg_quality_hint)
                                    } else {
                                        stringResource(R.string.jpg_quality_invalid)
                                    },
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isGenerating = true
                                try {
                                    val newBitmap = stitchImages(
                                        context = context,
                                        imageUris = selectedUris,
                                        options = StitchOptions(
                                            columns = appliedColumns,
                                            spacingPx = dpToPx(context, appliedSpacingDp),
                                            backgroundColor = selectedBackgroundArgb,
                                            maxOutputWidth = selectedMaxWidth,
                                            cornerRadiusPx = dpToPx(context, appliedCornerRadiusDp),
                                            exportFormat = exportFormat,
                                            jpegQuality = appliedJpegQuality,
                                        ),
                                    )
                                    recycleBitmap(resultBitmap)
                                    resultBitmap = newBitmap
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.result_size,
                                            newBitmap.width,
                                            newBitmap.height,
                                        ),
                                    )
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar(
                                        error.message ?: context.getString(R.string.generating_result),
                                    )
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canGenerate,
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(
                                if (isGenerating) {
                                    R.string.generating_result
                                } else {
                                    R.string.generate_result
                                },
                            ),
                        )
                    }
                }
            }

            if (selectedUris.isNotEmpty()) {
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

                        val previewRows = ceil(selectedUris.size / 2f).toInt().coerceAtLeast(1)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((previewRows * 188).dp),
                            userScrollEnabled = false,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(selectedUris.size) { index ->
                                val uri = selectedUris[index]
                                Card {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = stringResource(R.string.preview_image),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .fillMaxWidth()
                                                .height(100.dp),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Text(
                                            text = stringResource(R.string.position_label, index + 1),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedUris = moveUri(selectedUris, index, index - 1)
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = index > 0 && !isGenerating && !isSaving && !isSharing,
                                            ) {
                                                Text(text = stringResource(R.string.reorder_left))
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    selectedUris = moveUri(selectedUris, index, index + 1)
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = index < selectedUris.lastIndex && !isGenerating && !isSaving && !isSharing,
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

            resultBitmap?.let { bitmap ->
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

                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.preview_result),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 640.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Fit,
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        saveBitmapToGallery(
                                            context = context,
                                            bitmap = bitmap,
                                            exportFormat = exportFormat,
                                            jpegQuality = appliedJpegQuality,
                                        )
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.save_success),
                                        )
                                    } catch (error: Exception) {
                                        snackbarHostState.showSnackbar(
                                            error.message ?: context.getString(R.string.saving_result),
                                        )
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving && !isGenerating && !isSharing,
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(
                                    if (isSaving) {
                                        R.string.saving_result
                                    } else {
                                        R.string.save_result
                                    },
                                ),
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isSharing = true
                                    try {
                                        shareBitmap(
                                            context = context,
                                            bitmap = bitmap,
                                            chooserTitle = context.getString(R.string.share_chooser_title),
                                            exportFormat = exportFormat,
                                            jpegQuality = appliedJpegQuality,
                                        )
                                    } catch (error: Exception) {
                                        snackbarHostState.showSnackbar(
                                            error.message ?: context.getString(R.string.sharing_result),
                                        )
                                    } finally {
                                        isSharing = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving && !isGenerating && !isSharing,
                        ) {
                            if (isSharing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(
                                    if (isSharing) {
                                        R.string.sharing_result
                                    } else {
                                        R.string.share_result
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}

private fun moveUri(items: List<Uri>, fromIndex: Int, toIndex: Int): List<Uri> {
    if (fromIndex !in items.indices || toIndex !in items.indices) {
        return items
    }

    val mutable = items.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

private fun recycleBitmap(bitmap: Bitmap?) {
    if (bitmap != null && !bitmap.isRecycled) {
        bitmap.recycle()
    }
}

@Preview(showBackground = true)
@Composable
fun ImageStitcherScreenPreview() {
    ImageToolsTheme {
        ImageStitcherScreen()
    }
}
