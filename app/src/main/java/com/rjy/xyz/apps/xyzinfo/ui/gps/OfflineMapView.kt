package com.rjy.xyz.apps.xyzinfo.ui.gps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 内置离线地图。
 *
 * 地图是打包在 assets 里的 1920px 等距圆柱投影世界地图，不联网也能用：
 * 经纬度按 `x = (lon + 180) / 360`、`y = (90 - lat) / 180` 直接映射到图片上，
 * 再叠加经纬网、定位标记与精度圈。支持双指缩放 / 拖动，也可以点按钮切换档位。
 */
class OfflineMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var map: Bitmap? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isFilterBitmap = true }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33000000
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val accuracyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val sourceRect = Rect()
    private val destinationRect = RectF()

    private val colorAccent = ContextCompat.getColor(context, R.color.accent)

    private var latitude: Double? = null
    private var longitude: Double? = null
    private var accuracyMeters: Float = 0f

    /** 1 = 整幅世界地图铺满，档位越大越近。 */
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var pulse = 0f

    private var lastX = 0f
    private var lastY = 0f

    init {
        setWillNotDraw(false)
        loadMap()
    }

    private fun loadMap() {
        map = runCatching {
            context.assets.open(MAP_ASSET).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    /**
     * 更新标记位置。
     *
     * @param recenter true 时把视野重新对到本机（缩放到默认档位、清掉平移）
     */
    fun updatePosition(lat: Double?, lon: Double?, accuracy: Float, recenter: Boolean = false) {
        val first = latitude == null
        latitude = lat
        longitude = lon
        accuracyMeters = accuracy
        if ((first || recenter) && lat != null && lon != null) {
            zoom = DEFAULT_ZOOM
            offsetX = 0f
            offsetY = 0f
        }
        invalidate()
    }

    fun zoomIn() {
        zoom = min(zoom * 2f, MAX_ZOOM)
        invalidate()
    }

    fun zoomOut() {
        zoom = max(zoom / 2f, 1f)
        if (zoom == 1f) {
            offsetX = 0f
            offsetY = 0f
        }
        invalidate()
    }

    fun resetView() {
        zoom = DEFAULT_ZOOM
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        drawSurface(canvas, width, height)
        val image = map
        if (image != null) {
            val fitScale = min(width / image.width, height / image.height)
            val drawWidth = image.width * fitScale * zoom
            val drawHeight = image.height * fitScale * zoom
            val left = (width - drawWidth) / 2f + offsetX
            val top = (height - drawHeight) / 2f + offsetY
            destinationRect.set(left, top, left + drawWidth, top + drawHeight)
            sourceRect.set(0, 0, image.width, image.height)
            canvas.drawBitmap(image, sourceRect, destinationRect, bitmapPaint)
            drawGraticule(canvas, destinationRect)
            drawMarker(canvas, destinationRect)
        }
    }

    private fun drawSurface(canvas: Canvas, width: Float, height: Float) {
        canvas.drawColor(if (isDark) 0xFF0E1A24.toInt() else 0xFFE8EFF7.toInt())
    }

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun drawGraticule(canvas: Canvas, rect: RectF) {
        gridPaint.color = if (isDark) 0x33FFFFFF else 0x22000000
        for (lon in -180..180 step 30) {
            val x = rect.left + rect.width() * ((lon + 180f) / 360f)
            canvas.drawLine(x, rect.top, x, rect.bottom, gridPaint)
        }
        for (lat in -60..60 step 30) {
            val y = rect.top + rect.height() * ((90f - lat) / 180f)
            canvas.drawLine(rect.left, y, rect.right, y, gridPaint)
        }
        gridPaint.color = if (isDark) 0x55FFFFFF else 0x40000000
        val equatorY = rect.top + rect.height() * 0.5f
        canvas.drawLine(rect.left, equatorY, rect.right, equatorY, gridPaint)
    }

    private fun drawMarker(canvas: Canvas, rect: RectF) {
        val lat = latitude ?: return
        val lon = longitude ?: return
        val x = rect.left + rect.width() * ((lon + 180.0) / 360.0).toFloat()
        val y = rect.top + rect.height() * ((90.0 - lat) / 180.0).toFloat()
        val density = resources.displayMetrics.density

        // 精度圈：把米换成像素（赤道上 1° 经度约 111.32km）
        if (accuracyMeters > 0f) {
            val metersPerPixel = 111_320.0 * 360.0 / rect.width() * cos(Math.toRadians(lat))
            val radius = (accuracyMeters / metersPerPixel).toFloat().coerceIn(4f * density, rect.width())
            accuracyPaint.color = withAlpha(colorAccent, 0x33)
            canvas.drawCircle(x, y, radius, accuracyPaint)
            accuracyPaint.color = withAlpha(colorAccent, 0x55)
            canvas.drawCircle(x, y, radius, crosshairPaint)
        }

        // 呼吸光晕
        markerPaint.color = withAlpha(colorAccent, (70 * (1f - pulse)).toInt())
        canvas.drawCircle(x, y, (10f + 16f * pulse) * density, markerPaint)

        markerPaint.color = colorAccent
        canvas.drawCircle(x, y, 5.5f * density, markerPaint)
        markerPaint.color = Color.WHITE
        canvas.drawCircle(x, y, 2.2f * density, markerPaint)

        crosshairPaint.color = withAlpha(colorAccent, 0xCC)
        canvas.drawLine(x - 16f * density, y, x + 16f * density, y, crosshairPaint)
        canvas.drawLine(x, y - 16f * density, x, y + 16f * density, crosshairPaint)

        if (!SettingsRepository.animationsEnabled(context)) {
            pulse = 0f
        } else {
            pulse += 0.035f
            if (pulse >= 1f) pulse = 0f
            postInvalidateOnAnimation()
        }
    }

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
                if (zoom > 1f) {
                    offsetX += dx
                    offsetY += dy
                    clampOffsets()
                    invalidate()
                }
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    private fun clampOffsets() {
        val limit = 2000f * resources.displayMetrics.density * (zoom - 1f).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-limit, limit)
        offsetY = offsetY.coerceIn(-limit, limit)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private companion object {
        const val MAP_ASSET = "world_map.png"
        const val DEFAULT_ZOOM = 4f
        const val MAX_ZOOM = 32f
    }
}
