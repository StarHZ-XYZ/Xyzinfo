package com.rjy.xyz.apps.xyzinfo.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 玻璃背后的**实时磨砂背景**。
 *
 * 这是主流 Android 端做「液态玻璃 / 毛玻璃」的标准做法（BlurView、haze 等库同思路）：
 * 把玻璃所在区域背后的内容截一张快照 → 降采样若干倍 → 在小图上做模糊 →
 * 用 BitmapShader 贴回玻璃形状里。
 *
 * 为什么要降采样：1080p 全屏做一次高斯模糊要几百毫秒，而降到 1/6 之后再模糊
 * 只要零点几毫秒，最后拉伸回去肉眼看不出差别——玻璃本来就要糊。
 *
 * 这样文字和卡片会真的从玻璃下面「糊过去」，而不是只有一层半透明色块。
 */
class GlassBackdrop(private val host: View) {

    /** 降采样倍数：越大越糊、越省性能。 */
    private val downscale = 5

    /** 小图上的模糊半径（× downscale 后就是整屏视觉上的模糊半径）。 */
    private val blurRadius = 3

    private var source: View? = null
    private var bitmap: Bitmap? = null
    private var shader: BitmapShader? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // 玻璃会把背后的颜色提亮、加饱和，这一步让「玻璃感」更接近系统级材质
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix().apply {
                setSaturation(1.5f)
                postConcat(ColorMatrix().apply { setScale(1.06f, 1.06f, 1.06f, 1f) })
            }
        )
        isAntiAlias = true
    }
    private val shaderMatrix = Matrix()

    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private val throttleMillis = 90L

    /** 设置背后要取样的内容视图（通常是页面根布局）。 */
    fun attachSource(view: View) {
        source = view
        requestRefresh(immediate = true)
    }

    /** 节流刷新：滚动时每帧都调也只会按 [throttleMillis] 真正重算一次。 */
    fun requestRefresh(immediate: Boolean = false) {
        if (source == null || !host.isAttachedToWindow) return
        if (immediate) {
            pending = false
            handler.removeCallbacksAndMessages(null)
            rebuild()
            return
        }
        if (pending) return
        pending = true
        handler.postDelayed({
            pending = false
            if (host.isAttachedToWindow) rebuild()
        }, throttleMillis)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        bitmap?.recycle()
        bitmap = null
        shader = null
    }

    private fun rebuild() {
        val src = source ?: return
        val hostWidth = host.width
        val hostHeight = host.height
        if (hostWidth <= 0 || hostHeight <= 0 || src.width <= 0) return

        val targetWidth = max(hostWidth / downscale, 2)
        val targetHeight = max(hostHeight / downscale, 2)

        val current = bitmap
        val target = if (current == null || current.width != targetWidth || current.height != targetHeight) {
            current?.recycle()
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                bitmap = it
                shader = BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
        } else {
            current
        }

        // 把底栏在窗口里的位置换算成「内容视图」坐标系，只把这一块画进小图
        val hostLocation = IntArray(2)
        val sourceLocation = IntArray(2)
        host.getLocationInWindow(hostLocation)
        src.getLocationInWindow(sourceLocation)
        val offsetX = (hostLocation[0] - sourceLocation[0]).toFloat()
        val offsetY = (hostLocation[1] - sourceLocation[1]).toFloat()

        target.eraseColor(0)
        val canvas = Canvas(target)
        val scale = 1f / downscale
        canvas.scale(scale, scale)
        canvas.translate(-offsetX, -offsetY)
        // 如果玻璃自己是内容的一部分（设置页里的活体预览），取快照时要先把自己藏起来，
        // 否则会把上一帧的自己一起糊进去。
        val selfInside = isDescendantOf(host, src)
        val previousVisibility = host.visibility
        if (selfInside) host.visibility = View.INVISIBLE
        runCatching { src.draw(canvas) }
        if (selfInside) host.visibility = previousVisibility

        blur(target, blurRadius)
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    /**
     * 把玻璃背后的内容画进 [bounds]（圆角矩形）。
     *
     * @param zoom 轻微放大，模拟玻璃边缘的折射（越靠边越能看出「放大了一圈」）。
     */
    fun draw(canvas: Canvas, bounds: RectF, cornerRadius: Float, zoom: Float = 1.06f) {
        val target = bitmap
        val bitmapShader = shader
        if (target == null || bitmapShader == null || target.width <= 0) return

        shaderMatrix.reset()
        val sx = (bounds.width() / target.width) * zoom
        val sy = (bounds.height() / target.height) * zoom
        shaderMatrix.setScale(sx, sy)
        // 以玻璃中心为缩放中心，让边缘均匀地「折出去」
        shaderMatrix.postTranslate(
            bounds.centerX() - bounds.width() * zoom / 2f,
            bounds.centerY() - bounds.height() * zoom / 2f
        )
        bitmapShader.setLocalMatrix(shaderMatrix)
        paint.shader = bitmapShader

        canvas.save()
        val path = android.graphics.Path()
        path.addRoundRect(bounds, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawRect(bounds, paint)
        canvas.restore()
        paint.shader = null
    }

    /** 三次盒式模糊近似高斯；只在小图上跑，几十微秒级。 */
    private fun blur(image: Bitmap, radius: Int) {
        if (radius < 1) return
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        val scratch = IntArray(pixels.size)

        repeat(BLUR_PASSES) {
            boxBlurHorizontal(pixels, scratch, width, height, radius)
            boxBlurVertical(scratch, pixels, width, height, radius)
        }
        image.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val color = src[row + i.coerceIn(0, width - 1)]
                a += color ushr 24 and 0xFF
                r += color ushr 16 and 0xFF
                g += color ushr 8 and 0xFF
                b += color and 0xFF
            }
            for (x in 0 until width) {
                dst[row + x] = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
                val outIndex = row + (x - radius).coerceIn(0, width - 1)
                val inIndex = row + (x + radius + 1).coerceIn(0, width - 1)
                val out = src[outIndex]
                val incoming = src[inIndex]
                a += (incoming ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
                r += (incoming ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
                g += (incoming ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
                b += (incoming and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val color = src[i.coerceIn(0, height - 1) * width + x]
                a += color ushr 24 and 0xFF
                r += color ushr 16 and 0xFF
                g += color ushr 8 and 0xFF
                b += color and 0xFF
            }
            for (y in 0 until height) {
                dst[y * width + x] = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
                val outColor = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val inColor = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                a += (inColor ushr 24 and 0xFF) - (outColor ushr 24 and 0xFF)
                r += (inColor ushr 16 and 0xFF) - (outColor ushr 16 and 0xFF)
                g += (inColor ushr 8 and 0xFF) - (outColor ushr 8 and 0xFF)
                b += (inColor and 0xFF) - (outColor and 0xFF)
            }
        }
    }

    private companion object {
        const val BLUR_PASSES = 3
    }
}
