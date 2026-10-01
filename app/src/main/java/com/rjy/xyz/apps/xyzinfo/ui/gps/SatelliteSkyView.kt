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
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

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

    private val colorAccent = ThemeColors.accent(context)
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
    /*
     * 1.0.8：每个卫星系统写清楚「是哪国的、叫什么」。
     *
     * 顺序对应 GnssStatus 的星座常量：GPS(1) SBAS(2) GLONASS(3) QZSS(4) BEIDOU(5) GALILEO(6) IRNSS(7)。
     */
    private val constellationNames = arrayOf(
        "GPS（美国）",
        "SBAS（星基增强）",
        "GLONASS（俄罗斯）",
        "QZSS（日本 引路）",
        "北斗（中国）",
        "Galileo（欧盟）",
        "NavIC（印度）"
    )

    /** 图上的短标：G12 = 美国 GPS 12 号，C06 = 中国北斗 6 号 …… */
    private val constellationPrefixes = arrayOf("G", "S", "R", "J", "C", "E", "I")

    /** 只有一个字母太抽象，这里给更短但看得懂的名字（统计行用）。 */
    private val constellationShortNames = arrayOf(
        "GPS", "SBAS", "GLONASS", "QZSS", "北斗", "Galileo", "NavIC"
    )

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
            .joinToString("  ") { (constellation, count) -> "${shortLabel(constellation)} $count" }
    }

    /** 图例文案，放在视图下方：每个字母对应哪国的哪套系统，都写清楚。 */
    fun legend(): String =
        "G=GPS（美国）  C=北斗（中国）  R=GLONASS（俄罗斯）  E=Galileo（欧盟）  " +
            "J=QZSS（日本）  I=NavIC（印度）  S=SBAS（星基增强）"

    /** 星座中文名（供页面拼「各国卫星各有多少」的统计用）。 */
    fun constellationLabelOf(constellation: Int): String =
        constellationNames.getOrElse(constellation - GnssStatus.CONSTELLATION_GPS) { "其它卫星系统" }

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

            /*
             * 1.0.8：圆点收小一圈。
             *
             * 以前光晕最大能画到 12dp、实心点 4.4dp，二三十颗挤在一起就糊成一片。
             * 现在光晕 6~8dp、实心点 3.6dp，参与定位的环 6dp —— 密度下来了还是分得清。
             */
            dotPaint.color = withAlpha(color, (70 * strength).toInt())
            canvas.drawCircle(x, y, (6f + 2f * strength) * density, dotPaint)

            if (sv.usedInFix) {
                dotPaint.style = Paint.Style.STROKE
                dotPaint.strokeWidth = 1.5f * density
                dotPaint.color = withAlpha(color, 0xAA)
                canvas.drawCircle(x, y, 6f * density, dotPaint)
                dotPaint.style = Paint.Style.FILL
                dotPaint.color = color
                canvas.drawCircle(x, y, 3.6f * density, dotPaint)
                dotPaint.color = colorSurface
                canvas.drawCircle(x, y, 1.4f * density, dotPaint)
            } else {
                dotPaint.style = Paint.Style.STROKE
                dotPaint.strokeWidth = 1.3f * density
                dotPaint.color = withAlpha(color, 0xCC)
                canvas.drawCircle(x, y, 3f * density, dotPaint)
                dotPaint.style = Paint.Style.FILL
            }
        }

        canvas.restoreToCount(checkpoint)

        /*
         * 标签在**旋转之外**画：文字跟着罗盘转的话，手机一转字就全歪了。
         * 位置用同样的变换手算一遍（屏幕角度 = 卫星方位角 − 手机朝向）。
         */
        drawLabels(canvas, centerX, centerY, radius, heading)

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

    /**
     * 给卫星标「星座代号 + 编号」，并且**不许糊成一片**（1.0.8 去拥挤）。
     *
     * 做法：
     * 1. 参与定位的卫星优先，同组里按信号从强到弱；
     * 2. 每个标签占一个 24×12dp 的格子（左右各占一格），格子被占就换到圆点下方试；
     *    上下都占满就**干脆不标** —— 少一个标签也比一坨叠字强；
     * 3. 标签画在旋转之外，手机怎么转字都是正的。
     */
    private fun drawLabels(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, heading: Float) {
        if (satellites.isEmpty()) return
        val density = resources.displayMetrics.density
        val cellWidth = 26f * density
        val cellHeight = 12f * density
        val columns = (width / cellWidth).toInt().coerceAtLeast(1)
        val rows = (height / cellHeight).toInt().coerceAtLeast(1)
        val occupied = BooleanArray(columns * rows)

        fun takeCell(x: Float, y: Float): Boolean {
            val column = (x / cellWidth).toInt().coerceIn(0, columns - 1)
            val row = (y / cellHeight).toInt().coerceIn(0, rows - 1)
            val range = (column - 1)..(column + 1)
            if (range.any { it in 0 until columns && occupied[row * columns + it] }) return false
            range.forEach { if (it in 0 until columns) occupied[row * columns + it] = true }
            return true
        }

        val ordered = satellites.sortedWith(
            compareByDescending<Satellite> { it.usedInFix }.thenByDescending { it.snr }
        )
        ordered.forEach { sv ->
            val elevation = sv.elevation.coerceIn(0f, 90f)
            // 屏幕角度：画布整体转过 -heading，所以这里也要减掉
            val screenAzimuth = sv.azimuth - heading
            val rad = Math.toRadians(screenAzimuth.toDouble())
            val rho = radius * (1f - elevation / 90f)
            val x = centerX + rho * sin(rad).toFloat()
            val y = centerY - rho * cos(rad).toFloat()

            labelPaint.color = if (sv.usedInFix) {
                constellationColor(sv.constellation)
            } else {
                withAlpha(constellationColor(sv.constellation), 0xAA)
            }
            labelPaint.isFakeBoldText = sv.usedInFix

            val aboveY = y - 8f * density
            val belowY = y + 12f * density
            val textY = when {
                takeCell(x, aboveY) -> aboveY
                takeCell(x, belowY) -> belowY
                else -> null
            }
            if (textY != null) canvas.drawText(labelOf(sv), x, textY, labelPaint)
            labelPaint.isFakeBoldText = false
        }
    }

    private fun prefixOf(constellation: Int): String =
        constellationPrefixes.getOrElse(constellation - GnssStatus.CONSTELLATION_GPS) { "?" }

    /** 统计行里的短名（不带国家后缀，太长了排不下）。 */
    private fun shortLabel(constellation: Int): String =
        constellationShortNames.getOrElse(constellation - GnssStatus.CONSTELLATION_GPS) { "其它" }

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
