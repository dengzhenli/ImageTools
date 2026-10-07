package org.fatty.imagetools.log

import android.app.Activity
import android.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * App-specific bridge. To reuse [FloatingLogOverlay] in another project, replace only this
 * adapter with that project's log Flow and clear action.
 */
class ALogOverlay {
    private val overlay = FloatingLogOverlay()
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val selectedPresetId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun showBubble(activity: Activity) {
        overlay.showBubble(
            activity = activity,
            logs = selectedPresetId.flatMapLatest { id ->
                if (id == null) ALog.visibleLogs else ALog.presetLogs.map { it[id].orEmpty() }
            }.map { lines -> lines.map(::toOverlayLine) },
            onClear = ALog::clearVisibleLogs,
            onSaveFilter = { level, query ->
                ALog.addFilterPreset(level?.toALogLevel(), query)?.let { id ->
                    selectedPresetId.value = id
                    OverlayLogFilterPreset(level, query)
                }
            },
            onNextFilter = {
                val presets = ALog.filterPresets.value
                if (presets.isEmpty()) null else {
                    val index = presets.indexOfFirst { it.id == selectedPresetId.value }
                    val preset = presets[if (index == -1 || index == presets.lastIndex) 0 else index + 1]
                    selectedPresetId.value = preset.id
                    OverlayLogFilterPreset(preset.level.toOverlayLevel(), preset.query)
                }
            },
            onManageFilters = { level, query -> showFilterManager(activity, level, query) },
        )
    }

    fun hideBubble() = overlay.hideBubble()

    fun showPanel() = overlay.showPanel()

    fun hidePanel() = overlay.hidePanel()

    fun dismiss() = overlay.dismiss()

    private fun toOverlayLine(line: ALog.LogLine): OverlayLogLine {
        val time = synchronized(timeFormatter) { timeFormatter.format(Date(line.timeMillis)) }
        return OverlayLogLine(
            text = "$time ${line.level}/${line.tag}: ${line.message}",
            level = when (line.level) {
                "D" -> OverlayLogLevel.DEBUG
                "I" -> OverlayLogLevel.INFO
                "W" -> OverlayLogLevel.WARN
                else -> OverlayLogLevel.ERROR
            },
        )
    }

    private fun OverlayLogLevel.toALogLevel() = when (this) {
        OverlayLogLevel.DEBUG -> "D"
        OverlayLogLevel.INFO -> "I"
        OverlayLogLevel.WARN -> "W"
        OverlayLogLevel.ERROR -> "E"
    }

    private fun String?.toOverlayLevel() = when (this) {
        "D" -> OverlayLogLevel.DEBUG
        "I" -> OverlayLogLevel.INFO
        "W" -> OverlayLogLevel.WARN
        "E" -> OverlayLogLevel.ERROR
        else -> null
    }

    private fun showFilterManager(activity: Activity, level: OverlayLogLevel?, query: String) {
        val presets = ALog.filterPresets.value
        val current = "当前条件：${level?.name ?: "全部"}${if (query.isBlank()) "" else "，关键词：$query"}"
        val items = listOf("实时日志") + presets.mapIndexed { index, preset ->
            "预设 ${index + 1}：${preset.level ?: "全部"}${if (preset.query.isBlank()) "" else "，${preset.query}"}"
        }
        AlertDialog.Builder(activity)
            .setTitle("筛选预设（${presets.size}/3）")
            .setMessage(current)
            .setItems(items.toTypedArray()) { _, which ->
                selectedPresetId.value = presets.getOrNull(which - 1)?.id
            }
            .setPositiveButton("保存当前") { _, _ ->
                ALog.addFilterPreset(level?.toALogLevel(), query)?.let { selectedPresetId.value = it }
            }
            .setNeutralButton("删除当前预设") { _, _ ->
                selectedPresetId.value?.let(ALog::removeFilterPreset)
                selectedPresetId.value = null
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}
