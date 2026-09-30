package org.fatty.imagetools.log

import android.app.Activity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.map

/**
 * App-specific bridge. To reuse [FloatingLogOverlay] in another project, replace only this
 * adapter with that project's log Flow and clear action.
 */
class ALogOverlay {
    private val overlay = FloatingLogOverlay()
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun showBubble(activity: Activity) {
        overlay.showBubble(
            activity = activity,
            logs = ALog.visibleLogs.map { lines -> lines.map(::toOverlayLine) },
            onClear = ALog::clearVisibleLogs,
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
}
