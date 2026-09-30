package org.fatty.imagetools.log

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dongjiayi.mxlogger.MXLogger
import com.dongjiayi.mxlogger.MXStoragePolicyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fatty.imagetools.BuildConfig
import java.lang.ref.WeakReference

object ALog {
    /** Set false in projects that handle the log UI themselves. */
    const val AUTO_SHOW_LOG_BUBBLE = true

    private const val LOG_NAME = "alog"
    private const val NAMESPACE = "org.fatty.imagetools"
    private const val FILE_NAME = "mxlog"
    private const val FILE_HEADER = "{\"app_version\":\"${BuildConfig.VERSION_NAME}\"}"
    // MXLogger currently accepts a raw symmetric key. Replace this with a keystore-backed
    // provider when the library supports non-exportable Android Keystore keys.
    private const val CRYPT_KEY = "jsgfbhvjdhfngmnf"
    private const val IV = "durhfjgkdhfngjvh"

    private lateinit var logger: MXLogger
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logBuffer = ArrayDeque<LogLine>(MAX_VISIBLE_LOGS)
    private val _visibleLogs = MutableStateFlow<List<LogLine>>(emptyList())
    /** 供调试悬浮窗订阅的实时日志；只保留最近 [MAX_VISIBLE_LOGS] 条。 */
    val visibleLogs = _visibleLogs.asStateFlow()
    @Volatile private var isReady = false
    @Volatile private var lifecycleObserverRegistered = false
    @Volatile private var overlayLifecycleRegistered = false
    private var overlayLifecycleManagerRef: WeakReference<OverlayLifecycleManager>? = null

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
        registerCleanupObserver()
        if (AUTO_SHOW_LOG_BUBBLE) {
            registerOverlayLifecycle(context.applicationContext as? Application)
        }
    }

    fun d(tag: String, msg: String) = write("D", tag, msg) { debug(tag, LOG_NAME, msg) }

    fun i(tag: String, msg: String) = write("I", tag, msg) { info(tag, LOG_NAME, msg) }

    fun w(tag: String, msg: String) = write("W", tag, msg) { warn(tag, LOG_NAME, msg) }

    fun e(tag: String, msg: String) = write("E", tag, msg) { error(tag, LOG_NAME, msg) }

    fun f(tag: String, msg: String) = write("F", tag, msg) { fatal(tag, LOG_NAME, msg) }

    fun clearVisibleLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _visibleLogs.value = emptyList()
        }
    }

    fun hideLogBubble() {
        overlayLifecycleManagerRef?.get()?.hideBubble()
    }

    /** Shows the bubble on the current resumed Activity, if the app has one. */
    fun showLogBubble() {
        if (BuildConfig.DEBUG) overlayLifecycleManagerRef?.get()?.showBubble()
    }

    fun showLogPanel() {
        if (BuildConfig.DEBUG) overlayLifecycleManagerRef?.get()?.showPanel()
    }

    fun hideLogPanel() {
        overlayLifecycleManagerRef?.get()?.hidePanel()
    }

    fun removeExpiredData() {
        cleanupScope.launch {
            withLogger { it.removeExpireData() }
        }
    }

    private fun registerCleanupObserver() {
        if (lifecycleObserverRegistered) return
        synchronized(this) {
            if (lifecycleObserverRegistered) return
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    overlayLifecycleManagerRef?.get()?.dismiss()
                    removeExpiredData()
                }
            })
            lifecycleObserverRegistered = true
        }
    }

    private fun registerOverlayLifecycle(application: Application?) {
        if (!BuildConfig.DEBUG || application == null || overlayLifecycleRegistered) return
        synchronized(this) {
            if (overlayLifecycleRegistered) return
            OverlayLifecycleManager().also { manager ->
                application.registerActivityLifecycleCallbacks(manager)
                overlayLifecycleManagerRef = WeakReference(manager)
            }
            overlayLifecycleRegistered = true
        }
    }

    private inline fun withLogger(block: (MXLogger) -> Unit) {
        if (isReady) block(logger)
    }

    private inline fun write(level: String, tag: String, msg: String, block: MXLogger.() -> Int) {
        if (!isReady) return
        appendVisibleLog(level, tag, msg)
        val result = logger.block()
        if (result != 0 && BuildConfig.DEBUG) {
            Log.w("MXLogger", "日志写入失败($result)：${logger.errorDesc}")
        }
    }

    private fun appendVisibleLog(level: String, tag: String, message: String) {
        synchronized(logBuffer) {
            if (logBuffer.size == MAX_VISIBLE_LOGS) logBuffer.removeFirst()
            logBuffer.addLast(LogLine(System.currentTimeMillis(), level, tag, message))
            _visibleLogs.value = logBuffer.toList()
        }
    }

    data class LogLine(val timeMillis: Long, val level: String, val tag: String, val message: String)

    private const val MAX_VISIBLE_LOGS = 200

    /**
     * Registered from [init], so no screen needs to know about the log UI. The manager is
     * process-scoped but clears every View reference whenever an Activity pauses or the app
     * enters the background.
     */
    private class OverlayLifecycleManager : Application.ActivityLifecycleCallbacks {
        private val overlay = ALogOverlay()
        private var resumedActivityRef: WeakReference<Activity>? = null

        override fun onActivityResumed(activity: Activity) {
            resumedActivityRef = WeakReference(activity)
            overlay.showBubble(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumedActivityRef?.get() === activity) resumedActivityRef = null
            overlay.dismiss()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (resumedActivityRef?.get() === activity) resumedActivityRef = null
            overlay.dismiss()
        }

        fun showBubble() = resumedActivityRef?.get()?.let(overlay::showBubble)

        fun hideBubble() = overlay.hideBubble()

        fun showPanel() = overlay.showPanel()

        fun hidePanel() = overlay.hidePanel()

        fun dismiss() = overlay.dismiss()

        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
    }
}
