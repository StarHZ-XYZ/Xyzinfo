package com.rjy.xyz.apps.xyzinfo.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 四季氛围效果。 */
enum class Season(val label: String) {
    SPRING("春天（花瓣）"),
    SUMMER("夏天（阳光）"),
    AUTUMN("秋天（枫叶）"),
    WINTER("冬天（雪花）");

    companion object {
        fun current(): Season {
            val month = Calendar.getInstance().get(Calendar.MONTH) + 1
            return when (month) {
                in 3..5 -> SPRING
                in 6..8 -> SUMMER
                in 9..11 -> AUTUMN
                else -> WINTER
            }
        }

        /** 按设置解析季节：auto 走当前月份，其余手动锁定。 */
        fun resolve(context: Context): Season =
            when (SettingsRepository.seasonMode(context)) {
                "spring" -> SPRING
                "summer" -> SUMMER
                "autumn" -> AUTUMN
                "winter" -> WINTER
                else -> current()
            }
    }
}

/**
 * 四季氛围层（v0.8 第二版）。
 *
 * 上一版冬天卡顿的原因：每一帧对**每个**粒子现场画圆 + 两条线，
 * 24 个粒子就是 70 多次矢量绘制，全屏重绘时很吃亏。
 *
 * 现在改成**预渲染精灵图**：每个季节只生成一张 64px 的精灵（雪花 / 枫叶 / 花瓣 / 光斑），
 * 每帧用 Matrix（旋转 + 缩放 + 平移）直接 drawBitmap，一次调用画一个粒子，
 * 绘制开销降到原来的十分之一左右。形状也跟着重画了：
 * 雪花是六芒 + 中心圆 + 柔光晕，枫叶是带叶柄的三裂叶，花瓣是双色椭圆，夏天是暖色光斑。
 *
 * 另外限频 30fps（雪花本来就是慢动作），页面切后台立刻停。
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
        var sway: Float,
        var phase: Float,
        var rotation: Float,
        var spin: Float,
        var alpha: Int
    )

    private val flakes = ArrayList<Flake>(32)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var lastFrameNanos = 0L
    private var running = false
    private var sprite: Bitmap? = null

    var season: Season = Season.WINTER
        set(value) {
            if (field != value) {
                field = value
                sprite = null
                flakes.clear()
                invalidate()
            }
        }

    init {
        isClickable = false
        isFocusable = false
        isEnabled = false
        setWillNotDraw(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        postInvalidateDelayed(FRAME_INTERVAL_MILLIS)
    }

    fun stop() {
        running = false
        flakes.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        flakes.clear()
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
        if (flakes.isEmpty()) spawn(w, h)
        val image = sprite ?: buildSprite().also { sprite = it }

        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) 0.016f
        else ((now - lastFrameNanos) / 1e9).toFloat().coerceAtMost(0.06f)
        lastFrameNanos = now

        flakes.forEach { flake ->
            flake.phase += delta * flake.sway
            flake.y += flake.speed * delta
            flake.x += sin(flake.phase * 2f * PI.toFloat()) * flake.sway * 14f * delta
            flake.rotation += flake.spin * delta

            if (flake.y - flake.size > h) {
                flake.y = -flake.size
                flake.x = Random.nextFloat() * w
            }
            if (flake.x < -flake.size * 2) flake.x = w + flake.size
            if (flake.x > w + flake.size * 2) flake.x = -flake.size

            val scale = flake.size * 2f / image.width
            matrix.reset()
            matrix.postTranslate(-image.width / 2f, -image.height / 2f)
            matrix.postRotate(flake.rotation)
            matrix.postScale(scale, scale)
            matrix.postTranslate(flake.x, flake.y)
            paint.alpha = flake.alpha
            canvas.drawBitmap(image, matrix, paint)
        }
        postInvalidateDelayed(FRAME_INTERVAL_MILLIS)
    }

    // ---------- 精灵图 ----------

    private fun buildSprite(): Bitmap {
        val size = when (season) {
            Season.SUMMER -> 128
            else -> 64
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        when (season) {
            Season.WINTER -> {
                // 柔光晕
                paint.shader = RadialGradient(
                    center, center, center,
                    intArrayOf(0x99FFFFFF.toInt(), 0x33FFFFFF, 0x00FFFFFF),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(center, center, center, paint)
                paint.shader = null
                // 六芒骨架
                paint.color = Color.WHITE
                paint.strokeWidth = size * 0.055f
                paint.strokeCap = Paint.Cap.ROUND
                paint.style = Paint.Style.STROKE
                val arm = center * 0.78f
                for (index in 0 until 3) {
                    val angle = Math.toRadians((index * 60).toDouble())
                    val dx = cos(angle).toFloat() * arm
                    val dy = sin(angle).toFloat() * arm
                    canvas.drawLine(center - dx, center - dy, center + dx, center + dy, paint)
                }
                // 每根主枝上的小分叉，更像雪花
                paint.strokeWidth = size * 0.035f
                for (index in 0 until 6) {
                    val angle = Math.toRadians((index * 60).toDouble())
                    val tipX = center + cos(angle).toFloat() * arm
                    val tipY = center + sin(angle).toFloat() * arm
                    val branch = arm * 0.32f
                    for (offset in intArrayOf(-35, 35)) {
                        val branchAngle = angle + Math.toRadians(offset.toDouble() + 180.0)
                        canvas.drawLine(
                            tipX, tipY,
                            tipX + cos(branchAngle).toFloat() * branch,
                            tipY + sin(branchAngle).toFloat() * branch,
                            paint
                        )
                    }
                }
                paint.style = Paint.Style.FILL
                canvas.drawCircle(center, center, size * 0.07f, paint)
            }

            Season.AUTUMN -> {
                // 三裂枫叶 + 叶柄
                val leaf = Path()
                leaf.moveTo(center, size * 0.10f)
                leaf.cubicTo(size * 0.72f, size * 0.26f, size * 0.94f, size * 0.42f, size * 0.72f, size * 0.56f)
                leaf.cubicTo(size * 0.62f, size * 0.62f, size * 0.58f, size * 0.58f, size * 0.56f, size * 0.68f)
                leaf.cubicTo(size * 0.50f, size * 0.60f, size * 0.42f, size * 0.74f, size * 0.44f, size * 0.86f)
                leaf.cubicTo(size * 0.30f, size * 0.78f, size * 0.22f, size * 0.62f, size * 0.28f, size * 0.46f)
                leaf.cubicTo(size * 0.10f, size * 0.40f, size * 0.28f, size * 0.24f, size * 0.42f, size * 0.30f)
                leaf.cubicTo(size * 0.46f, size * 0.14f, size * 0.60f, size * 0.16f, center, size * 0.10f)
                leaf.close()
                paint.shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    Color.parseColor("#E8642C"), Color.parseColor("#B23A16"),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(leaf, paint)
                paint.shader = null
                paint.color = Color.parseColor("#8C3B12")
                paint.strokeWidth = size * 0.06f
                paint.strokeCap = Paint.Cap.ROUND
                paint.style = Paint.Style.STROKE
                canvas.drawLine(center, size * 0.52f, center, size * 0.96f, paint)
                paint.style = Paint.Style.FILL
            }

            Season.SPRING -> {
                // 双色花瓣：两片椭圆叠在一起，边缘带高光
                val petal = RectF(size * 0.12f, size * 0.30f, size * 0.88f, size * 0.70f)
                paint.shader = LinearGradient(
                    0f, size * 0.30f, 0f, size * 0.70f,
                    Color.parseColor("#FFE3F0"), Color.parseColor("#FFAFCF"),
                    Shader.TileMode.CLAMP
                )
                canvas.save()
                canvas.rotate(24f, center, center)
                canvas.drawOval(petal, paint)
                canvas.restore()
                paint.shader = LinearGradient(
                    0f, size * 0.30f, 0f, size * 0.70f,
                    Color.parseColor("#FFF3F8"), Color.parseColor("#FFC6DC"),
                    Shader.TileMode.CLAMP
                )
                canvas.save()
                canvas.rotate(-28f, center, center)
                canvas.drawOval(petal, paint)
                canvas.restore()
                paint.shader = null
            }

            Season.SUMMER -> {
                // 暖色光斑
                paint.shader = RadialGradient(
                    center, center, center,
                    intArrayOf(
                        Color.argb(150, 255, 240, 190),
                        Color.argb(70, 255, 220, 140),
                        Color.argb(0, 255, 220, 140)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(center, center, center, paint)
                paint.shader = null
            }
        }
        return bitmap
    }

    private fun spawn(w: Float, h: Float) {
        val density = resources.displayMetrics.density
        val count = when (season) {
            Season.WINTER -> 16
            Season.AUTUMN -> 12
            Season.SPRING -> 14
            Season.SUMMER -> 8
        }
        repeat(count) {
            val size = when (season) {
                Season.WINTER -> (7f + Random.nextFloat() * 9f) * density
                Season.AUTUMN -> (9f + Random.nextFloat() * 8f) * density
                Season.SPRING -> (8f + Random.nextFloat() * 6f) * density
                Season.SUMMER -> (26f + Random.nextFloat() * 34f) * density
            }
            flakes += Flake(
                x = Random.nextFloat() * w,
                y = Random.nextFloat() * h,
                size = size,
                speed = when (season) {
                    Season.WINTER -> (26f + Random.nextFloat() * 40f) * density
                    Season.AUTUMN -> (34f + Random.nextFloat() * 46f) * density
                    Season.SPRING -> (22f + Random.nextFloat() * 34f) * density
                    Season.SUMMER -> (6f + Random.nextFloat() * 10f) * density
                },
                sway = 0.4f + Random.nextFloat() * 1.3f,
                phase = Random.nextFloat() * 6.28f,
                rotation = Random.nextFloat() * 360f,
                spin = -70f + Random.nextFloat() * 140f,
                alpha = when (season) {
                    Season.WINTER -> 150 + Random.nextInt(90)
                    Season.AUTUMN -> 165 + Random.nextInt(80)
                    Season.SPRING -> 150 + Random.nextInt(80)
                    Season.SUMMER -> 70 + Random.nextInt(60)
                }
            )
        }
    }

    private companion object {
        /** 氛围层 30fps 足够（雪花叶子是慢动作），省一半绘制也不让设备一直满帧。 */
        const val FRAME_INTERVAL_MILLIS = 33L
    }
}
