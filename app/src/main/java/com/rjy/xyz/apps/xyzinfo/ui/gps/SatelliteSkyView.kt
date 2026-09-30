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
 * 卫星天顶图 + 指南针二合一。
 *
 * - 极坐标：圆心是天顶（仰角 90°），最外圈是地平线（仰角 0°）；
 * - **带指南针**：接入方向传感器后整张图会跟着手机朝向转，
 *   屏幕上方的那个固定三角就是「手机正对的方向」，方位角实时显示；
 * - 每颗卫星按方位角 / 仰角落点，标出「星座代号 + 编号」，
 *   例如 G12 = GPS 12 号、C06 = 北斗 6 号；
 * - 参与定位的卫星画实心 + 加一圈亮环，并在下方单独列出「当前卫星」。
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
    private var headingDegrees: Float? = null

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
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 8f * resources.displayMetrics.density
    }
    private val needlePath = Path()

    private val colorAccent = ContextCompat.getColor(context, R.color.accent)
    private val colorSurface = ContextCompat.getColor(context, R.color.surface)
    private val colorIdle = ContextCompat.getColor(context, R.color.text_tertiary)
    private val colorText = ContextCompat.getColor(context, R.color.text_secondary)

    /** GPS / SBAS / GLONASS / QZSS / 北斗 / Galileo / IRNSS 的区分色。 */
    private val constellationColors = intArrayOf(
        Color.parseColor("#4C9AFF"),
        Color.parseColor("#9AA5B1"),
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#B07CFF"),
        Color.parseColor("#FFB020"),
        Color.parseColor("#2ED573"),
        Color.parseColor("#00C8C8")
    )
    private val constellationNames = arrayOf("GPS", "SBAS", "GLONASS", "QZSS", "北斗", "伽利略", "IRNSS")
    private val constellationPrefixes = arrayOf("G", "S", "R", "J", "C", "E", "I")

    fun update(list: List<Satellite>) {
        satellites = list
        invalidate()
    }

    /** 指南针方位角（0 = 正北，顺时针）；传 null 表示回到「北朝上」。 */
    fun setHeading(degrees: Float?) {
        headingDegrees = degrees
        invalidate()
    }

    /** 参与定位的卫星清单，例如「G12、C06、E19」。 */
    fun usedSatelliteLabels(): String {
        val used = satellites.filter { it.usedInFix }
        if (used.isEmpty()) return "还没有参与定位的卫星"
        return used.sortedBy { it.constellation }.joinToString("、") { labelOf(it) }
    }

    fun constellationSummary(): String {
        if (satellites.isEmpty()) return "还没有卫星数据"
        val counts = mutableMapOf<Int, Int>()
        satellites.forEach { sv -> counts[sv.constellation] = (counts[sv.constellation] ?: 0) + 1 }
        return counts.entries
            .sortedByDescending { it.value }
            .joinToString("  ") { (constellation, count) -> "${prefixLabel(constellation)} $count" }
    }

    /** 图例文案，放在视图下方。 */
    fun legend(): String = "G=GPS  C=北斗  R=GLONASS  E=伽利略  J=QZSS  S=SBAS  I=IRNSS"

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        val density = resources.displayMetrics.density
        val centerX = width / 2f
        val centerY = height / 2f + 4f * density
        val radius = min(width / 2f, height / 2f) - 20f * density
        if (radius <= 0f) return

        val heading = headingDegrees ?: 0f
        val checkpoint = canvas.save()
        // 指南针模式：整张图跟着手机朝向转，屏幕上方的固定三角代表手机正对方向
        canvas.rotate(-heading, centerX, centerY)

        fillPaint.color = 0x14000000
        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        gridPaint.color = 0x33FFFFFF
        for (step in 0..3) {
            canvas.drawCircle(centerX, centerY, radius * step / 3f, gridPaint)
        }
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, gridPaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, gridPaint)

        // 刻度：每 30° 一小段
        for (azimuth in 0 until 360 step 30) {
            val rad = Math.toRadians(azimuth.toDouble())
            val outer = radius
            val inner = radius - (if (azimuth % 90 == 0) 9f else 5f) * density
            canvas.drawLine(
                centerX + inner * sin(rad).toFloat(),
                centerY - inner * cos(rad).toFloat(),
                centerX + outer * sin(rad).toFloat(),
                centerY - outer * cos(rad).toFloat(),
                gridPaint
            )
        }

        textPaint.color = colorText
        listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (label, azimuth) ->
            val rad = Math.toRadians(azimuth.toDouble())
            val x = centerX + (radius + 12f * density) * sin(rad).toFloat()
            val y = centerY - (radius + 12f * density) * cos(rad).toFloat() + textPaint.textSize / 3f
            textPaint.color = if (azimuth == 0) colorAccent else colorText
            canvas.drawText(label, x, y, textPaint)
        }

        satellites.forEach { sv ->
            val elevation = sv.elevation.coerceIn(0f, 90f)
            val rad = Math.toRadians(sv.azimuth.toDouble())
            val rho = radius * (1f - elevation / 90f)
            val x = centerX + rho * sin(rad).toFloat()
            val y = centerY - rho * cos(rad).toFloat()
            val color = constellationColor(sv.constellation)
            val strength = (sv.snr / 45f).coerceIn(0.15f, 1f)

            dotPaint.color = withAlpha(color, (70 * strength).toInt())
            canvas.drawCircle(x, y, (9f + 3f * strength) * density, dotPaint)

            if (sv.usedInFix) {
                dotPaint.style = Paint.Style.STROKE
                dotPaint.strokeWidth = 1.6f * density
                dotPaint.color = withAlpha(color, 0xAA)
                canvas.drawCircle(x, y, 7.5f * density, dotPaint)
                dotPaint.style = Paint.Style.FILL
                dotPaint.color = color
                canvas.drawCircle(x, y, 4.4f * density, dotPaint)
                dotPaint.color = colorSurface
                canvas.drawCircle(x, y, 1.6f * density, dotPaint)
            } else {
                dotPaint.style = Paint.Style.STROKE
                dotPaint.strokeWidth = 1.4f * density
                dotPaint.color = withAlpha(color, 0xCC)
                canvas.drawCircle(x, y, 3.6f * density, dotPaint)
                dotPaint.style = Paint.Style.FILL
            }

            // 标出「是哪颗卫星」：星座代号 + 编号
            labelPaint.color = if (sv.usedInFix) color else withAlpha(color, 0xAA)
            labelPaint.isFakeBoldText = sv.usedInFix
            canvas.drawText(labelOf(sv), x, y - 7.5f * density, labelPaint)
            labelPaint.isFakeBoldText = false
        }

        canvas.restoreToCount(checkpoint)

        // 屏幕上方固定不动：代表手机正对的方向
        needlePath.reset()
        needlePath.moveTo(width / 2f, 2f * density)
        needlePath.lineTo(width / 2f - 6f * density, 12f * density)
        needlePath.lineTo(width / 2f + 6f * density, 12f * density)
        needlePath.close()
        fillPaint.color = colorAccent
        canvas.drawPath(needlePath, fillPaint)

        // 方位角读数 + 图例
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = colorIdle
        textPaint.textSize = 10f * density
        canvas.drawText(
            "朝向 ${heading.toInt()}°  ${cardinal(heading)}",
            10f * density,
            height - 8f * density,
            textPaint
        )
        textPaint.textAlign = Paint.Align.CENTER

        if (satellites.isEmpty()) {
            textPaint.color = colorIdle
            canvas.drawText("等待卫星数据…", centerX, centerY + textPaint.textSize / 3f, textPaint)
        }
    }

    private fun labelOf(sv: Satellite): String = "${prefixOf(sv.constellation)}${sv.id}"

    private fun prefixOf(constellation: Int): String =
        constellationPrefixes.getOrElse(constellation - GnssStatus.CONSTELLATION_GPS) { "?" }

    private fun prefixLabel(constellation: Int): String =
        constellationNames.getOrElse(constellation - GnssStatus.CONSTELLATION_GPS) { "其它" }

    private fun constellationColor(constellation: Int): Int =
        constellationColors.getOrElse(constellation - GnssStatus.CONSTELLATION_GPS) { colorIdle }

    private fun cardinal(degrees: Float): String {
        val names = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
        val index = (((degrees % 360f) + 22.5f) / 45f).toInt() % 8
        return names[index]
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
