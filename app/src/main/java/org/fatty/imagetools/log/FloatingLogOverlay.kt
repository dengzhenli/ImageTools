package org.fatty.imagetools.log

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

data class OverlayLogFilterPreset(val level: OverlayLogLevel?, val query: String)

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
    private var selectedLevel: OverlayLogLevel? = null
    private var query = ""
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingRenderJob: Job? = null
    private var renderAction: (() -> Unit)? = null
    private var onSaveFilter: ((OverlayLogLevel?, String) -> OverlayLogFilterPreset?)? = null
    private var onNextFilter: (() -> OverlayLogFilterPreset?)? = null
    private var onManageFilters: ((OverlayLogLevel?, String) -> Unit)? = null

    fun showBubble(
        activity: Activity,
        logs: Flow<List<OverlayLogLine>>,
        onClear: () -> Unit,
        onSaveFilter: (OverlayLogLevel?, String) -> OverlayLogFilterPreset?,
        onNextFilter: () -> OverlayLogFilterPreset?,
        onManageFilters: (OverlayLogLevel?, String) -> Unit,
    ) {
        if (bubbleView != null) return

        formattedLogs = logs
        this.onClear = onClear
        this.onSaveFilter = onSaveFilter
        this.onNextFilter = onNextFilter
        this.onManageFilters = onManageFilters
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
        val params = FrameLayout.LayoutParams(dp(context, 340), dp(context, 290)).apply {
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
        var currentEntries: List<OverlayLogLine> = emptyList()
        val render = {
            val normalizedQuery = query.trim()
            val filteredEntries = currentEntries.filter { entry ->
                (selectedLevel == null || entry.level == selectedLevel) &&
                    (normalizedQuery.isEmpty() || entry.text.contains(normalizedQuery, ignoreCase = true))
            }
            val wasAtBottom = scrollView.scrollY + scrollView.height >=
                (scrollView.getChildAt(0)?.height ?: 0) - dp(context, 16)
            logText.text = buildColoredLogText(filteredEntries)
            if (wasAtBottom) scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
        renderAction = render
        val filterBar = createFilterBar(context) { level ->
            selectedLevel = level
            renderNow()
        }
        val searchInput = EditText(context).apply {
            hint = "筛选关键字（标签或内容）"
            setHintTextColor(0xFF9AA0AA.toInt())
            setTextColor(Color.WHITE)
            textSize = 12f
            setSingleLine()
            setText(query)
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
            background = GradientDrawable().apply {
                setColor(0x332A3038)
                cornerRadius = dp(context, 6).toFloat()
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                    debounceRender(KEYWORD_DEBOUNCE_MS)
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        fun applyPreset(preset: OverlayLogFilterPreset?) {
            if (preset == null) return
            selectedLevel = preset.level
            searchInput.setText(preset.query)
            renderNow()
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xB8000000.toInt())
                cornerRadius = dp(context, 10).toFloat()
            }
            clipToOutline = true
            addView(createHeader(context, onClear ?: {},
                onSave = { applyPreset(onSaveFilter?.invoke(selectedLevel, query)) },
                onNext = { applyPreset(onNextFilter?.invoke()) },
                onManage = { onManageFilters?.invoke(selectedLevel, query) },
            ), LinearLayout.LayoutParams(-1, dp(context, 34)))
            addView(filterBar, LinearLayout.LayoutParams(-1, dp(context, 36)))
            addView(searchInput, LinearLayout.LayoutParams(-1, dp(context, 36)).apply {
                leftMargin = dp(context, 8)
                rightMargin = dp(context, 8)
                bottomMargin = dp(context, 4)
            })
            addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        host.addView(root, params)
        panelView = root
        renderJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            logs.collectLatest { entries ->
                currentEntries = entries
                requestBatchedRender()
            }
        }
    }

    private fun createHeader(
        context: Context,
        onClear: () -> Unit,
        onSave: () -> Unit,
        onNext: () -> Unit,
        onManage: () -> Unit,
    ): View = LinearLayout(context).apply {
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
        addView(action(context, "管理", onManage))
        addView(action(context, "保存", onSave))
        addView(action(context, "预设", onNext))
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

    private fun createFilterBar(
        context: Context,
        onLevelSelected: (OverlayLogLevel?) -> Unit,
    ): View {
        val container = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
        }
        val filters = listOf(
            "全部" to null,
            "D" to OverlayLogLevel.DEBUG,
            "I" to OverlayLogLevel.INFO,
            "W" to OverlayLogLevel.WARN,
            "E" to OverlayLogLevel.ERROR,
        )
        val buttons = mutableListOf<TextView>()
        filters.forEach { (label, level) ->
            lateinit var button: TextView
            button = action(context, label) {
                selectedLevel = level
                buttons.forEach { it.isSelected = false }
                button.isSelected = true
                onLevelSelected(level)
            }.apply {
                setPadding(dp(context, 10), 0, dp(context, 10), 0)
                background = filterBackground(context)
                isSelected = selectedLevel == level
            }
            buttons += button
            container.addView(button, LinearLayout.LayoutParams(0, dp(context, 28), 1f).apply {
                leftMargin = dp(context, 2)
                rightMargin = dp(context, 2)
            })
        }
        return container
    }

    private fun filterBackground(context: Context) = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), GradientDrawable().apply {
            setColor(0xFF2E7D32.toInt())
            cornerRadius = dp(context, 6).toFloat()
        })
        addState(intArrayOf(), GradientDrawable().apply {
            setColor(0x332A3038)
            cornerRadius = dp(context, 6).toFloat()
        })
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
        cancelPendingRender()
        renderAction = null
        panelView?.let { view -> hostView?.removeView(view) }
        panelView = null
    }

    fun dismiss() {
        hidePanel()
        hideBubble()
        formattedLogs = null
        onClear = null
        onSaveFilter = null
        onNextFilter = null
        onManageFilters = null
        hostView = null
    }

    /** Limits expensive spannable rebuilding when a burst of logs arrives. */
    private fun requestBatchedRender() {
        if (pendingRenderJob == null) scheduleRender(LOG_RENDER_INTERVAL_MS)
    }

    /** Waits until the user pauses typing before applying a text filter. */
    private fun debounceRender(delayMillis: Long) {
        cancelPendingRender()
        scheduleRender(delayMillis)
    }

    private fun renderNow() {
        cancelPendingRender()
        renderAction?.invoke()
    }

    private fun scheduleRender(delayMillis: Long) {
        pendingRenderJob = renderScope.launch {
            delay(delayMillis)
            pendingRenderJob = null
            renderAction?.invoke()
        }
    }

    private fun cancelPendingRender() {
        pendingRenderJob?.cancel()
        pendingRenderJob = null
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val LOG_RENDER_INTERVAL_MS = 100L
        const val KEYWORD_DEBOUNCE_MS = 300L
    }
}
