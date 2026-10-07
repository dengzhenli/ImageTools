package org.fatty.imagetools.log

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Persistent background task for MXLogger's on-disk files. The transport is deliberately a
 * placeholder for now; replace [LogUploadApi.upload] with the production multipart API call.
 */
class LogUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val logFiles = ALog.mxLogFilesForUpload()
        if (logFiles.isEmpty()) return Result.success()
        return when (LogUploadApi.upload(logFiles)) {
            UploadResult.Success -> Result.success()
            UploadResult.Retry -> Result.retry()
            UploadResult.Failure -> Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "log-upload"
    }
}

object LogUploadScheduler {
    /** Enqueues one network-constrained upload; repeated requests are coalesced. */
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<LogUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            LogUploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

private sealed interface UploadResult {
    data object Success : UploadResult
    data object Retry : UploadResult
    data object Failure : UploadResult
}

/** Temporary no-op transport. Replace this body with the real upload request. */
private object LogUploadApi {
    suspend fun upload(logFiles: List<java.io.File>): UploadResult {
        logFiles // Keeps the future multipart payload explicit until the API contract is available.
        return UploadResult.Success
    }
}
