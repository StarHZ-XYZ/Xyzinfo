package com.rjy.xyz.apps.xyzinfo.ui.gps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.rjy.xyz.apps.xyzinfo.data.LocalMapRepository
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 定位地图（1.0.7 重写）。
 *
 * 老版本的问题：底图是打包在 assets 里的世界地图（以及从它裁出来的国家 / 区域图），
 * 放大到 4 倍以后视野里只剩一片同色的模糊区域 —— 用户看到的就是"定位之后一片空白"。
 * 离线又没有瓦片服务，靠一张图是解决不了"看细节"的。
 *
 * 所以这里改成**以本机位置为中心的米级网格图**：
 *
 * * 底图（区域 / 世界地图）只在**缩小时**淡淡地铺一层，当个方位参考，放大后自动淡出；
 * * 主体是**随缩放自适应的经纬网格**：线距始终保持在 90dp 左右，每条线标出真实经纬度；
 * * 以本机为圆心画**距离环**（100m / 250m / 500m / 1km …），环上标米数；
 * * 左下角一条**比例尺**（跟随缩放变化），右上角一个**指北针**；
 * * 圆心是本机标记 + 精度圈，左上角用等宽字体写出到小数点后 6 位的经纬度。
 *
 * 这样无论缩放到哪一档，屏幕上永远有可读的坐标信息，不会再出现"一片空白"。
 * 所有数据都在本机算，不需要网络、不需要定位权限之外的任何东西。
 */
class OfflineMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var map: Bitmap? = null
    private var pack: LocalMapRepository.MapPack = LocalMapRepository.WORLD
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isFilterBitmap = true }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sourceRect = android.graphics.Rect()
    private val destinationRect = RectF()

    private val accent = ThemeColors.accent(context)
    private val density = resources.displayMetrics.density

    private var latitude: Double? = null
    private var longitude: Double? = null
    private var accuracyMeters: Float = 0f

    /** 1 = 整幅底图铺满，越大越近；放大到 4 倍以上底图就淡出了。 */
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var followPosition = true
    private var pulse = 0f

    private var lastX = 0f
    private var lastY = 0f

    /** 当前每一像素代表多少米（按纬度修正经度收缩）。 */
    private var metersPerPixel = 0.0
    /** 当前网格步长（度）。 */
    private var gridStep = 1.0

    init {
        setWillNotDraw(false)
        loadMap()
    }

    private fun loadMap() {
        map?.recycle()
        map = LocalMapRepository.load(context, pack)
    }

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** 当前底图包名（界面提示用）。 */
    val currentPackLabel: String get() = pack.label

    fun updatePosition(lat: Double?, lon: Double?, accuracy: Float, recenter: Boolean = false) {
        val target = LocalMapRepository.findFor(lat, lon)
        if (target.code != pack.code) {
            pack = target
            loadMap()
        }
        latitude = lat
        longitude = lon
        accuracyMeters = accuracy
        if (recenter) resetView()
        invalidate()
    }

    fun zoomIn() {
        zoom = min(zoom * 2f, MAX_ZOOM)
        panX = 0f
        panY = 0f
        followPosition = true
        invalidate()
    }

    fun zoomOut() {
        zoom = max(zoom / 2f, 1f)
        panX = 0f
        panY = 0f
        followPosition = true
        invalidate()
    }

    fun resetView() {
        zoom = DEFAULT_ZOOM
        panX = 0f
        panY = 0f
        followPosition = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        canvas.drawColor(if (isDark) 0xFF0C1620.toInt() else 0xFFEDF3FA.toInt())
        updateTransform(width, height)
        drawBaseMap(canvas)
        drawGraticule(canvas, width, height)
        drawRings(canvas, width, height)
        drawMarker(canvas)
        drawScaleBar(canvas, height)
        drawCompass(canvas, width)
        drawCoordinateChip(canvas, width)
    }

    // ---------- 变换（底图、网格、标记共用同一套，缩放拖动永远不会互相错位） ----------

    /** 底图在视图里的位置与尺寸。 */
    private var drawLeft = 0f
    private var drawTop = 0f
    private var drawWidth = 0f
    private var drawHeight = 0f

    private fun updateTransform(viewWidth: Float, viewHeight: Float) {
        val image = map
        val imageWidth = image?.width?.toFloat() ?: pack.spanLon.toFloat()
        val imageHeight = image?.height?.toFloat() ?: pack.spanLat.toFloat()
        val fitScale = min(viewWidth / imageWidth, viewHeight / imageHeight)
        drawWidth = imageWidth * fitScale * zoom
        drawHeight = imageHeight * fitScale * zoom

        val lat = latitude
        val lon = longitude
        if (followPosition && lat != null && lon != null) {
            drawLeft = viewWidth / 2f - drawWidth * fx(lon)
            drawTop = viewHeight / 2f - drawHeight * fy(lat)
        } else {
            drawLeft = (viewWidth - drawWidth) / 2f + panX
            drawTop = (viewHeight - drawHeight) / 2f + panY
        }
        drawLeft = if (drawWidth <= viewWidth) {
            (viewWidth - drawWidth) / 2f
        } else {
            drawLeft.coerceIn(viewWidth - drawWidth, 0f)
        }
        drawTop = if (drawHeight <= viewHeight) {
            (viewHeight - drawHeight) / 2f
        } else {
            drawTop.coerceIn(viewHeight - drawHeight, 0f)
        }

        // 每像素米数：整幅底图的经度跨度对应的实际距离 ÷ 绘制宽度
        val centerLat = latitude ?: 0.0
        metersPerPixel = pack.spanLon * METERS_PER_DEGREE * cos(Math.toRadians(centerLat)) / drawWidth
        if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) metersPerPixel = 1.0
        gridStep = chooseGridStep(metersPerPixel)
    }

    /** 经度 → 视图横坐标。 */
    private fun screenX(lon: Double): Float =
        drawLeft + ((lon - pack.left) / pack.spanLon * drawWidth).toFloat()

    /** 纬度 → 视图纵坐标。 */
    private fun screenY(lat: Double): Float =
        drawTop + ((pack.top - lat) / pack.spanLat * drawHeight).toFloat()

    // ---------- 底图（只在缩小时出现） ----------

    private fun drawBaseMap(canvas: Canvas) {
        val image = map ?: return
        // 放大到 4 倍以上就不画底图了：那张图的精度到此为止，硬画只会是一片糊
        val alpha = when {
            zoom <= 2f -> 150
            zoom <= 4f -> (150 * (4f - zoom) / 2f).toInt()
            else -> 0
        }
        if (alpha <= 4) return

        destinationRect.set(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight)
        sourceRect.set(0, 0, image.width, image.height)
        bitmapPaint.alpha = alpha
        canvas.drawBitmap(image, sourceRect, destinationRect, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    /** 经度 → 底图横向比例。 */
    private fun fx(lon: Double): Float = ((lon - pack.left) / pack.spanLon).toFloat()

    /** 纬度 → 底图纵向比例。 */
    private fun fy(lat: Double): Float = ((pack.top - lat) / pack.spanLat).toFloat()

    // ---------- 经纬网格 ----------

    /**
     * 选一个"整"的经纬步长，让屏幕上的线距落在 90dp 左右。
     * 候选取的都是日常会用的刻度（0.0005° ≈ 55m 一直到 30°），越界自动往回退。
     */
    private fun chooseGridStep(metersPerPixel: Double): Double {
        val targetPx = 110.0 * density
        val targetMeters = targetPx * metersPerPixel
        val targetDegrees = targetMeters / (METERS_PER_DEGREE * cos(Math.toRadians(latitude ?: 0.0)))
        return GRID_STEPS.firstOrNull { it >= targetDegrees } ?: GRID_STEPS.last()
    }

    private fun drawGraticule(canvas: Canvas, width: Float, height: Float) {
        val lat = latitude
        val lon = longitude
        gridPaint.strokeWidth = 1f * density
        // 网格线是放大后的主要信息，对比度给足（太淡就等于没有）
        gridPaint.color = if (isDark) 0x4DFFFFFF else 0x38000000
        textPaint.textSize = 10f * density
        textPaint.color = if (isDark) 0x99FFFFFF.toInt() else 0x99000000.toInt()

        // 没有定位就只画一个装饰性网格，避免纯色空白
        val centerLat = lat ?: ((pack.top + pack.bottom) / 2.0)
        val centerLon = lon ?: ((pack.left + pack.right) / 2.0)
        val pxPerDegreeLon = drawWidth / pack.spanLon
        val pxPerDegreeLat = drawHeight / pack.spanLat
        val halfLon = (width / 2f) / pxPerDegreeLon
        val halfLat = (height / 2f) / pxPerDegreeLat

        val startLon = floor((centerLon - halfLon) / gridStep) * gridStep
        val endLon = centerLon + halfLon
        var value = startLon
        while (value <= endLon) {
            val x = screenX(value)
            if (x >= 0f && x <= width) {
                canvas.drawLine(x, 0f, x, height, gridPaint)
                canvas.drawText(formatLon(value), x + 4f * density, height - 42f * density, textPaint)
            }
            value += gridStep
        }

        val startLat = floor((centerLat - halfLat) / gridStep) * gridStep
        val endLat = centerLat + halfLat
        var latValue = startLat
        while (latValue <= endLat) {
            val y = screenY(latValue)
            if (y >= 0f && y <= height) {
                canvas.drawLine(0f, y, width, y, gridPaint)
                canvas.drawText(formatLat(latValue), 6f * density, y - 5f * density, textPaint)
            }
            latValue += gridStep
        }
    }

    // ---------- 距离环 ----------

    private fun drawRings(canvas: Canvas, width: Float, height: Float) {
        val lat = latitude ?: return
        val lon = longitude ?: return
        val cx = screenX(lon)
        val cy = screenY(lat)
        val step = niceDistance(max(metersPerPixel * height * 0.22, 50.0))
        ringPaint.strokeWidth = 1f * density
        textPaint.textSize = 10f * density
        var ring = step
        while (ring <= 20_000.0) {
            val radiusPx = (ring / metersPerPixel).toFloat()
            if (radiusPx > max(width, height) * 1.5f) break
            if (radiusPx >= 18f * density) {
                ringPaint.color = if (isDark) 0x597FE8E0 else 0x4D0E7C74
                canvas.drawCircle(cx, cy, radiusPx, ringPaint)
                textPaint.color = if (isDark) 0x99FFFFFF.toInt() else 0x99000000.toInt()
                canvas.drawText(distanceLabel(ring), cx + 4f * density, cy - radiusPx + 11f * density, textPaint)
            }
            ring = if (ring < 1000) ring * 2 else ring + step
        }
    }

    /** 取 1/2/5×10^n 里最接近 [value] 的"整"距离。 */
    private fun niceDistance(value: Double): Double {
        if (value <= 0) return 100.0
        val exponent = floor(log10(value))
        val base = 10.0.pow(exponent)
        val normalized = value / base
        val pick = when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 5.0 -> 5.0
            else -> 10.0
        }
        return pick * base
    }

    private fun distanceLabel(meters: Double): String = when {
        meters >= 1000 -> String.format(Locale.US, "%.0f km", meters / 1000.0)
        else -> String.format(Locale.US, "%.0f m", meters)
    }

    // ---------- 标记 / 比例尺 / 指北针 / 坐标 ----------

    private fun drawMarker(canvas: Canvas) {
        val lat = latitude ?: return
        val lon = longitude ?: return
        val cx = screenX(lon)
        val cy = screenY(lat)

        if (accuracyMeters > 0f) {
            val radius = (accuracyMeters / metersPerPixel).toFloat()
                .coerceIn(6f * density, max(this.width.toFloat(), this.height.toFloat()))
            markerPaint.color = withAlpha(accent, 0x26)
            canvas.drawCircle(cx, cy, radius, markerPaint)
            linePaint.strokeWidth = 1.2f * density
            linePaint.color = withAlpha(accent, 0x66)
            canvas.drawCircle(cx, cy, radius, linePaint)
        }

        markerPaint.color = withAlpha(accent, (70 * (1f - pulse)).toInt())
        canvas.drawCircle(cx, cy, (10f + 16f * pulse) * density, markerPaint)
        markerPaint.color = accent
        canvas.drawCircle(cx, cy, 6f * density, markerPaint)
        markerPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 2.4f * density, markerPaint)

        linePaint.strokeWidth = 1.6f * density
        linePaint.color = withAlpha(accent, 0xCC)
        val arm = 18f * density
        canvas.drawLine(cx - arm, cy, cx + arm, cy, linePaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, linePaint)

        if (SettingsRepository.animationsEnabled(context)) {
            pulse += 0.035f
            if (pulse >= 1f) pulse = 0f
            postInvalidateOnAnimation()
        } else {
            pulse = 0f
        }
    }

    private fun drawScaleBar(canvas: Canvas, height: Float) {
        val targetPx = 90f * density
        val meters = niceDistance(targetPx * metersPerPixel)
        val px = (meters / metersPerPixel).toFloat()
        val left = 14f * density
        val bottom = height - 14f * density
        linePaint.strokeWidth = 2f * density
        linePaint.color = if (isDark) 0xCCFFFFFF.toInt() else 0xCC000000.toInt()
        canvas.drawLine(left, bottom, left + px, bottom, linePaint)
        canvas.drawLine(left, bottom - 5f * density, left, bottom + 5f * density, linePaint)
        canvas.drawLine(left + px, bottom - 5f * density, left + px, bottom + 5f * density, linePaint)
        textPaint.textSize = 10f * density
        textPaint.color = linePaint.color
        canvas.drawText(distanceLabel(meters), left + 4f * density, bottom - 8f * density, textPaint)
    }

    private fun drawCompass(canvas: Canvas, width: Float) {
        val cx = width - 30f * density
        val cy = 44f * density
        val r = 16f * density
        linePaint.strokeWidth = 1.2f * density
        linePaint.color = if (isDark) 0x66FFFFFF else 0x44000000
        canvas.drawCircle(cx, cy, r, linePaint)
        val arrow = Path().apply {
            moveTo(cx, cy - r * 0.8f)
            lineTo(cx - r * 0.36f, cy + r * 0.55f)
            lineTo(cx, cy + r * 0.25f)
            lineTo(cx + r * 0.36f, cy + r * 0.55f)
            close()
        }
        markerPaint.color = if (isDark) 0xE6FFFFFF.toInt() else 0xE6000000.toInt()
        canvas.drawPath(arrow, markerPaint)
        textPaint.textSize = 9f * density
        textPaint.color = markerPaint.color
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("N", cx, cy - r - 4f * density, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    /** 左上角坐标条：没有定位时给出提示，绝不留白。 */
    private fun drawCoordinateChip(canvas: Canvas, width: Float) {
        val lat = latitude
        val lon = longitude
        val text = if (lat != null && lon != null) {
            String.format(Locale.US, "%.6f°N  %.6f°E", lat, lon).let {
                if (lat < 0 || lon < 0) {
                    String.format(Locale.US, "%s  %s", formatLat(lat), formatLon(lon))
                } else {
                    it
                }
            }
        } else {
            "等待定位…（底图：${pack.label}）"
        }
        textPaint.textSize = 11f * density
        val padding = 8f * density
        val textWidth = textPaint.measureText(text)
        val rect = RectF(12f * density, 12f * density, min(12f * density + textWidth + padding * 2, width - 12f * density), 40f * density)
        chipPaint.color = if (isDark) 0x99000000.toInt() else 0x99FFFFFF.toInt()
        canvas.drawRoundRect(rect, 8f * density, 8f * density, chipPaint)
        textPaint.color = if (isDark) 0xFFFFFFFF.toInt() else 0xFF11151A.toInt()
        canvas.drawText(text, rect.left + padding, rect.top + 19f * density, textPaint)
    }

    private fun formatLat(value: Double): String =
        String.format(Locale.US, "%.6f°%s", abs(value), if (value >= 0) "N" else "S")

    private fun formatLon(value: Double): String =
        String.format(Locale.US, "%.6f°%s", abs(value), if (value >= 0) "E" else "W")

    // ---------- 触摸 ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                panX += dx
                panY += dy
                followPosition = false
                invalidate()
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private companion object {
        /** 1° 纬度的长度（米）。经度方向要再乘 cos(纬度)。 */
        const val METERS_PER_DEGREE = 111_320.0
        const val DEFAULT_ZOOM = 2f
        const val MAX_ZOOM = 64f

        /** 网格步长候选（度）：从 0.0005°（约 55 米）到 30°。 */
        val GRID_STEPS = listOf(
            0.0005, 0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1,
            0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0
        )
    }
}
