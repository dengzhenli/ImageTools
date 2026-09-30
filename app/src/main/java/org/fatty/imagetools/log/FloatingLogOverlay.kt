package org.fatty.imagetools.log

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class OverlayLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class OverlayLogLine(
    val text: String,
    val level: OverlayLogLevel,
)

/**
 * A debug-only floating log viewer. The owner must call [dismiss] with its lifecycle.
 * It accepts plain text formatting so this library stays independent of the host app's logger.
 */
class FloatingLogOverlay {
    private var hostView: ViewGroup? = null
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var renderJob: Job? = null
    private var formattedLogs: Flow<List<OverlayLogLine>>? = null
    private var onClear: (() -> Unit)? = null

    fun showBubble(
        activity: Activity,
        logs: Flow<List<OverlayLogLine>>,
        onClear: () -> Unit,
    ) {
        if (bubbleView != null) return

        formattedLogs = logs
        this.onClear = onClear
        val host = activity.findViewById<ViewGroup>(android.R.id.content)
        val bubble = createBubble(activity, ::showPanel)
        val params = FrameLayout.LayoutParams(dp(activity, 48), dp(activity, 48)).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = dp(activity, 12)
            topMargin = dp(activity, 12)
        }
        host.addView(bubble, params)
        hostView = host
        bubbleView = bubble
    }

    private fun createBubble(activity: Activity, onClick: () -> Unit) = TextView(activity).apply {
        text = "Log"
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 12f
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xCC2E7D32.toInt())
            setStroke(dp(activity, 1), 0xFF81C995.toInt())
        }
        elevation = dp(activity, 6).toFloat()
        setOnClickListener { onClick() }
    }

    fun showPanel() {
        if (panelView != null) return
        val host = hostView ?: return
        val context = host.context
        val logs = formattedLogs ?: return
        val params = FrameLayout.LayoutParams(dp(context, 340), dp(context, 250)).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(context, 12)
            bottomMargin = dp(context, 24)
        }
        val logText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
            setTextIsSelectable(true)
        }
        val scrollView = ScrollView(context).apply {
            addView(logText, LinearLayout.LayoutParams(-1, -2))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xB8000000.toInt())
                cornerRadius = dp(context, 10).toFloat()
            }
            clipToOutline = true
            addView(createHeader(context, onClear ?: {}), LinearLayout.LayoutParams(-1, dp(context, 34)))
            addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        host.addView(root, params)
        panelView = root
        renderJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            logs.collectLatest { entries ->
                logText.text = buildColoredLogText(entries)
                scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun createHeader(context: Context, onClear: () -> Unit): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 10), 0, dp(context, 4), 0)
        background = GradientDrawable().apply {
            setColor(0xD912161D.toInt())
            val radius = dp(context, 10).toFloat()
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }
        addView(TextView(context).apply {
            text = "MXLog 实时日志"
            setTextColor(0xFF81C995.toInt())
            textSize = 12f
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(action(context, "清空", onClear))
        addView(action(context, "×", ::hidePanel))
    }

    private fun buildColoredLogText(entries: List<OverlayLogLine>): SpannableStringBuilder {
        return SpannableStringBuilder().apply {
            entries.forEachIndexed { index, entry ->
                val start = length
                append(entry.text)
                setSpan(
                    ForegroundColorSpan(colorFor(entry.level)),
                    start,
                    length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                if (index != entries.lastIndex) append('\n')
            }
        }
    }

    private fun colorFor(level: OverlayLogLevel): Int = when (level) {
        OverlayLogLevel.DEBUG -> 0xFFB0B7C3.toInt()
        OverlayLogLevel.INFO -> 0xFF81C995.toInt()
        OverlayLogLevel.WARN -> 0xFFFFC857.toInt()
        OverlayLogLevel.ERROR -> 0xFFFF6B6B.toInt()
    }

    private fun action(context: Context, label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 12f
        setPadding(dp(context, 8), 0, dp(context, 8), 0)
        setOnClickListener { onClick() }
    }

    fun hideBubble() {
        bubbleView?.let { view -> hostView?.removeView(view) }
        bubbleView = null
    }

    fun hidePanel() {
        renderJob?.cancel()
        renderJob = null
        panelView?.let { view -> hostView?.removeView(view) }
        panelView = null
    }

    fun dismiss() {
        hidePanel()
        hideBubble()
        formattedLogs = null
        onClear = null
        hostView = null
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
