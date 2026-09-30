package com.rjy.xyz.apps.xyzinfo.ui.thermal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.ThermalLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 温度曲线：把热区温度随时间画成折线。
 *
 * 取"最高温的前 4 个热区"来画（再多就分不清了），每条线配一种颜色并标出峰值；
 * 纵轴按数据自适应，横轴按时间等距，并在末尾显示时间刻度。
 */
class ThermalChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var samples: List<ThermalLogger.Sample> = emptyList()
    private var hours = 24

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * resources.displayMetrics.density
    }
    private val path = Path()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.US)

    private val palette = intArrayOf(
        ContextCompat.getColor(context, R.color.accent),
        ContextCompat.getColor(context, R.color.status_warning),
        ContextCompat.getColor(context, R.color.status_danger),
        Color.parseColor("#4D6BFE")
    )

    fun setData(list: List<ThermalLogger.Sample>, windowHours: Int) {
        samples = list
        hours = windowHours
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val paddingLeft = 38f * density
        val paddingRight = 10f * density
        val paddingTop = 12f * density
        val paddingBottom = 26f * density
        val plotWidth = w - paddingLeft - paddingRight
        val plotHeight = h - paddingTop - paddingBottom
        gridPaint.color = ContextCompat.getColor(context, R.color.divider)
        textPaint.color = ContextCompat.getColor(context, R.color.text_tertiary)

        if (samples.isEmpty() || plotWidth <= 0 || plotHeight <= 0) {
            textPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
            canvas.drawText("还没有数据：点「开始记录」跑一会儿再回来", paddingLeft, h / 2f, textPaint)
            return
        }

        // 按热区聚合，取峰值最高的 4 个
        val groups = samples.groupBy { it.name }
            .mapValues { entry -> entry.value.sortedBy { it.timestamp } }
            .entries
            .sortedByDescending { entry -> entry.value.maxOf { it.celsius } }
            .take(4)

        val minTime = samples.minOf { it.timestamp }
        val maxTime = samples.maxOf { it.timestamp }.coerceAtLeast(minTime + 1)
        val minTemp = (samples.minOf { it.celsius } - 2).coerceAtLeast(0.0)
        val maxTemp = samples.maxOf { it.celsius } + 2

        // 网格 + 温度刻度
        for (step in 0..4) {
            val y = paddingTop + plotHeight * step / 4f
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
            val value = maxTemp - (maxTemp - minTemp) * step / 4f
            canvas.drawText(String.format(Locale.US, "%.0f°", value), 4f * density, y + 3f * density, textPaint)
        }

        // 每条热区一条折线
        groups.forEachIndexed { index, entry ->
            val color = palette[index % palette.size]
            linePaint.color = color
            path.reset()
            var lastX = 0f
            var lastY = 0f
            entry.value.forEachIndexed { pointIndex, sample ->
                val x = paddingLeft + plotWidth * ((sample.timestamp - minTime).toFloat() / (maxTime - minTime))
                val y = paddingTop + plotHeight * (1f - ((sample.celsius - minTemp) / (maxTemp - minTemp)).toFloat())
                if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                lastX = x
                lastY = y
            }
            canvas.drawPath(path, linePaint)
            // 末尾标一个圆点 + 峰值文字（只标前两条，避免太挤）
            if (index < 2) {
                canvas.drawCircle(lastX, lastY, 3f * density, linePaint)
                val peak = entry.value.maxOf { it.celsius }
                textPaint.color = color
                canvas.drawText(
                    "${entry.key.take(12)} ${String.format(Locale.US, "%.0f°", peak)}",
                    lastX - 90f * density,
                    lastY - 5f * density,
                    textPaint
                )
            }
        }

        // 时间刻度
        textPaint.color = ContextCompat.getColor(context, R.color.text_tertiary)
        canvas.drawText(
            timeFormat.format(Date(minTime)),
            paddingLeft,
            h - 8f * density,
            textPaint
        )
        val endText = timeFormat.format(Date(maxTime))
        canvas.drawText(
            endText,
            w - paddingRight - textPaint.measureText(endText),
            h - 8f * density,
            textPaint
        )
        canvas.drawText(
            "最近 ${hours} 小时 · ${samples.size} 个采样点",
            paddingLeft + plotWidth / 2f - 60f * density,
            h - 8f * density,
            textPaint
        )
    }
}
