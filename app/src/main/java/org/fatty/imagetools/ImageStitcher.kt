package org.fatty.imagetools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val MAX_IMAGE_SELECTION = 100

private const val BASE_CELL_WIDTH = 1080
private const val DEFAULT_MAX_OUTPUT_WIDTH = 4096
private const val MAX_OUTPUT_PIXELS = 18_000_000L
private const val MIN_CELL_WIDTH = 160

enum class ExportFormat(
    val mimeType: String,
    val extension: String,
    val compressFormat: Bitmap.CompressFormat,
) {
    PNG(
        mimeType = "image/png",
        extension = "png",
        compressFormat = Bitmap.CompressFormat.PNG,
    ),
    JPG(
        mimeType = "image/jpeg",
        extension = "jpg",
        compressFormat = Bitmap.CompressFormat.JPEG,
    ),
}

data class StitchOptions(
    val columns: Int,
    val spacingPx: Int,
    val backgroundColor: Int,
    val maxOutputWidth: Int = DEFAULT_MAX_OUTPUT_WIDTH,
    val cornerRadiusPx: Int = 0,
    val exportFormat: ExportFormat = ExportFormat.PNG,
    val jpegQuality: Int = 95,
)

data class ImageSize(
    val width: Int,
    val height: Int,
)

suspend fun stitchImages(
    context: Context,
    imageUris: List<Uri>,
    options: StitchOptions,
): Bitmap = withContext(Dispatchers.IO) {
    require(imageUris.isNotEmpty()) { "至少需要选择一张图片" }

    val safeColumns = options.columns.coerceIn(1, imageUris.size)
    val spacingPx = options.spacingPx.coerceAtLeast(0)
    val cornerRadiusPx = options.cornerRadiusPx.coerceAtLeast(0)
    val resolver = context.contentResolver
    val sizes = imageUris.map { uri -> readImageSize(resolver, uri) }
    val targetCellWidth = calculateCellWidth(
        sizes = sizes,
        columns = safeColumns,
        spacingPx = spacingPx,
        maxOutputWidth = options.maxOutputWidth.coerceAtLeast(safeColumns * MIN_CELL_WIDTH),
    )
    val rowChunks = sizes.chunked(safeColumns)
    val rowHeights = rowChunks.map { row ->
        row.maxOf { size ->
            max(1, (size.height.toFloat() / size.width * targetCellWidth).roundToInt())
        }
    }
    val outputWidth = targetCellWidth * safeColumns + spacingPx * (safeColumns + 1)
    val outputHeight = rowHeights.sum().coerceAtLeast(1) + spacingPx * (rowHeights.size + 1)

    val result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(options.backgroundColor)

    var currentTop = spacingPx
    var index = 0

    rowHeights.forEach { rowHeight ->
        for (columnIndex in 0 until safeColumns) {
            if (index >= imageUris.size) {
                break
            }

            val sourceSize = sizes[index]
            val targetHeight = max(1, (sourceSize.height.toFloat() / sourceSize.width * targetCellWidth).roundToInt())
            val cellBitmap = decodeScaledBitmap(
                context = context,
                uri = imageUris[index],
                targetWidth = targetCellWidth,
                targetHeight = targetHeight,
            )

            val x = spacingPx + columnIndex * (targetCellWidth + spacingPx)
            drawBitmapInCell(
                canvas = canvas,
                bitmap = cellBitmap,
                left = x.toFloat(),
                top = currentTop.toFloat(),
                width = targetCellWidth.toFloat(),
                height = targetHeight.toFloat(),
                cornerRadius = cornerRadiusPx.toFloat(),
            )
            cellBitmap.recycle()
            index += 1
        }
        currentTop += rowHeight + spacingPx
    }

    result
}

suspend fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    exportFormat: ExportFormat,
    jpegQuality: Int,
): Uri = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val fileName = "stitched_${System.currentTimeMillis()}.${exportFormat.extension}"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, exportFormat.mimeType)
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "${Environment.DIRECTORY_PICTURES}/ImageTools",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建输出文件")

    try {
        resolver.openOutputStream(outputUri)?.use { outputStream ->
            check(bitmap.compress(exportFormat.compressFormat, jpegQuality.coerceIn(50, 100), outputStream)) {
                "写入图片失败"
            }
        } ?: error("无法打开输出流")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val completedValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(outputUri, completedValues, null, null)
        }

        outputUri
    } catch (error: Exception) {
        resolver.delete(outputUri, null, null)
        throw error
    }
}

