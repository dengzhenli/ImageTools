package org.fatty.imagetools.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import org.fatty.imagetools.domain.ExportFormat

suspend fun shareBitmap(
    context: Context,
    bitmap: Bitmap,
    chooserTitle: String,
    exportFormat: ExportFormat,
    jpegQuality: Int,
) = withContext(Dispatchers.IO) {
    val shareDirectory = File(context.cacheDir, "shared_images").apply {
        mkdirs()
    }
    val outputFile = File(
        shareDirectory,
        "stitched_share_${System.currentTimeMillis()}.${exportFormat.extension}",
    )

    FileOutputStream(outputFile).use { outputStream ->
        check(bitmap.compress(exportFormat.compressFormat, jpegQuality.coerceIn(50, 100), outputStream)) {
            "无法导出分享图片"
        }
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outputFile,
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = exportFormat.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(chooserIntent)
}
