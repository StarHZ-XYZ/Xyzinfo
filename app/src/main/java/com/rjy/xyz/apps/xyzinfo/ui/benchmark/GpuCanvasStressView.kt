package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * GPU 压力视图：走系统硬件加速的 2D 渲染管线（Skia → GPU），靠填充率堆负载。
 *
 * 为什么不直接用 OpenGL 写着色器：在真机（Xiaomi Civi / HyperOS + Adreno 6xx）上，
 * 无论着色器多简单，着色器编译都会触发驱动级原生崩溃（SIGSEGV）把 App 杀掉，
 * 而原生崩溃无法捕获。硬件加速 2D 绘制是每个 App 都在用的稳定路径，
 * 负载压在填充率/混合上，测到的同样是真实 GPU 吞吐。
 */
class GpuCanvasStressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var gradients: Array<LinearGradient> = emptyArray()

    private var measuring = false
    private var frames = 0
    private var startNanos = 0L
    private var onMeasured: ((Double) -> Unit)? = null

    /** 开始计时；测满 [MEASURE_SECONDS] 秒后回调帧率（主线程）。 */
    fun startMeasure(callback: (Double) -> Unit) {
        onMeasured = callback
        frames = 0
        startNanos = 0L
        measuring = true
        invalidate()
    }

    fun stopMeasure() {
        measuring = false
        onMeasured = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 渐变着色器只创建一次，避免每帧分配造成 GC 抖动
        gradients = Array(GRADIENT_LAYERS) { index ->
            val mix = index.toFloat() / GRADIENT_LAYERS
            val start = Color.rgb((40 + 180 * mix).toInt(), (90 - 40 * mix).toInt(), (200 - 120 * mix).toInt())
            val end = Color.rgb((120 * mix).toInt(), (200 - 90 * mix).toInt(), (110 + 100 * mix).toInt())
            LinearGradient(
                0f,
                0f,
                w.toFloat().coerceAtLeast(1f),
                h.toFloat().coerceAtLeast(1f),
                start,
                end,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawStressScene(canvas)

        if (!measuring) return
        if (startNanos == 0L) {
            // 第一帧只对齐时间基准
            startNanos = System.nanoTime()
            invalidate()
            return
        }

        frames++
        val seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0
        if (seconds >= MEASURE_SECONDS) {
            measuring = false
            onMeasured?.invoke(frames / seconds)
        } else {
            invalidate()
        }
    }

    /** 全屏渐变叠加 + 大量带阴影的形状，把 GPU 填充率与混合打满。 */
    private fun drawStressScene(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        for (index in 0 until GRADIENT_LAYERS) {
            gradientPaint.shader = gradients.getOrNull(index) ?: continue
            canvas.drawRect(0f, 0f, width, height, gradientPaint)
        }

        shapePaint.setShadowLayer(SHADOW_RADIUS, SHADOW_OFFSET, SHADOW_OFFSET, SHADOW_COLOR)
        for (index in 0 until SHAPE_COUNT) {
            val step = index.toFloat() / SHAPE_COUNT
            val radius = width * (0.05f + 0.35f * step)
            val centerX = width * ((index * 37 % 100) / 100f)
            val centerY = height * ((index * 61 % 100) / 100f)
            rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
            shapePaint.color = Color.argb(90, (60 + 150 * step).toInt(), 120, (200 - 90 * step).toInt())
            canvas.drawOval(rect, shapePaint)
        }
    }

    private companion object {
        const val MEASURE_SECONDS = 3.0
        const val GRADIENT_LAYERS = 200
        const val SHAPE_COUNT = 400
        const val SHADOW_RADIUS = 18f
        const val SHADOW_OFFSET = 8f
        const val SHADOW_COLOR = 0x66000000
    }
}
