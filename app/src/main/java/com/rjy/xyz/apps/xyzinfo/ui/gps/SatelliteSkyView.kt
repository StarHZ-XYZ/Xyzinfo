package com.rjy.xyz.apps.xyzinfo.ui.gps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.location.GnssStatus
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 卫星天顶图（罗盘样式）。
 *
 * 正北朝上、方位角顺时针，圆心是天顶（仰角 90°），最外圈是地平线（仰角 0°）。
 * 每颗卫星按「方位角 → 角度、仰角 → 半径」落在图上：
 * 参与定位的实心，只被搜到的空心；颜色区分星座，点旁边标 SNR（载噪比）。
 */
class SatelliteSkyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Satellite(
        val id: Int,
        val constellation: Int,
        val azimuth: Float,
        val elevation: Float,
        val snr: Float,
        val usedInFix: Boolean
    )

    private var satellites: List<Satellite> = emptyList()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * resources.displayMetrics.density
    }
    private val snrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 8.5f * resources.displayMetrics.density
    }
    private val needlePath = Path()

    private val colorGrid = 0x33FFFFFF
    private val colorText = ContextCompat.getColor(context, R.color.text_secondary)
    private val colorAccent = ContextCompat.getColor(context, R.color.accent)
    private val colorOnAccent = ContextCompat.getColor(context, R.color.surface)
    private val colorIdle = ContextCompat.getColor(context, R.color.text_tertiary)

    /** 各星座的区分色（GPS / SBAS / GLONASS / QZSS / 北斗 / 伽利略 / IRNSS）。 */
    private val constellationColors = intArrayOf(
        Color.parseColor("#4C9AFF"), // GPS
        Color.parseColor("#9AA5B1"), // SBAS
        Color.parseColor("#FF6B6B"), // GLONASS
        Color.parseColor("#B07CFF"), // QZSS
        Color.parseColor("#FFB020"), // 北斗
        Color.parseColor("#2ED573"), // Galileo
        Color.parseColor("#00C8C8")  // IRNSS
    )

    private val constellationNames = arrayOf("GPS", "SBAS", "GLONASS", "QZSS", "北斗", "伽利略", "IRNSS")

    fun update(list: List<Satellite>) {
        satellites = list
        invalidate()
    }

    fun constellationSummary(): String {
        if (satellites.isEmpty()) return "还没有卫星数据"
        val counts = mutableMapOf<Int, Int>()
        satellites.forEach { sv -> counts[sv.constellation] = (counts[sv.constellation] ?: 0) + 1 }
        return counts.entries
            .sortedByDescending { it.value }
            .joinToString("  ") { (constellation, count) ->
                "${constellationLabel(constellation)} $count"
            }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        val density = resources.displayMetrics.density
        val centerX = width / 2f
        val centerY = height / 2f + 4f * density
        val radius = min(width / 2f, height / 2f) - 18f * density
        if (radius <= 0f) return

        // 底色圆盘
        fillPaint.color = 0x14000000
        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        // 仰角圈：地平线 / 30° / 60° / 天顶
        gridPaint.color = colorGrid
        for (step in 0..3) {
            canvas.drawCircle(centerX, centerY, radius * step / 3f, gridPaint)
        }
        // 方位十字线
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, gridPaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, gridPaint)

        // 方位标签：北在上、顺时针
        textPaint.color = colorText
        val labels = arrayOf("N" to 0, "E" to 90, "S" to 180, "W" to 270)
        labels.forEach { (label, azimuth) ->
            val rad = Math.toRadians(azimuth.toDouble())
            val x = centerX + (radius + 11f * density) * sin(rad).toFloat()
            val y = centerY - (radius + 11f * density) * cos(rad).toFloat() + textPaint.textSize / 3f
            textPaint.color = if (azimuth == 0) colorAccent else colorText
            canvas.drawText(label, x, y, textPaint)
        }

        // 正北小三角
        needlePath.reset()
        needlePath.moveTo(centerX, centerY - radius - 2f * density)
        needlePath.lineTo(centerX - 5f * density, centerY - radius - 11f * density)
        needlePath.lineTo(centerX + 5f * density, centerY - radius - 11f * density)
        needlePath.close()
        fillPaint.color = colorAccent
        canvas.drawPath(needlePath, fillPaint)

        // 卫星
        satellites.forEach { sv ->
            val elevation = sv.elevation.coerceIn(0f, 90f)
            val rad = Math.toRadians(sv.azimuth.toDouble())
            val rho = radius * (1f - elevation / 90f)
            val x = centerX + rho * sin(rad).toFloat()
            val y = centerY - rho * cos(rad).toFloat()
            val color = constellationColor(sv.constellation)

            // 信号越强光晕越大
            val strength = (sv.snr / 45f).coerceIn(0.15f, 1f)
            dotPaint.color = withAlpha(color, (70 * strength).toInt())
            canvas.drawCircle(x, y, (9f + 3f * strength) * density, dotPaint)

            if (sv.usedInFix) {
                dotPaint.color = color
                canvas.drawCircle(x, y, 4.2f * density, dotPaint)
                dotPaint.color = colorOnAccent
                canvas.drawCircle(x, y, 1.5f * density, dotPaint)
            } else {
                dotPaint.style = Paint.Style.STROKE
                dotPaint.strokeWidth = 1.4f * density
                dotPaint.color = withAlpha(color, 0xCC)
                canvas.drawCircle(x, y, 3.6f * density, dotPaint)
                dotPaint.style = Paint.Style.FILL
            }

            snrPaint.color = if (sv.usedInFix) colorIdle else withAlpha(colorIdle, 0x99)
            canvas.drawText(sv.id.toString(), x, y - 5.5f * density, snrPaint)
        }

        // 没有数据时给个提示
        if (satellites.isEmpty()) {
            textPaint.color = colorIdle
            canvas.drawText("等待卫星数据…", centerX, centerY + textPaint.textSize / 3f, textPaint)
        }
    }

    private fun constellationColor(constellation: Int): Int {
        val index = constellation - GnssStatus.CONSTELLATION_GPS
        return constellationColors.getOrElse(index) { colorIdle }
    }

    private fun constellationLabel(constellation: Int): String {
        val index = constellation - GnssStatus.CONSTELLATION_GPS
        return constellationNames.getOrElse(index) { "其它" }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
