package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * GPU 压力视图：走系统硬件加速的 2D 渲染管线（Skia → GPU），靠填充率堆负载。
 *
 * 为什么不用 OpenGL 着色器：真机（Xiaomi Civi / HyperOS + Adreno 6xx）上无论着色器多简单，
 * 着色器编译都会触发驱动级原生崩溃（SIGSEGV），原生崩溃无法捕获。硬件加速 2D 绘制
 * 是每个 App 都在用的稳定路径，压的同样是真实 GPU 吞吐。
 *
 * v0.7 的改动：
 * - 压力画面改成**全屏**（0.5 版只有 180dp 高，填充量太小、读数容易虚高）；
 * - 每 5 秒换一种场景（渐变填充 / 混合阴影 / 路径描边 / 小图形），一直跑 20 秒；
 * - 记录每一帧耗时，除平均帧率外还算 1% low 与像素填充率。
 */
class GpuCanvasStressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 原始测量结果。 */
    data class RawResult(
        val framesPerSecond: Double,
        val lowFramesPerSecond: Double,
        val frames: Int,
        val pixelsPerSecond: Double,
        val sceneFramesPerSecond: List<Double>
    )

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val path = Path()
    private var gradients: Array<LinearGradient> = emptyArray()

    private var measuring = false
    private var startNanos = 0L
    private var lastFrameNanos = 0L
    private var lastReportMillis = 0L
    private var pixelsDrawn = 0.0
    private val frameIntervals = ArrayList<Int>(2048)
    private val sceneFrames = IntArray(SCENE_COUNT)
    private val sceneSeconds = DoubleArray(SCENE_COUNT)
    private var sceneIndex = -1
    private var sceneStartNanos = 0L
    private var totalSeconds = 20.0

    private var onProgress: ((Double, Double, Double) -> Unit)? = null
    private var onFinished: ((RawResult) -> Unit)? = null

    fun startMeasure(
        seconds: Double,
        progress: (elapsed: Double, total: Double, fps: Double) -> Unit,
        finished: (RawResult) -> Unit
    ) {
        frameIntervals.clear()
        java.util.Arrays.fill(sceneFrames, 0)
        java.util.Arrays.fill(sceneSeconds, 0.0)
        pixelsDrawn = 0.0
        sceneIndex = -1
        totalSeconds = seconds
        startNanos = 0L
        lastFrameNanos = 0L
        lastReportMillis = 0L
        onProgress = progress
        onFinished = finished
        measuring = true
        invalidate()
    }

    fun stopMeasure() {
        measuring = false
        onProgress = null
        onFinished = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 着色器一次建好，避免每帧分配造成 GC 抖动影响帧率
        gradients = Array(GRADIENT_LAYERS) { index ->
            val mix = index.toFloat() / GRADIENT_LAYERS
            LinearGradient(
                0f,
                0f,
                w.toFloat().coerceAtLeast(1f),
                h.toFloat().coerceAtLeast(1f),
                Color.rgb((40 + 180 * mix).toInt(), (90 - 40 * mix).toInt(), (200 - 120 * mix).toInt()),
                Color.rgb((120 * mix).toInt(), (200 - 90 * mix).toInt(), (110 + 100 * mix).toInt()),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!measuring) {
            drawScene(canvas, 0)
            return
        }

        val now = System.nanoTime()
        if (startNanos == 0L) {
            startNanos = now
            lastFrameNanos = now
            sceneStartNanos = now
            sceneIndex = 0
            invalidate()
            return
        }

        val elapsed = (now - startNanos) / 1e9
        if (elapsed >= totalSeconds) {
            measuring = false
            if (lastFrameNanos > 0) {
                val delta = ((now - lastFrameNanos) / 1_000_000L).toInt()
                if (delta > 0) frameIntervals.add(delta)
            }
            onProgress?.invoke(totalSeconds, totalSeconds, framesPerSecond())
            onFinished?.invoke(result())
            return
        }

        val target = min((elapsed / SCENE_SECONDS).toInt(), SCENE_COUNT - 1)
        if (target != sceneIndex) {
            if (sceneIndex in 0 until SCENE_COUNT) {
                sceneSeconds[sceneIndex] = (now - sceneStartNanos) / 1e9
            }
            sceneIndex = target
            sceneStartNanos = now
        }

        if (lastFrameNanos > 0) {
            val delta = ((now - lastFrameNanos) / 1_000_000L).toInt()
            if (delta > 0 && frameIntervals.size < MAX_FRAMES) frameIntervals.add(delta)
        }
        lastFrameNanos = now
        sceneFrames[sceneIndex.coerceIn(0, SCENE_COUNT - 1)]++

        drawScene(canvas, sceneIndex)
        pixelsDrawn += framePixels(sceneIndex)

        val elapsedMillis = (elapsed * 1000).toLong()
        if (elapsedMillis - lastReportMillis >= 250) {
            lastReportMillis = elapsedMillis
            onProgress?.invoke(elapsed, totalSeconds, framesPerSecond())
        }
        invalidate()
    }

    /** 四种负载轮着来，每种都尽量把每帧填充量压到「全屏 × 40 层」这个量级。 */
    private fun drawScene(canvas: Canvas, scene: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        when (scene) {
            0 -> {
                for (index in 0 until 40) {
                    gradientPaint.shader = gradients.getOrNull(index * 5) ?: continue
                    canvas.drawRect(0f, 0f, w, h, gradientPaint)
                }
            }

            1 -> {
                for (index in 0 until 6) {
                    gradientPaint.shader = gradients.getOrNull(index * 30) ?: continue
                    canvas.drawRect(0f, 0f, w, h, gradientPaint)
                }
                shapePaint.setShadowLayer(SHADOW_RADIUS, SHADOW_OFFSET, SHADOW_OFFSET, SHADOW_COLOR)
                for (index in 0 until 60) {
                    val step = index.toFloat() / 60
                    val radius = w * (0.18f + 0.16f * step)
                    val cx = w * ((index * 37 % 100) / 100f)
                    val cy = h * ((index * 61 % 100) / 100f)
                    rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
                    shapePaint.color = Color.argb(110, (60 + 150 * step).toInt(), 120, (200 - 90 * step).toInt())
                    canvas.drawOval(rect, shapePaint)
                }
                shapePaint.clearShadowLayer()
            }

            2 -> {
                for (index in 0 until 8) {
                    gradientPaint.shader = gradients.getOrNull(index * 25) ?: continue
                    canvas.drawRect(0f, 0f, w, h, gradientPaint)
                }
                strokePaint.strokeWidth = w * 0.02f
                for (line in 0 until 150) {
                    path.reset()
                    path.moveTo(0f, h * ((line * 53 % 100) / 100f))
                    for (segment in 1..8) {
                        path.lineTo(w * segment / 8f, h * (((line * 53 + segment * 29) % 100) / 100f))
                    }
                    strokePaint.color = Color.argb(90, 90, (60 + line % 160), 220)
                    canvas.drawPath(path, strokePaint)
                }
            }

            else -> {
                for (index in 0 until 4) {
                    gradientPaint.shader = gradients.getOrNull(index * 40) ?: continue
                    canvas.drawRect(0f, 0f, w, h, gradientPaint)
                }
                val radius = w * 0.055f
                for (index in 0 until 900) {
                    val cx = w * ((index * 71 % 100) / 100f)
                    val cy = h * ((index * 37 % 100) / 100f)
                    rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
                    shapePaint.color = Color.argb(70, (100 + index % 150), (150 - index % 120), 240)
                    canvas.drawOval(rect, shapePaint)
                }
            }
        }
    }

    /** 估算当前场景每帧实际写出的像素量，用来换算像素填充率。 */
    private fun framePixels(scene: Int): Double {
        val w = width.toFloat()
        val h = height.toFloat()
        val screen = (w * h).toDouble()
        return when (scene) {
            0 -> screen * 40
            1 -> screen * 6 + 60 * min((2f * w * 0.34f).toDouble().let { it * it }, screen) * 2
            2 -> screen * 8 + 150.0 * (w * 0.02f) * (w * 1.35f)
            else -> screen * 4 + 900 * (Math.PI * (w * 0.055) * (w * 0.055)) * 2
        }
    }

    private fun framesPerSecond(): Double {
        val elapsed = (System.nanoTime() - startNanos) / 1e9
        if (elapsed <= 1.0) return 0.0
        val settled = frameIntervals.drop(FIRST_SECOND_FRAMES)
        if (settled.isEmpty()) return 0.0
        val averageMillis = settled.average()
        return if (averageMillis > 0) 1000.0 / averageMillis else 0.0
    }

    private fun result(): RawResult {
        val sorted = frameIntervals.sorted()
        val average = if (sorted.isEmpty()) 0.0 else sorted.average()
        val p99 = if (sorted.isEmpty()) {
            0.0
        } else {
            sorted[((sorted.size - 1) * 0.99).toInt().coerceIn(0, sorted.size - 1)].toDouble()
        }
        val elapsed = (System.nanoTime() - startNanos) / 1e9
        val sceneFps = (0 until SCENE_COUNT).map { index ->
            val seconds = if (sceneSeconds[index] > 0.1) sceneSeconds[index] else SCENE_SECONDS
            sceneFrames[index] / seconds
        }
        return RawResult(
            framesPerSecond = if (average > 0) 1000.0 / average else 0.0,
            lowFramesPerSecond = if (p99 > 0) 1000.0 / p99 else 0.0,
            frames = sorted.size,
            pixelsPerSecond = if (elapsed > 0) pixelsDrawn / elapsed else 0.0,
            sceneFramesPerSecond = sceneFps
        )
    }

    private companion object {
        const val SCENE_COUNT = 4
        const val SCENE_SECONDS = 5.0
        const val GRADIENT_LAYERS = 200
        const val MAX_FRAMES = 6000
        const val FIRST_SECOND_FRAMES = 12
        const val SHADOW_RADIUS = 18f
        const val SHADOW_OFFSET = 10f
        const val SHADOW_COLOR = 0x66000000
    }
}
