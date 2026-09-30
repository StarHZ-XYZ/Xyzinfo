package com.rjy.xyz.apps.xyzinfo.ui.thermal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import java.util.Locale
import kotlin.math.min

/**
 * 实时温度环：一个大圆环 + 中间的大号温度。
 *
 * 环的填充比例按 20~80 ℃ 映射，颜色随温度分级（凉=主题色 / 温=橙 / 烫=红），
 * 比单纯一行数字直观得多。
 */
class TemperatureGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var celsius: Double? = null
    private var zoneName: String = ""

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 13f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.divider)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 13f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 46f * resources.displayMetrics.density
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * resources.displayMetrics.density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11.5f * resources.displayMetrics.density
    }
    private val rect = RectF()

    fun setTemperature(value: Double?, zone: String) {
        celsius = value
        zoneName = zone
        invalidate()
    }

    private fun heatColor(): Int {
        val value = celsius ?: return ContextCompat.getColor(context, R.color.text_tertiary)
        return when {
            value >= 55 -> ContextCompat.getColor(context, R.color.status_danger)
            value >= 45 -> ContextCompat.getColor(context, R.color.status_warning)
            else -> ContextCompat.getColor(context, R.color.accent)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f + 6f * density
        val radius = min(width / 2f, height / 2f) - 20f * density
        if (radius <= 0) return
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 底环
        canvas.drawArc(rect, START_ANGLE, SWEEP_ANGLE, false, trackPaint)

        val value = celsius
        if (value != null) {
            val ratio = ((value - 20.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
            arcPaint.color = heatColor()
            canvas.drawArc(rect, START_ANGLE, SWEEP_ANGLE * ratio, false, arcPaint)
        }

        // 中间的文字
        val color = heatColor()
        numberPaint.color = color
        val numberText = value?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        canvas.drawText(numberText, cx, cy + 8f * density, numberPaint)
        unitPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
        val numberWidth = numberPaint.measureText(numberText)
        canvas.drawText("℃", cx + numberWidth / 2 + 14f * density, cy + 2f * density, unitPaint)
        labelPaint.color = ContextCompat.getColor(context, R.color.text_tertiary)
        canvas.drawText(
            if (zoneName.isBlank()) "当前最高温" else "当前最高温 · ${zoneName.take(16)}",
            cx,
            cy + 30f * density,
            labelPaint
        )
    }

    private companion object {
        /** 从左上 135° 起，顺时针 270° 的表盘。 */
        const val START_ANGLE = 135f
        const val SWEEP_ANGLE = 270f
    }
}