private fun calculateCellWidth(
    sizes: List<ImageSize>,
    columns: Int,
    spacingPx: Int,
    maxOutputWidth: Int,
): Int {
    val availableWidth = (maxOutputWidth - spacingPx * (columns + 1))
        .coerceAtLeast(columns * MIN_CELL_WIDTH)
    val widthByColumns = max(1, floor(availableWidth.toFloat() / columns).toInt())
    var cellWidth = minOf(BASE_CELL_WIDTH, widthByColumns)
    var estimatedPixels = estimateOutputPixels(sizes, columns, cellWidth, spacingPx)

    if (estimatedPixels > MAX_OUTPUT_PIXELS) {
        val scale = sqrt(MAX_OUTPUT_PIXELS.toDouble() / estimatedPixels.toDouble())
        cellWidth = max(MIN_CELL_WIDTH, floor(cellWidth * scale).toInt())
        estimatedPixels = estimateOutputPixels(sizes, columns, cellWidth, spacingPx)
    }

    while (estimatedPixels > MAX_OUTPUT_PIXELS && cellWidth > MIN_CELL_WIDTH) {
        cellWidth -= 16
        estimatedPixels = estimateOutputPixels(sizes, columns, cellWidth, spacingPx)
    }

    return cellWidth.coerceAtLeast(MIN_CELL_WIDTH)
}

private fun drawBitmapInCell(
    canvas: Canvas,
    bitmap: Bitmap,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    cornerRadius: Float,
) {
    if (cornerRadius <= 0f) {
        canvas.drawBitmap(bitmap, left, top, null)
        return
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    val rect = RectF(left, top, left + width, top + height)
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
}

private fun estimateOutputPixels(
    sizes: List<ImageSize>,
    columns: Int,
    cellWidth: Int,
    spacingPx: Int,
): Long {
    val rowHeights = sizes
        .chunked(columns)
        .map { row ->
            row.maxOf { size ->
                max(1, (size.height.toFloat() / size.width * cellWidth).roundToInt())
            }
        }
    val totalHeight = rowHeights.sum() + spacingPx * (rowHeights.size + 1)
    val totalWidth = cellWidth * columns + spacingPx * (columns + 1)

    return totalWidth.toLong() * totalHeight.toLong()
}

private fun readImageSize(
    resolver: android.content.ContentResolver,
    uri: Uri,
): ImageSize {
    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    val inputStream = resolver.openInputStream(uri) ?: error("无法打开图片")
    inputStream.use {
        BitmapFactory.decodeStream(inputStream, null, boundsOptions)
    }

    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        error("无法读取图片信息")
    }

    val rawWidth = boundsOptions.outWidth
    val rawHeight = boundsOptions.outHeight
    val orientation = readImageOrientation(resolver, uri)
    val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
        orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE

    return if (rotated) {
        ImageSize(width = rawHeight, height = rawWidth)
    } else {
        ImageSize(width = rawWidth, height = rawHeight)
    }
}

private fun decodeScaledBitmap(
    context: Context,
    uri: Uri,
    targetWidth: Int,
    targetHeight: Int,
): Bitmap {
    val resolver = context.contentResolver
    val sourceSize = readImageSize(resolver, uri)
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            sourceWidth = sourceSize.width,
            sourceHeight = sourceSize.height,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val decoded = resolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, decodeOptions)
    } ?: error("无法解码图片")

    val oriented = applyExifOrientation(resolver, uri, decoded)
    val scaledHeight = max(1, oriented.height * targetWidth / oriented.width)
    val scaled = Bitmap.createScaledBitmap(oriented, targetWidth, scaledHeight, true)
    if (scaled !== oriented) {
        oriented.recycle()
    }
    return scaled
}

private fun calculateInSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): Int {
    var sampleSize = 1
    var halfWidth = sourceWidth / 2
    var halfHeight = sourceHeight / 2

    while (halfWidth / sampleSize >= targetWidth && halfHeight / sampleSize >= targetHeight) {
        sampleSize *= 2
    }

    return sampleSize.coerceAtLeast(1)
}

private fun applyExifOrientation(
    resolver: android.content.ContentResolver,
    uri: Uri,
    bitmap: Bitmap,
): Bitmap {
    val matrix = Matrix()

    when (readImageOrientation(resolver, uri)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.preScale(-1f, 1f)
            matrix.postRotate(270f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.preScale(-1f, 1f)
            matrix.postRotate(90f)
        }
        else -> return bitmap
    }

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) {
            bitmap.recycle()
        }
    }
}

private fun readImageOrientation(
    resolver: android.content.ContentResolver,
    uri: Uri,
): Int {
    return resolver.openInputStream(uri)?.use { inputStream ->
        ExifInterface(inputStream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    } ?: ExifInterface.ORIENTATION_NORMAL
}
