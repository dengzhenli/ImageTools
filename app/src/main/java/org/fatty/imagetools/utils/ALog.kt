package org.fatty.imagetools.utils

import android.content.Context
import android.util.Log
import com.dongjiayi.mxlogger.MXLogger
import com.dongjiayi.mxlogger.MXStoragePolicyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.fatty.imagetools.BuildConfig

object ALog {
    private const val LOG_NAME = "alog"
    private const val NAMESPACE = "org.fatty.imagetools"
    private const val FILE_NAME = "mxlog"
    private const val FILE_HEADER = "{\"app_version\":\"2.0.0\"}"
    // MXLogger currently accepts a raw symmetric key. Replace this with a keystore-backed
    // provider when the library supports non-exportable Android Keystore keys.
    private const val CRYPT_KEY = "jsgfbhvjdhfngmnf"
    private const val IV = "durhfjgkdhfngjvh"

    private lateinit var logger: MXLogger
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isReady = false

    fun init(context: Context) {
        runCatching {
            MXLogger.initialize(
                context.applicationContext,
                NAMESPACE,
                null,
                MXStoragePolicyType.YYYY_MM_DD,
                FILE_NAME,
                FILE_HEADER,
                CRYPT_KEY,
                IV
            ).also {
                it.apply {
                    isConsoleEnable = BuildConfig.DEBUG
                    maxDiskAge = 60 * 60 * 24 * 7
                    maxDiskSize = 1024 * 1024 * 10
                    level = 0
                }
            }
        }.onSuccess {
            logger = it
            isReady = it.isEnable
            if (!isReady) Log.e("MXLogger", "日志初始化失败：${it.errorDesc}")
        }.onFailure {
            isReady = false
            Log.e("MXLogger", "日志初始化异常", it)
        }

        if (BuildConfig.DEBUG) {
            withLogger { Log.d("MXLogger", "日志目录 ${it.diskCachePath}") }
        }
    }

    fun d(tag: String, msg: String) = write { debug(tag, LOG_NAME, msg) }

    fun i(tag: String, msg: String) = write { info(tag, LOG_NAME, msg) }

    fun w(tag: String, msg: String) = write { warn(tag, LOG_NAME, msg) }

    fun e(tag: String, msg: String) = write { error(tag, LOG_NAME, msg) }

    fun f(tag: String, msg: String) = write { fatal(tag, LOG_NAME, msg) }

    fun removeExpiredData() {
        cleanupScope.launch {
            withLogger { it.removeExpireData() }
        }
    }

    private inline fun withLogger(block: (MXLogger) -> Unit) {
        if (isReady) block(logger)
    }

    private inline fun write(block: MXLogger.() -> Int) {
        if (!isReady) return
        val result = logger.block()
        if (result != 0 && BuildConfig.DEBUG) {
            Log.w("MXLogger", "日志写入失败($result)：${logger.errorDesc}")
        }
    }
}
