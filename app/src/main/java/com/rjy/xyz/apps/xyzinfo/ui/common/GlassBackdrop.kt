package com.rjy.xyz.apps.xyzinfo.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlin.math.max

/**
 * 底栏背后的**普通高斯模糊**。
 *
 * 做法：把底下那层内容截快照 → 降采样 4 倍 → 四遍盒式模糊（高斯的标准近似）
 * → 用 BitmapShader 贴回底栏形状里。
 *
 * 为什么降采样：全分辨率做高斯要几百毫秒，降到 1/4 之后只要零点几毫秒，
 * 而玻璃本来就要糊，肉眼看不出差别。
 */
class GlassBackdrop(private val host: View) {

    /** 实时模糊：降采样 8 倍够用（反正是"轻微"磨砂），每帧重算的成本降到很低。 */
    private val downscale = 8
    private val blurRadius = 2

    private var source: View? = null
    private var bitmap: Bitmap? = null
    private var shader: BitmapShader? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }
    private val shaderMatrix = Matrix()
    private val clipPath = Path()

    private val handler = Handler(Looper.getMainLooper())
    private var pending = false

    /** 关掉后不再取样重建（跑分期间用，见 [setEnabled]）。 */
    private var enabled = true

    fun attachSource(view: View) {
        source = view
        requestRefresh(immediate = true)
    }

    /**
     * 暂停 / 恢复实时模糊。
     *
     * 为什么要这个开关：重建一次模糊要把**整个页面**重绘进一张位图再模糊，
     * 而页面里任何布局变化（比如跑分时每 250ms 刷一次状态文字）都会触发一次**立即**重建。
     * 跑分页内容长、刷新密，主线程会被这些重绘彻底占满 —— 实测把一次
     * `startActivity` 的调用推迟了 44 秒才执行。跑分期间把玻璃取样停掉，
     * 主线程才能腾出来处理真正重要的事情。
     */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            handler.removeCallbacksAndMessages(null)
            pending = false
        } else {
            requestRefresh(immediate = true)
        }
    }

    /** 滚动时每帧都能调，内部按 80ms 节流，真正重算不会太频繁。 */
    fun requestRefresh(immediate: Boolean = false) {
        if (!enabled) return
        val src = source ?: return
        if (!host.isAttachedToWindow || src.width <= 0) return
        if (immediate) {
            handler.removeCallbacksAndMessages(null)
            pending = false
            rebuild()
            return
        }
        if (pending) return
        pending = true
        handler.postDelayed({
            pending = false
            if (host.isAttachedToWindow) rebuild()
        }, THROTTLE_MILLIS)
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
        if (hostWidth <= 0 || hostHeight <= 0) return

        val targetWidth = max(hostWidth / downscale, 4)
        val targetHeight = max(hostHeight / downscale, 4)
        val current = bitmap
        val small = if (current == null || current.width != targetWidth || current.height != targetHeight) {
            current?.recycle()
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                bitmap = it
                shader = BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
        } else {
            current
        }

        val hostLocation = IntArray(2)
        val sourceLocation = IntArray(2)
        host.getLocationInWindow(hostLocation)
        src.getLocationInWindow(sourceLocation)

        small.eraseColor(0)
        val canvas = Canvas(small)
        canvas.scale(1f / downscale, 1f / downscale)
        canvas.translate(
            -(hostLocation[0] - sourceLocation[0]).toFloat(),
            -(hostLocation[1] - sourceLocation[1]).toFloat()
        )
        // 玻璃自己在内容里（设置页的活体预览）时先藏起来，避免把上一帧的自己糊进去
        val selfInside = isDescendantOf(host, src)
        val previousVisibility = host.visibility
        if (selfInside) host.visibility = View.INVISIBLE
        runCatching { src.draw(canvas) }
        if (selfInside) host.visibility = previousVisibility

        blur(small, blurRadius)
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    /** 把模糊结果画进 [bounds]。 */
    fun draw(canvas: Canvas, bounds: RectF, cornerRadius: Float) {
        val image = bitmap ?: return
        val bitmapShader = shader ?: return
        if (image.width <= 1) return

        shaderMatrix.reset()
        shaderMatrix.setScale(bounds.width() / image.width, bounds.height() / image.height)
        shaderMatrix.postTranslate(bounds.left, bounds.top)
        bitmapShader.setLocalMatrix(shaderMatrix)
        paint.shader = bitmapShader

        clipPath.reset()
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(bounds, paint)
        canvas.restoreToCount(checkpoint)
        paint.shader = null
    }

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
                val out = src[row + (x - radius).coerceIn(0, width - 1)]
                val incoming = src[row + (x + radius + 1).coerceIn(0, width - 1)]
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
                val out = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val incoming = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                a += (incoming ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
                r += (incoming ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
                g += (incoming ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
                b += (incoming and 0xFF) - (out and 0xFF)
            }
        }
    }

    private companion object {
        const val BLUR_PASSES = 4
        /** 节流 110ms ≈ 9fps：滚动时看得出模糊跟着动，又不会把整页重绘拖成卡顿。 */
        const val THROTTLE_MILLIS = 110L
    }
}
