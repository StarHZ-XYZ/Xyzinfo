package com.rjy.xyz.apps.xyzinfo.ui.network

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
import com.rjy.xyz.apps.xyzinfo.data.speed.SpeedUnit
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * 实时速度曲线（测速时一直在跑的折线 + 面积图）。
 *
 * 为什么要有它：只把大数字刷新快一点，看起来仍然像"数字乱跳"；配一条最近 15 秒的曲线，
 * 速度爬升、抖动、平台期全都变成看得见的东西 —— 这才是测速该有的手感。
 *
 * 性能上很克制：只在收到新样本时重画（每秒 10 次），缓冲区是定长数组，绘制过程零分配。
 */
class SpeedGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 单位只影响纵轴文字的写法，内部一律按字节/秒存。 */
    var unit: SpeedUnit = SpeedUnit.MBPS
        set(value) {
            field = value
            invalidate()
        }

    private val samples = FloatArray(CAPACITY)
    private var count = 0
    /** 纵轴上限：取整到 1/2/5×10^k，避免每来一个样本刻度就跳一次。 */
    private var axisMax = 1.0

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * resources.displayMetrics.density
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val linePath = Path()
    private val fillPath = Path()

    fun reset() {
        count = 0
        axisMax = 1.0
        invalidate()
    }

    /** 收到一个采样点（字节/秒）。 */
    fun addSample(bytesPerSecond: Double) {
        val value = bytesPerSecond.coerceAtLeast(0.0).toFloat()
        if (count < CAPACITY) {
            samples[count] = value
            count++
        } else {
            // 环形缓冲：整体左移一格（150 个 float 的拷贝，代价可以忽略）
            System.arraycopy(samples, 1, samples, 0, CAPACITY - 1)
            samples[CAPACITY - 1] = value
        }
        val peak = peak()
        if (peak > axisMax || peak < axisMax * 0.55) axisMax = niceCeil(peak)
        invalidate()
    }

    /** 曲线里的峰值（按当前单位换算后的数值）。 */
    fun peak(): Double {
        var peak = 0.0
        for (index in 0 until count) {
            if (samples[index] > peak) peak = samples[index].toDouble()
        }
        return peak
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val accent = ThemeColors.accent(context)
        val radius = 12f * resources.displayMetrics.density
        val density = resources.displayMetrics.density
        val topPadding = 14f * density
        val baseline = height - 4f * density
        val usableHeight = baseline - topPadding

        // 背景 + 两条参考网格线
        canvas.drawRoundRect(0f, 0f, width, height, radius, radius, fillPaint.apply {
            shader = null
            color = ContextCompat.getColor(context, R.color.surface_variant)
        })
        gridPaint.color = ContextCompat.getColor(context, R.color.divider)
        for (step in 1..2) {
            val y = topPadding + usableHeight * step / 3f
            canvas.drawLine(0f, y, width, y, gridPaint)
        }

        if (count < 2) {
            labelPaint.color = ContextCompat.getColor(context, R.color.text_tertiary)
            canvas.drawText("测速时这里会画出实时速度曲线", 10f * density, height / 2f + 4f * density, labelPaint)
            return
        }

        // 纵轴上限（字节/秒）；至少留一点高度，免得全 0 时画成一条贴底的线
        val maxBytes = max(axisMax, 1.0)
        val stepX = width / (CAPACITY - 1)
        // 曲线从右往左生长：最新的点永远贴在右边
        val offsetX = width - (count - 1) * stepX

        linePath.reset()
        fillPath.reset()
        for (index in 0 until count) {
            val x = offsetX + index * stepX
            val ratio = (samples[index] / maxBytes).coerceIn(0.0, 1.0)
            val y = baseline - (ratio * usableHeight).toFloat()
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, baseline)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(offsetX + (count - 1) * stepX, baseline)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f, topPadding, 0f, baseline,
            (accent and 0x00FFFFFF) or 0x55000000,
            (accent and 0x00FFFFFF) or 0x05000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        linePaint.color = accent
        canvas.drawPath(linePath, linePaint)

        // 最新样本：一个亮点，让"当前速度"有落点
        val lastX = offsetX + (count - 1) * stepX
        val lastY = baseline -
            ((samples[count - 1] / maxBytes).coerceIn(0.0, 1.0) * usableHeight).toFloat()
        dotPaint.color = accent
        canvas.drawCircle(lastX.coerceAtMost(width - 3f * density), lastY, 3.2f * density, dotPaint)

        // 纵轴上限标注：告诉用户这条线画到多高
        labelPaint.color = ContextCompat.getColor(context, R.color.text_tertiary)
        canvas.drawText(unit.text(maxBytes), 8f * density, 12f * density, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "峰值 ${unit.text(peak())}",
            width - 8f * density,
            12f * density,
            labelPaint
        )
        labelPaint.textAlign = Paint.Align.LEFT
    }

    /** 1 / 2 / 5 × 10^k 里挑一个不小于 [value] 的刻度。 */
    private fun niceCeil(value: Double): Double {
        if (value <= 0.0) return 1.0
        val magnitude = 10.0.pow(kotlin.math.floor(log10(value)))
        val normalized = value / magnitude
        val step = when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 5.0 -> 5.0
            else -> 10.0
        }
        return ceil(step * magnitude)
    }

    private companion object {
        /** 15 秒 @ 100ms 一个点。 */
        const val CAPACITY = 150
    }
}
