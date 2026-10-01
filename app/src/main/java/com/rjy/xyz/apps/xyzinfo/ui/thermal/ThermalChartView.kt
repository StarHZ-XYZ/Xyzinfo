package com.rjy.xyz.apps.xyzinfo.ui.thermal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.ThermalLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 温度曲线（1.0.2 重写，目标是"一眼看懂"）。
 *
 * 老版本的问题：纵轴范围随数据乱跳、没有时间刻度、四条线一样粗细、峰值只是个小圆点 ——
 * 看着像一堆乱线。现在按"先看结论、再看细节"来画：
 *
 * 1. **只突出最高那条**：最高热区画粗线 + 渐变面积 + 峰值气泡（写着温度和时间），
 *    其它热区用细线做背景对照，不再抢视线；
 * 2. **刻度规整**：纵轴取整到 5℃ 一档（不会出现 37.3 这种刻度），横轴按时间窗给 3~4 个
 *    时间刻度（1 小时看分钟、30 天看日期）；
 * 3. **右侧留"现在"的标记**：一眼看出哪端是最新；
 * 4. 数据只有一两个点的时候画点、不画线（线段会显得像断的）。
 */
class ThermalChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var samples: List<ThermalLogger.Sample> = emptyList()
    private var hours = 24
    private var liveOnly = false

    private val density = resources.displayMetrics.density

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val faintLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 110
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f * density }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val path = Path()
    private val fillPath = Path()

    /** 时间刻度：窗口越长，刻度越粗（1 小时看到分钟，30 天看到日期）。 */
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

    private val accent = ContextCompat.getColor(context, R.color.accent)
    private val faint = ContextCompat.getColor(context, R.color.text_tertiary)

    fun setData(list: List<ThermalLogger.Sample>, windowHours: Int, liveOnly: Boolean = false) {
        samples = list
        hours = windowHours
        this.liveOnly = liveOnly
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padLeft = 44f * density
        val padRight = 12f * density
        // 顶部留一行图例的位置，底部留时间刻度的位置 —— 两行各占各的，不会叠在一起
        val padTop = 30f * density
        val padBottom = 26f * density
        val plotWidth = w - padLeft - padRight
        val plotHeight = h - padTop - padBottom
        if (plotWidth <= 0f || plotHeight <= 0f) return

        if (samples.isEmpty()) {
            textPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
            canvas.drawText("正在采样…（每 2 秒一个点，十几秒后曲线就出来了）", padLeft, h / 2f, textPaint)
            return
        }

        val minTime = samples.minOf { it.timestamp }
        val maxTime = samples.maxOf { it.timestamp }.coerceAtLeast(minTime + 1)

        // 纵轴取整到 5℃ 一档，上下各留 2℃ 余量
        val rawMin = samples.minOf { it.celsius }
        val rawMax = samples.maxOf { it.celsius }
        val minTemp = floor((rawMin - 2) / 5.0) * 5.0
        val maxTemp = max(ceil((rawMax + 2) / 5.0) * 5.0, minTemp + 10.0)
        val span = maxTemp - minTemp

        fun xOf(timestamp: Long): Float =
            padLeft + plotWidth * ((timestamp - minTime).toFloat() / (maxTime - minTime))

        fun yOf(celsius: Double): Float =
            padTop + plotHeight * (1f - ((celsius - minTemp) / span).toFloat())

        // ---------- 网格与纵轴刻度 ----------
        gridPaint.color = ContextCompat.getColor(context, R.color.divider)
        textPaint.color = faint
        val steps = 4
        for (step in 0..steps) {
            val y = padTop + plotHeight * step / steps
            canvas.drawLine(padLeft, y, w - padRight, y, gridPaint)
            val value = maxTemp - span * step / steps
            canvas.drawText(
                String.format(Locale.US, "%.0f°", value),
                6f * density,
                y + 3.5f * density,
                textPaint
            )
        }
        axisPaint.color = ContextCompat.getColor(context, R.color.stroke)
        canvas.drawLine(padLeft, padTop, padLeft, padTop + plotHeight, axisPaint)
        canvas.drawLine(padLeft, padTop + plotHeight, w - padRight, padTop + plotHeight, axisPaint)

        // ---------- 按热区聚合，最高那条是主角 ----------
        val series = samples.groupBy { it.name }
            .mapValues { entry -> entry.value.sortedBy { it.timestamp } }
            .entries
            .sortedByDescending { entry -> entry.value.maxOf { it.celsius } }
        val main = series.firstOrNull() ?: return
        val others = series.drop(1).take(3)

        // 背景线（其它热区）
        others.forEach { entry ->
            faintLinePaint.color = faint
            drawSeries(canvas, entry.value, ::xOf, ::yOf, faintLinePaint)
        }

        // 主角：渐变面积 + 粗线
        val mainPoints = main.value
        if (mainPoints.size >= 2) {
            fillPath.reset()
            fillPath.moveTo(xOf(mainPoints.first().timestamp), padTop + plotHeight)
            mainPoints.forEach { fillPath.lineTo(xOf(it.timestamp), yOf(it.celsius)) }
            fillPath.lineTo(xOf(mainPoints.last().timestamp), padTop + plotHeight)
            fillPath.close()
            fillPaint.shader = LinearGradient(
                0f, padTop, 0f, padTop + plotHeight,
                (accent and 0x00FFFFFF) or 0x44000000,
                (accent and 0x00FFFFFF) or 0x05000000,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(fillPath, fillPaint)
            fillPaint.shader = null
        }
        linePaint.color = accent
        drawSeries(canvas, mainPoints, ::xOf, ::yOf, linePaint)

        // 最新点：右侧一个实心点 + 当前温度
        val latest = mainPoints.last()
        val latestX = xOf(latest.timestamp)
        val latestY = yOf(latest.celsius)
        canvas.drawCircle(latestX, latestY, 4f * density, linePaint)

        // 峰值气泡：写清"多少度、什么时候"
        val peak = mainPoints.maxByOrNull { it.celsius }!!
        val peakX = xOf(peak.timestamp)
        val peakY = yOf(peak.celsius)
        canvas.drawCircle(peakX, peakY, 3.5f * density, linePaint)
        bubblePaint.color = accent
        val peakLabel = String.format(Locale.US, "峰值 %.1f°", peak.celsius)
        val labelWidth = textPaint.measureText(peakLabel) + 12f * density
        val labelHeight = 18f * density
        var bubbleLeft = (peakX - labelWidth / 2f).coerceIn(padLeft, w - padRight - labelWidth)
        var bubbleTop = peakY - labelHeight - 8f * density
        if (bubbleTop < padTop) bubbleTop = peakY + 8f * density
        canvas.drawRoundRect(
            bubbleLeft, bubbleTop, bubbleLeft + labelWidth, bubbleTop + labelHeight,
            9f * density, 9f * density, bubblePaint
        )
        textPaint.color = Color.WHITE
        canvas.drawText(
            peakLabel,
            bubbleLeft + 6f * density,
            bubbleTop + 13f * density,
            textPaint
        )

        // ---------- 横轴时间刻度 ----------
        textPaint.color = faint
        val tickCount = 3
        val useDay = hours >= 48
        val formatter = if (useDay) dayFormat else timeFormat
        for (i in 0..tickCount) {
            val timestamp = minTime + (maxTime - minTime) * i / tickCount
            val label = formatter.format(Date(timestamp))
            val textWidth = textPaint.measureText(label)
            val x = (padLeft + plotWidth * i / tickCount - if (i == 0) 0f else textWidth / 2f)
                .coerceIn(0f, w - textWidth)
            canvas.drawText(label, x, h - 8f * density, textPaint)
        }

        // ---------- 图例（顶部一行） ----------
        textPaint.color = accent
        val head = "最高热区 " + shorten(main.key, 10)
        canvas.drawText(head, padLeft, 13f * density, textPaint)
        if (others.isNotEmpty()) {
            textPaint.color = faint
            val rest = "｜ 对照 " + others.joinToString("、") { shorten(it.key, 7) }
            canvas.drawText(rest, padLeft + textPaint.measureText(head) + 2f * density, 13f * density, textPaint)
        }
        if (mainPoints.size < 2) {
            // 只有一个点：在画面中央标注一下，避免用户以为图画坏了
            textPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
            val note = "只有 1 个采样点，再等十几秒就会出现曲线"
            canvas.drawText(note, padLeft, padTop + plotHeight / 2f, textPaint)
        }
        if (liveOnly) {
            textPaint.color = faint
            val note = "实时采样中"
            canvas.drawText(note, w - padRight - textPaint.measureText(note), 13f * density, textPaint)
        }
    }

    private fun shorten(name: String, limit: Int): String =
        if (name.length > limit) name.take(limit) + "…" else name

    private fun drawSeries(
        canvas: Canvas,
        points: List<ThermalLogger.Sample>,
        xOf: (Long) -> Float,
        yOf: (Double) -> Float,
        paint: Paint
    ) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            canvas.drawCircle(xOf(points[0].timestamp), yOf(points[0].celsius), 3f * density, paint)
            return
        }
        path.reset()
        points.forEachIndexed { index, sample ->
            val x = xOf(sample.timestamp)
            val y = yOf(sample.celsius)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}
