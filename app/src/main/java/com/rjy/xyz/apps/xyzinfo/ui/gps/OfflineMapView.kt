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
 * 内置离线地图（等距圆柱投影，1920px 世界地图打包在 assets 里）。
 *
 * 关键点是**缩放时的对齐**：放大之后必须让本机位置一直待在视图中心，
 * 而不是让地图围绕它自己的中心（经度 0°，也就是非洲西岸）放大——
 * 之前放大后看到非洲、缩小才看到中国，就是这个原因。
 */
class OfflineMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var map: Bitmap? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isFilterBitmap = true }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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

    /** 用户手动拖动产生的偏移；缩放/回到本机时会清零。 */
    private var panX = 0f
    private var panY = 0f

    /** 让本机位置保持在视图中心（用户一旦手动拖动就暂时关闭）。 */
    private var followPosition = true

    private var drawWidth = 0f
    private var drawHeight = 0f
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

    fun updatePosition(lat: Double?, lon: Double?, accuracy: Float, recenter: Boolean = false) {
        latitude = lat
        longitude = lon
        accuracyMeters = accuracy
        if (recenter) {
            zoom = DEFAULT_ZOOM
            panX = 0f
            panY = 0f
            followPosition = true
        }
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

    /** 回到本机：恢复默认缩放并让标记回到中心。 */
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

        canvas.drawColor(if (isDark) 0xFF0E1A24.toInt() else 0xFFE8EFF7.toInt())
        val image = map
        if (image != null) {
            val fitScale = min(width / image.width, height / image.height)
            drawWidth = image.width * fitScale * zoom
            drawHeight = image.height * fitScale * zoom

            // 关键：把标记点的投影位置摆到视图中心，而不是让地图围绕自己的中心缩放
            var baseX = 0f
            var baseY = 0f
            val lat = latitude
            val lon = longitude
            if (followPosition && lat != null && lon != null) {
                val markerX = drawWidth * ((lon + 180.0) / 360.0).toFloat()
                val markerY = drawHeight * ((90.0 - lat) / 180.0).toFloat()
                // 只有地图比视图大时才需要平移（世界全貌时本来就看得全）
                if (drawWidth > width) baseX = width / 2f - markerX
                if (drawHeight > height) baseY = height / 2f - markerY
            }
            val left = (width - drawWidth) / 2f + baseX + panX
            val top = (height - drawHeight) / 2f + baseY + panY
            destinationRect.set(left, top, left + drawWidth, top + drawHeight)
            sourceRect.set(0, 0, image.width, image.height)
            canvas.drawBitmap(image, sourceRect, destinationRect, bitmapPaint)
            drawGraticule(canvas, destinationRect)
            drawMarker(canvas, destinationRect)
        }
    }

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun drawGraticule(canvas: Canvas, rect: RectF) {
        val density = resources.displayMetrics.density
        gridPaint.strokeWidth = 1f * density
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

        if (accuracyMeters > 0f) {
            val metersPerPixel = 111_320.0 * 360.0 / rect.width() * cos(Math.toRadians(lat))
            val radius = (accuracyMeters / metersPerPixel).toFloat()
                .coerceIn(4f * density, rect.width())
            markerPaint.color = withAlpha(colorAccent, 0x33)
            canvas.drawCircle(x, y, radius, markerPaint)
            crosshairPaint.color = withAlpha(colorAccent, 0x66)
            canvas.drawCircle(x, y, radius, crosshairPaint)
        }

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
                    panX += dx
                    panY += dy
                    // 用户手动拖动后就不再强制居中，直到点「回到本机」
                    followPosition = false
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

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private companion object {
        const val MAP_ASSET = "world_map.png"
        const val DEFAULT_ZOOM = 4f
        const val MAX_ZOOM = 64f
    }
}
