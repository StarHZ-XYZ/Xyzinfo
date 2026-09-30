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
import com.rjy.xyz.apps.xyzinfo.data.LocalMapRepository
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

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
    /** 当前使用的地图包（世界 / 国家 / 区域），按定位自动切换。 */
    private var pack: LocalMapRepository.MapPack = LocalMapRepository.WORLD
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isFilterBitmap = true }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val sourceRect = Rect()
    private val destinationRect = RectF()

    private val colorAccent = ThemeColors.accent(context)

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
    /** 地图变换日志的节流时间戳。 */
    private var lastLogAt = 0L

    init {
        setWillNotDraw(false)
        loadMap()
    }

    private fun loadMap() {
        map?.recycle()
        map = LocalMapRepository.load(context, pack)
    }

    /** 经度 → 图内横向比例（按当前地图包自己的 bbox 换算）。 */
    private fun fx(lon: Double): Float = ((lon - pack.left) / pack.spanLon).toFloat()

    /** 纬度 → 图内纵向比例。 */
    private fun fy(lat: Double): Float = ((pack.top - lat) / pack.spanLat).toFloat()

    /** 当前地图包名字（界面提示用）。 */
    val currentPackLabel: String get() = pack.label

    fun updatePosition(lat: Double?, lon: Double?, accuracy: Float, recenter: Boolean = false) {
        // 位置落在哪个地图包里就用哪张图：国家优先于区域，最后兜底世界地图
        val target = LocalMapRepository.findFor(lat, lon)
        if (target.code != pack.code) {
            pack = target
            loadMap()
        }
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

            /*
             * 把「标记点」摆到视图中心。
             *
             * 标记在图片坐标系里的位置是 (drawWidth * fx, drawHeight * fy)，
             * 想让屏幕坐标等于 width/2，就要 left = width/2 - drawWidth * fx。
             * 之前写成 width/2 - markerX（少了左边那一项），缩放后标记会被推出屏幕，
             * 视野正好停在地图自己的中心——经度 0°、纬度 0°，也就是非洲西岸几内亚湾。
             */
            val lat = latitude
            val lon = longitude
            var left: Float
            var top: Float
            if (followPosition && lat != null && lon != null) {
                left = width / 2f - drawWidth * fx(lon)
                top = height / 2f - drawHeight * fy(lat)
            } else {
                left = (width - drawWidth) / 2f + panX
                top = (height - drawHeight) / 2f + panY
            }
            // 边界夹紧：放大后不让地图边缘露进视图里
            left = if (drawWidth <= width) {
                (width - drawWidth) / 2f
            } else {
                left.coerceIn(width - drawWidth, 0f)
            }
            top = if (drawHeight <= height) {
                (height - drawHeight) / 2f
            } else {
                top.coerceIn(height - drawHeight, 0f)
            }
            destinationRect.set(left, top, left + drawWidth, top + drawHeight)
            sourceRect.set(0, 0, image.width, image.height)
            canvas.drawBitmap(image, sourceRect, destinationRect, bitmapPaint)
            drawGraticule(canvas, destinationRect)
            drawMarker(canvas, destinationRect)
            logTransform(left, top, width, height)
        }
    }

    /**
     * 把地图变换的关键值打到 logcat（每秒最多一条）。
     *
     * 之前「放大后跑到非洲」就是这里的算式写错了，留一行日志方便以后在真机上直接核对
     * 「标记是不是真的落在视图中心」。
     */
    private fun logTransform(left: Float, top: Float, viewWidth: Float, viewHeight: Float) {
        val now = System.currentTimeMillis()
        if (now - lastLogAt < 1000L) return
        lastLogAt = now
        val lat = latitude ?: return
        val lon = longitude ?: return
        val markerX = left + drawWidth * ((lon + 180.0) / 360.0).toFloat()
        val markerY = top + drawHeight * ((90.0 - lat) / 180.0).toFloat()
        android.util.Log.d(
            "XyzMap",
            "zoom=$zoom view=${viewWidth.toInt()}x${viewHeight.toInt()} " +
                "draw=${drawWidth.toInt()}x${drawHeight.toInt()} " +
                "left=${left.toInt()} top=${top.toInt()} " +
                "marker=(${markerX.toInt()}, ${markerY.toInt()}) " +
                "center=(${(viewWidth / 2).toInt()}, ${(viewHeight / 2).toInt()}) " +
                "pos=($lat, $lon) follow=$followPosition"
        )
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
        val x = rect.left + rect.width() * fx(lon)
        val y = rect.top + rect.height() * fy(lat)
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
