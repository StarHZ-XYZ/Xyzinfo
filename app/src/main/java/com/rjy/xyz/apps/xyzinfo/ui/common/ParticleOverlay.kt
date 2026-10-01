package com.rjy.xyz.apps.xyzinfo.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.Holiday
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 全局点击粒子效果。
 *
 * 盖在整页最上层，但 [onTouchEvent] 永远返回 false——只旁听按下事件，
 * 不消费触摸，所以底下的按钮、卡片、列表点击完全不受影响。
 *
 * 1.0.4 起，节日当天点下去炸的是**节日造型**：过年是烟花、圣诞是雪球、
 * 中秋是月饼 …… 平时还是原来那簇彩色圆点。
 */
class ParticleOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var size: Float,
        var color: Int,
        var rotation: Float = 0f,
        var spin: Float = 0f,
        var sprite: Bitmap? = null
    )

    private class Ring(var x: Float, var y: Float, var life: Float, var maxRadius: Float, var color: Int)

    private val particles = ArrayList<Particle>(48)
    private val rings = ArrayList<Ring>(8)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val density = resources.displayMetrics.density

    /** 节日造型的点击粒子；为 null 时就是默认的彩色圆点。 */
    var holiday: Holiday? = null
        set(value) {
            field = value
            sprite = null
        }

    private var sprite: Bitmap? = null

    private val colors = intArrayOf(
        ThemeColors.accent(context),
        ThemeColors.accentDim(context),
        ContextCompat.getColor(context, R.color.status_warning)
    )

    /** 命名成 particleEnabled 而不是 enabled，避免和 View.setEnabled 撞 JVM 签名。 */
    var particleEnabled: Boolean = true

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (particleEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) {
            // 底栏那一条不炸粒子：点标签本来就会触发页面转场，
            // 两个动画叠在一起（粒子持续重绘 + 窗口转场）是明显的掉帧源。
            if (event.y < height - BOTTOM_SKIP_DP * density) {
                burst(event.x, event.y)
            }
        }
        return false
    }

    /** 页面离开时立刻收干净，避免和转场动画抢帧。 */
    fun stop() {
        if (particles.isEmpty() && rings.isEmpty()) return
        particles.clear()
        rings.clear()
        lastFrame = 0L
        invalidate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) stop()
    }

    /** 在 (x, y) 炸开一小簇粒子 + 一圈扩散波纹。 */
    fun burst(x: Float, y: Float) {
        if (!isAttachedToWindow) return
        val festive = holiday
        rings += Ring(x, y, 1f, 46f * density, festive?.accent ?: colors[0])
        if (festive != null) {
            val image = sprite ?: HolidaySprites.build(festive.tapTheme, festive.accent, festive.accentDeep, TAP_SPRITE_SIZE)
                .also { sprite = it }
            val count = 9
            for (index in 0 until count) {
                val angle = (index.toFloat() / count) * 2f * Math.PI.toFloat() +
                    Random.nextFloat() * 0.5f
                val speed = (90f + Random.nextFloat() * 190f) * density
                particles += Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 60f * density,
                    life = 1f,
                    size = (5f + Random.nextFloat() * 4f) * density,
                    color = festive.accent,
                    rotation = Random.nextFloat() * 360f,
                    spin = -160f + Random.nextFloat() * 320f,
                    sprite = image
                )
            }
            postInvalidateOnAnimation()
            return
        }
        val count = 11
        for (index in 0 until count) {
            val angle = (index.toFloat() / count) * 2f * Math.PI.toFloat() +
                Random.nextFloat() * 0.4f
            val speed = (110f + Random.nextFloat() * 210f) * density
            particles += Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 40f * density,
                life = 1f,
                size = (2.2f + Random.nextFloat() * 2.6f) * density,
                color = colors[Random.nextInt(colors.size)]
            )
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        if (particles.isEmpty() && rings.isEmpty()) return
        val now = System.nanoTime()
        val delta = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1e9).toFloat().coerceAtMost(0.05f)
        lastFrame = now

        var index = particles.size - 1
        while (index >= 0) {
            val particle = particles[index]
            particle.life -= delta * 2.1f
            if (particle.life <= 0f) {
                particles.removeAt(index)
            } else {
                particle.vy += 620f * density * delta
                particle.vx *= 0.985f
                particle.x += particle.vx * delta
                particle.y += particle.vy * delta
                val image = particle.sprite
                if (image != null) {
                    // 节日造型：也是一次 drawBitmap，顺手加个自转
                    particle.rotation += particle.spin * delta
                    val scale = particle.size * particle.life * 2.4f / image.width
                    matrix.reset()
                    matrix.postTranslate(-image.width / 2f, -image.height / 2f)
                    matrix.postRotate(particle.rotation)
                    matrix.postScale(scale, scale)
                    matrix.postTranslate(particle.x, particle.y)
                    spritePaint.alpha = (particle.life * 255).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(image, matrix, spritePaint)
                    index--
                    continue
                }
                // 不用 setShadowLayer：那会让每个粒子走软件渲染，是掉帧的大头。
                // 用「外圈淡 + 内圈实」两层圆模拟柔光，纯硬件绘制。
                paint.color = withAlpha(particle.color, (particle.life * 70).toInt())
                canvas.drawCircle(particle.x, particle.y, particle.size * particle.life * 2.2f, paint)
                paint.color = withAlpha(particle.color, (particle.life * 220).toInt())
                canvas.drawCircle(particle.x, particle.y, particle.size * particle.life, paint)
            }
            index--
        }

        index = rings.size - 1
        while (index >= 0) {
            val ring = rings[index]
            ring.life -= delta * 1.9f
            if (ring.life <= 0f) {
                rings.removeAt(index)
            } else {
                val progress = 1f - ring.life
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f * density
                paint.color = withAlpha(ring.color, (ring.life * 150).toInt())
                canvas.drawCircle(ring.x, ring.y, ring.maxRadius * progress, paint)
                paint.style = Paint.Style.FILL
            }
            index--
        }

        if (particles.isNotEmpty() || rings.isNotEmpty()) postInvalidateOnAnimation() else lastFrame = 0L
    }

    private var lastFrame = 0L

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private companion object {
        /** 底部多少 dp 之内不触发粒子（底栏 + 手势条）。 */
        const val BOTTOM_SKIP_DP = 96f

        /** 节日造型的点击粒子用多大的精灵图（比飘落的小，点一下洒出来更精致）。 */
        const val TAP_SPRITE_SIZE = 64
    }
}
