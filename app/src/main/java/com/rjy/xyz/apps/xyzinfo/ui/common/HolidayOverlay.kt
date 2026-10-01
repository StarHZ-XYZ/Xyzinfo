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
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.rjy.xyz.apps.xyzinfo.data.Holiday
import com.rjy.xyz.apps.xyzinfo.data.HolidayTheme
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 节日彩蛋层（1.0.4 新增）。
 *
 * 节假日打开软件时，把四季氛围换成节日特效：灯笼 / 爱心 / 五角星 / 烟花 / 月饼 / 礼物 …
 * 掉落一屏，同时在屏幕中间弹一条节日祝福（进场动画走完才弹，免得和开屏抢镜）。
 *
 * 载体和四季氛围一样是**预渲染精灵图 + Matrix 批量绘制**：
 * 每帧只做一次 drawBitmap，粒子数也就十几颗，不参与布局，不挡点击。
 * 特效只在当天是节日时才挂上去，平时这个类根本不会被实例化。
 */
class HolidayOverlay(context: Context) : FrameLayout(context) {

    private val particles = HolidayParticles(context)
    private val greeting = buildGreeting()
    private var started = false

    var holiday: Holiday? = null
        set(value) {
            field = value
            particles.holiday = value
            value?.let { bindGreeting(it) }
        }

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(true)
        addView(
            particles,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addView(
            greeting,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.CENTER }
        )
    }

    fun start() {
        started = true
        particles.start()
        maybeShowGreeting()
    }

    fun stop() {
        started = false
        particles.stop()
    }

    /**
     * 祝福卡：一次启动只弹一次（[greetedThisLaunch]），
     * 而且要等开屏动画彻底结束（延迟 [GREETING_DELAY_MILLIS]）再弹。
     */
    private fun maybeShowGreeting() {
        if (greetedThisLaunch) return
        greetedThisLaunch = true
        greeting.alpha = 0f
        greeting.visibility = View.VISIBLE
        greeting.scaleX = 0.86f
        greeting.scaleY = 0.86f
        greeting.translationY = 24f * resources.displayMetrics.density
        greeting.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setStartDelay(GREETING_DELAY_MILLIS)
            .setDuration(GREETING_IN_MILLIS)
            .withEndAction {
                if (!started) return@withEndAction
                greeting.animate()
                    .alpha(0f)
                    .translationY(-18f * resources.displayMetrics.density)
                    .setStartDelay(GREETING_HOLD_MILLIS)
                    .setDuration(GREETING_OUT_MILLIS)
                    .withEndAction {
                        greeting.animate().setStartDelay(0L)
                        greeting.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }

    private fun buildGreeting(): TextView = TextView(context).apply {
        visibility = View.GONE
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        val density = resources.displayMetrics.density
        setPadding(
            (24 * density).toInt(),
            (18 * density).toInt(),
            (24 * density).toInt(),
            (18 * density).toInt()
        )
        elevation = 12f * density
        maxWidth = (280 * density).toInt()
    }

    private fun bindGreeting(holiday: Holiday) {
        val title = "${holiday.emoji} ${holiday.title}"
        val text = SpannableStringBuilder().append(title).append("\n").append(holiday.wish)
        text.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(AbsoluteSizeSpan(21, true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(
            AbsoluteSizeSpan(13, true),
            title.length + 1,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        greeting.text = text
        val density = resources.displayMetrics.density
        greeting.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(holiday.accent, holiday.accentDeep)
        ).apply {
            cornerRadius = 22f * density
            setStroke((1.2f * density).toInt().coerceAtLeast(1), 0x59FFFFFF)
        }
    }

    private companion object {

        /** 这一条祝福本次启动是否已经弹过（换页面 / 重进首页都不再弹）。 */
        var greetedThisLaunch = false

        /** 等开屏动画结束：开屏最长 1.8s，这里再让出一段时间。 */
        const val GREETING_DELAY_MILLIS = 2600L
        const val GREETING_IN_MILLIS = 420L
        const val GREETING_HOLD_MILLIS = 3200L
        const val GREETING_OUT_MILLIS = 420L
    }
}

/**
 * 节日粒子层：节日造型的精灵图从天而降，带自转与横摆。
 *
 * 和四季氛围同源的做法，但**不读重力传感器**：节日特效只在一年里的十几天出现，
 * 没必要为它常驻一个传感器监听。
 */
private class HolidayParticles(context: Context) : View(context) {

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

    private val flakes = ArrayList<Flake>(24)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var sprite: Bitmap? = null
    private var lastFrameNanos = 0L
    private var running = false

    var holiday: Holiday? = null
        set(value) {
            if (field !== value) {
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        flakes.clear()
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        // 和四季氛围一样不清空粒子：回到前台时接着飘，不会"重来一遍"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val theme = holiday ?: return
        if (!running || width <= 0 || height <= 0) return
        if (!SettingsRepository.animationsEnabled(context)) {
            running = false
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        if (flakes.isEmpty()) spawn(w, h, theme)
        val image = sprite ?: buildSprite(theme).also { sprite = it }

        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) 0.016f
        else ((now - lastFrameNanos) / 1e9).toFloat().coerceAtMost(0.06f)
        lastFrameNanos = now

        flakes.forEach { flake ->
            flake.phase += delta * flake.sway
            flake.y += flake.speed * delta
            flake.x += sin(flake.phase * 2f * PI.toFloat()) * flake.sway * 22f * delta
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
        postInvalidateOnAnimation()
    }

    private fun spawn(w: Float, h: Float, holiday: Holiday) {
        val density = resources.displayMetrics.density
        val count = when (holiday.theme) {
            HolidayTheme.FIREWORK -> 10
            HolidayTheme.MOONCAKE -> 12
            else -> 16
        }
        repeat(count) {
            val size = when (holiday.theme) {
                HolidayTheme.FIREWORK -> (18f + Random.nextFloat() * 22f) * density
                HolidayTheme.MOONCAKE -> (12f + Random.nextFloat() * 8f) * density
                else -> (11f + Random.nextFloat() * 10f) * density
            }
            flakes += Flake(
                x = Random.nextFloat() * w,
                y = Random.nextFloat() * h,
                size = size,
                speed = when (holiday.theme) {
                    HolidayTheme.FIREWORK -> (18f + Random.nextFloat() * 26f) * density
                    else -> (30f + Random.nextFloat() * 42f) * density
                },
                sway = 0.5f + Random.nextFloat() * 1.4f,
                phase = Random.nextFloat() * 6.28f,
                rotation = Random.nextFloat() * 360f,
                spin = -60f + Random.nextFloat() * 120f,
                alpha = when (holiday.theme) {
                    HolidayTheme.FIREWORK -> 90 + Random.nextInt(70)
                    else -> 180 + Random.nextInt(70)
                }
            )
        }
    }

    // ---------- 精灵图：每种节日造型画一次，之后只做位图变换 ----------

    private fun buildSprite(holiday: Holiday): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        when (holiday.theme) {
            HolidayTheme.LANTERN -> drawLantern(canvas, paint, size, holiday)
            HolidayTheme.HEART -> drawHeart(canvas, paint, size, holiday)
            HolidayTheme.STAR -> drawStar(canvas, paint, size, holiday)
            HolidayTheme.FIREWORK -> drawFirework(canvas, paint, size, holiday)
            HolidayTheme.MOONCAKE -> drawMooncake(canvas, paint, size, holiday)
            HolidayTheme.GIFT -> drawGift(canvas, paint, size, holiday)
            HolidayTheme.DUMPLING -> drawDumpling(canvas, paint, size, holiday)
            HolidayTheme.BALLOON -> drawBalloon(canvas, paint, size, holiday)
        }
        return bitmap
    }

    private fun drawLantern(canvas: Canvas, paint: Paint, size: Int, holiday: Holiday) {
        val body = RectF(size * 0.24f, size * 0.20f, size * 0.76f, size * 0.70f)
        paint.shader = LinearGradient(
            body.left, body.top, body.right, body.bottom,
            holiday.accent, holiday.accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawOval(body, paint)
        paint.shader = null
        // 金色上下箍
        paint.color = 0xFFFFD34E.toInt()
        canvas.drawRect(size * 0.30f, size * 0.17f, size * 0.70f, size * 0.24f, paint)
        canvas.drawRect(size * 0.30f, size * 0.67f, size * 0.70f, size * 0.74f, paint)
        // 灯芯 + 流苏
        paint.strokeWidth = size * 0.035f
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        canvas.drawLine(size * 0.5f, size * 0.10f, size * 0.5f, size * 0.17f, paint)
        canvas.drawLine(size * 0.5f, size * 0.74f, size * 0.5f, size * 0.93f, paint)
        paint.style = Paint.Style.FILL
        // 中间的"福"字简化为一道竖纹
        paint.color = 0x66FFFFFF
        canvas.drawRect(size * 0.47f, size * 0.30f, size * 0.53f, size * 0.62f, paint)
    }

    private fun drawHeart(canvas: Canvas, paint: Paint, size: Int, holiday: Holiday) {
        val path = Path()
        path.moveTo(size * 0.5f, size * 0.82f)
        path.cubicTo(size * 0.06f, size * 0.55f, size * 0.16f, size * 0.16f, size * 0.5f, size * 0.36f)
        path.cubicTo(size * 0.84f, size * 0.16f, size * 0.94f, size * 0.55f, size * 0.5f, size * 0.82f)
        path.close()
        paint.shader = LinearGradient(
            size * 0.2f, size * 0.2f, size * 0.8f, size * 0.85f,
            holiday.accent, holiday.accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = 0x40FFFFFF
        canvas.drawCircle(size * 0.36f, size * 0.37f, size * 0.06f, paint)
    }

    private fun drawStar(canvas: Canvas, paint: Paint, size: Int, holiday: Holiday) {
        val path = starPath(size / 2f, size / 2f, size * 0.40f, size * 0.16f)
        paint.shader = LinearGradient(
            size * 0.2f, 0f, size * 0.8f, size.toFloat(),
            holiday.accent, holiday.accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun drawFirework(canvas: Canvas, paint: Paint, size: Int, holiday: Holiday) {
        canvas.drawCircle(
            size / 2f, size / 2f, size * 0.46f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    size / 2f, size / 2f, size * 0.46f,
                    intArrayOf(withAlpha(holiday.accent, 170), withAlpha(holiday.accentDeep, 0)),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        paint.color = Color.WHITE
        paint.strokeWidth = size * 0.045f
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        for (index in 0 until 8) {
            val angle = Math.toRadians((index * 45).toDouble())
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            canvas.drawLine(
                size / 2f + dx * size * 0.10f, size / 2f + dy * size * 0.10f,
                size / 2f + dx * size * 0.44f, size / 2f + dy * size * 0.44f,
                paint
            )
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size * 0.07f, paint)
    }

    private fun drawMooncake(canvas: Canvas, paint: Paint, size: Int, holiday: Holiday) {
        val body = RectF(size * 0.14f, size * 0.20f, size * 0.86f, size * 0.82f)
        paint.shader = LinearGradient(
            body.left, body.top, body.right, body.bottom,
            holiday.accent, holiday.accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(body, size * 0.22f, size * 0.22f, paint)
        paint.shader = null
        paint.color = 0x55FFFFFF
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.03f
        canvas.drawRoundRect(
            RectF(size * 0.22f, size * 0.28f, size * 0.78f, size * 0.74f),
            size * 0.16f, size * 0.16f, paint
        )
        paint.style = Paint.Style.FILL
        paint.color = 0xAAFFFFFF.toInt()
        canvas.drawCircle(size * 0.5f, size * 0.51f, size * 0.10f, paint)
    }

    private fun drawGift(canvas: Canvas, paint: Paint, size: Int, holiday: Holiday) {
        paint.shader = LinearGradient(
            size * 0.2f, size * 0.3f, size * 0.8f, size * 0.85f,
            holiday.accent, holiday.accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(size * 0.16f, size * 0.32f, size * 0.84f, size * 0.86f),
            size * 0.08f, size * 0.08f, paint
        )
        paint.shader = null
        paint.color = 0xFFFFD34E.toInt()
        canvas.drawRect(size * 0.42f, size * 0.32f, size * 0.58f, size * 0.86f, paint)
        canvas.drawRect(size * 0.16f, size * 0.52f, size * 0.84f, size * 0.62f, paint)
        // 蝴蝶结
        canvas.drawOval(RectF(size * 0.22f, size * 0.18f, size * 0.48f, size * 0.36f), paint)
        canvas.drawOval(RectF(size * 0.52f, size * 0.18f, size * 0.78f, size * 0.36f), paint)
    }

    private fun drawDumpling(canvas: Canvas, paint: Paint, size: Int, holiday: Holiday) {
        val path = Path()
        path.moveTo(size * 0.5f, size * 0.14f)
        path.lineTo(size * 0.86f, size * 0.80f)
        path.lineTo(size * 0.14f, size * 0.80f)
        path.close()
        paint.shader = LinearGradient(
            size * 0.3f, 0f, size * 0.7f, size.toFloat(),
            holiday.accent, holiday.accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = 0x66FFFFFF
        paint.strokeWidth = size * 0.04f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(size * 0.30f, size * 0.62f, size * 0.70f, size * 0.62f, paint)
        canvas.drawLine(size * 0.36f, size * 0.46f, size * 0.64f, size * 0.46f, paint)
        // 绑绳
        paint.color = 0xFFFFD34E.toInt()
        canvas.drawLine(size * 0.30f, size * 0.30f, size * 0.70f, size * 0.30f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBalloon(canvas: Canvas, paint: Paint, size: Int, holiday: Holiday) {
        paint.shader = LinearGradient(
            size * 0.3f, size * 0.1f, size * 0.7f, size * 0.7f,
            holiday.accent, holiday.accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(size * 0.24f, size * 0.10f, size * 0.76f, size * 0.68f), paint)
        paint.shader = null
        paint.color = withAlpha(holiday.accentDeep, 200)
        val knot = Path()
        knot.moveTo(size * 0.5f, size * 0.66f)
        knot.lineTo(size * 0.44f, size * 0.76f)
        knot.lineTo(size * 0.56f, size * 0.76f)
        knot.close()
        canvas.drawPath(knot, paint)
        paint.color = 0x99FFFFFF.toInt()
        paint.strokeWidth = size * 0.025f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(size * 0.5f, size * 0.76f, size * 0.5f, size * 0.94f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path {
        val path = Path()
        for (index in 0 until 10) {
            val radius = if (index % 2 == 0) outer else inner
            val angle = Math.toRadians((-90 + index * 36).toDouble())
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
