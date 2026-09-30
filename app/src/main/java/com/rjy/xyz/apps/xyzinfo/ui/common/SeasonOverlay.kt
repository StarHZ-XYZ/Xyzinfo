package com.rjy.xyz.apps.xyzinfo.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 四季氛围效果。 */
enum class Season(val label: String) {
    SPRING("春天（花瓣与柳絮）"),
    SUMMER("夏天（阳光光斑）"),
    AUTUMN("秋天（枫叶）"),
    WINTER("冬天（雪花）");

    companion object {
        /** 按当前月份自动决定季节：3~5 春、6~8 夏、9~11 秋、12~2 冬。 */
        fun current(): Season {
            val month = Calendar.getInstance().get(Calendar.MONTH) + 1
            return when (month) {
                in 3..5 -> SPRING
                in 6..8 -> SUMMER
                in 9..11 -> AUTUMN
                else -> WINTER
            }
        }
    }
}

/**
 * 四季氛围层。
 *
 * 铺在内容之上、底栏之下，粒子数量刻意压得很少（20~26 个），
 * 只做位移 + 旋转 + 透明度，笔触都是最简单的图形，保证不掉帧；
 * 页面不在前台（ON_PAUSE）或总动画开关关闭时完全停掉，不参与绘制。
 */
class SeasonOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Flake(
        var x: Float,
        var y: Float,
        var size: Float,
        var speed: Float,
        var drift: Float,
        var phase: Float,
        var rotation: Float,
        var spin: Float,
        var color: Int,
        var alpha: Int
    )

    private val flakes = ArrayList<Flake>(32)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var lastFrameNanos = 0L
    private var running = false

    var season: Season = Season.WINTER
        set(value) {
            if (field != value) {
                field = value
                flakes.clear()
                invalidate()
            }
        }

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
        // 氛围层默认不参与命中测试，任何点击都直接穿透到下面的控件
        isEnabled = false
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        flakes.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        flakes.clear()
        if (w > 0 && h > 0) spawn(w.toFloat(), h.toFloat(), initial = true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running || width <= 0 || height <= 0) return
        if (!SettingsRepository.animationsEnabled(context) ||
            !SettingsRepository.seasonEffectEnabled(context)
        ) {
            running = false
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        if (flakes.isEmpty()) spawn(w, h, initial = true)

        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) {
            0.016f
        } else {
            ((now - lastFrameNanos) / 1e9).toFloat().coerceAtMost(0.05f)
        }
        lastFrameNanos = now

        if (season == Season.SUMMER) drawSummer(canvas, w, h, delta) else drawFalling(canvas, w, h, delta)
        postInvalidateOnAnimation()
    }

    /** 雪花 / 枫叶 / 花瓣：都是「飘落 + 摆动 + 自转」。 */
    private fun drawFalling(canvas: Canvas, w: Float, h: Float, delta: Float) {
        flakes.forEach { flake ->
            flake.phase += delta * flake.drift
            flake.y += flake.speed * delta
            flake.x += sin(flake.phase.toDouble() * 2 * PI).toFloat() * flake.drift * 22f * delta
            flake.rotation += flake.spin * delta
            if (flake.y - flake.size > h) {
                flake.y = -flake.size * 2
                flake.x = Random.nextFloat() * w
            }
            if (flake.x < -flake.size) flake.x = w + flake.size
            if (flake.x > w + flake.size) flake.x = -flake.size

            paint.color = withAlpha(flake.color, flake.alpha)
            when (season) {
                Season.WINTER -> {
                    // 雪花：柔光圆 + 一点点六角感（两条细线）
                    canvas.drawCircle(flake.x, flake.y, flake.size, paint)
                    paint.strokeWidth = 1f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(
                        flake.x - flake.size * 1.6f, flake.y,
                        flake.x + flake.size * 1.6f, flake.y, paint
                    )
                    canvas.drawLine(
                        flake.x, flake.y - flake.size * 1.6f,
                        flake.x, flake.y + flake.size * 1.6f, paint
                    )
                    paint.style = Paint.Style.FILL
                }

                Season.AUTUMN -> {
                    // 枫叶：旋转的小圆角三角块，比纯圆点更像叶子
                    canvas.save()
                    canvas.rotate(flake.rotation, flake.x, flake.y)
                    rect.set(
                        flake.x - flake.size * 1.3f, flake.y - flake.size,
                        flake.x + flake.size * 1.3f, flake.y + flake.size
                    )
                    canvas.drawRoundRect(rect, flake.size * 0.7f, flake.size * 0.7f, paint)
                    canvas.drawLine(flake.x, flake.y - flake.size, flake.x, flake.y + flake.size * 1.4f, paint)
                    canvas.restore()
                }

                else -> {
                    // 花瓣 / 柳絮：椭圆旋转飘落
                    canvas.save()
                    canvas.rotate(flake.rotation, flake.x, flake.y)
                    rect.set(
                        flake.x - flake.size * 1.7f, flake.y - flake.size * 0.8f,
                        flake.x + flake.size * 1.7f, flake.y + flake.size * 0.8f
                    )
                    canvas.drawOval(rect, paint)
                    canvas.restore()
                }
            }
        }
    }

    /** 夏天：右上角一团暖阳光晕 + 缓慢游走的光斑 + 轻微热浪横纹。 */
    private fun drawSummer(canvas: Canvas, w: Float, h: Float, delta: Float) {
        val sunX = w * 0.82f
        val sunY = h * 0.10f
        val radius = w * 0.55f
        paint.color = Color.argb(46, 255, 214, 130)
        canvas.drawCircle(sunX, sunY, radius, paint)
        paint.color = Color.argb(30, 255, 236, 180)
        canvas.drawCircle(sunX, sunY, radius * 0.62f, paint)

        flakes.forEach { spot ->
            spot.phase += delta * spot.drift
            spot.x += cos(spot.phase.toDouble() * 2 * PI).toFloat() * 6f * delta
            spot.y += spot.speed * delta * 0.25f
            if (spot.y > h + spot.size) spot.y = -spot.size
            paint.color = Color.argb(spot.alpha / 3, 255, 228, 160)
            canvas.drawCircle(spot.x, spot.y, spot.size * 2.2f, paint)
        }

        // 热浪：底部几条极淡的横向波纹
        paint.color = Color.argb(14, 255, 255, 255)
        paint.strokeWidth = 2f
        for (line in 0 until 3) {
            val y = h * (0.72f + line * 0.06f) + sin((System.nanoTime() / 1e9 + line).toFloat()) * 6f
            canvas.drawLine(w * 0.05f, y, w * 0.95f, y, paint)
        }
    }

    private fun spawn(w: Float, h: Float, initial: Boolean) {
        val count = if (season == Season.SUMMER) 16 else 24
        repeat(count) {
            val size = when (season) {
                Season.WINTER -> 1.4f + Random.nextFloat() * 2.4f
                Season.AUTUMN -> 2.4f + Random.nextFloat() * 3.2f
                Season.SPRING -> 2.2f + Random.nextFloat() * 2.6f
                Season.SUMMER -> 6f + Random.nextFloat() * 10f
            } * resources.displayMetrics.density
            val color = when (season) {
                Season.WINTER -> Color.WHITE
                Season.AUTUMN -> autumnColors[Random.nextInt(autumnColors.size)]
                Season.SPRING -> springColors[Random.nextInt(springColors.size)]
                Season.SUMMER -> Color.WHITE
            }
            flakes += Flake(
                x = Random.nextFloat() * w,
                y = if (initial) Random.nextFloat() * h else -size * 2,
                size = size,
                speed = (40f + Random.nextFloat() * 70f) * resources.displayMetrics.density,
                drift = 0.4f + Random.nextFloat() * 1.5f,
                phase = Random.nextFloat() * 6.28f,
                rotation = Random.nextFloat() * 360f,
                spin = (-90f + Random.nextFloat() * 180f),
                color = color,
                alpha = 150 + Random.nextInt(80)
            )
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private companion object {
        val autumnColors = intArrayOf(
            Color.parseColor("#E8642C"), Color.parseColor("#D24A1E"),
            Color.parseColor("#C98A2B"), Color.parseColor("#A8481F")
        )
        val springColors = intArrayOf(
            Color.parseColor("#FFB7D5"), Color.parseColor("#FFC9E2"),
            Color.parseColor("#F7E6C4")
        )
    }
}
