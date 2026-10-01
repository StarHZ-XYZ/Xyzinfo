package com.rjy.xyz.apps.xyzinfo.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * 重力开关和四季氛围**共用同一个**（设置里一个开关管两处），
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
        text.setSpan(
            StyleSpan(android.graphics.Typeface.BOLD),
            0,
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
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
 * 重力和四季氛围共用一个开关（[SettingsRepository.seasonGravity]）：
 * 设备往哪边歪，灯笼 / 爱心 / 星星就往哪边斜着落，贴图也跟着倾斜同样的角度。
 * 传感器只在特效运行期间注册，页面不可见时立刻注销。
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

    /** 加速度计原始读数（低通滤波后），只在特效运行期间有值。 */
    private var rawGravityX = 0f
    private var rawGravityY = 0f
    private var gravityEnabled = false
    private var settingsCheckedAt = 0L
    private val sensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val gravityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // 低通滤波：传感器噪声不小，直接用会看到粒子抖
            rawGravityX += (event.values[0] - rawGravityX) * TILT_SMOOTHING
            rawGravityY += (event.values[1] - rawGravityY) * TILT_SMOOTHING
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

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
        settingsCheckedAt = 0L
        syncGravity(force = true)
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        stopGravitySensor()
        // 和四季氛围一样不清空粒子：回到前台时接着飘，不会"重来一遍"
        invalidate()
    }

    /** 重力开关每秒最多读一次；开关变化时立刻注册 / 注销传感器。 */
    private fun syncGravity(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - settingsCheckedAt < SETTINGS_CHECK_INTERVAL_MILLIS) return
        settingsCheckedAt = now
        val wanted = SettingsRepository.seasonGravity(context)
        if (wanted == gravityEnabled) return
        gravityEnabled = wanted
        if (wanted) startGravitySensor() else stopGravitySensor()
    }

    private fun startGravitySensor() {
        val manager = sensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        runCatching {
            manager.registerListener(gravityListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopGravitySensor() {
        val manager = sensorManager ?: return
        runCatching { manager.unregisterListener(gravityListener) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val holiday = holiday ?: return
        if (!running || width <= 0 || height <= 0) return
        syncGravity()
        if (!SettingsRepository.animationsEnabled(context)) {
            running = false
            stopGravitySensor()
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        if (flakes.isEmpty()) spawn(w, h, holiday)
        val image = sprite ?: HolidaySprites.build(holiday, SPRITE_SIZE).also { sprite = it }

        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) 0.016f
        else ((now - lastFrameNanos) / 1e9).toFloat().coerceAtMost(0.06f)
        lastFrameNanos = now

        /*
         * 重力：和四季氛围完全一套算法（同一个 SeasonTilt 映射）。
         * tilt 是下落方向相对竖直方向的偏角，速度分量就是 (sin, cos)，
         * 贴图也跟着倾斜同样的角度 —— 灯笼 / 爱心"顺着重力倒"。
         */
        val tilt = if (gravityEnabled) SeasonTilt.tiltFromAccelerometerX(rawGravityX) else 0f
        val tiltSin = sin(tilt)
        val tiltCos = cos(tilt)
        val tiltDegrees = Math.toDegrees(tilt.toDouble()).toFloat()
        val speedScale = if (gravityEnabled) {
            (1f + (rawGravityY / SeasonTilt.SensorGravity).coerceIn(-1f, 1f) * 0.18f)
        } else {
            1f
        }

        flakes.forEach { flake ->
            flake.phase += delta * flake.sway
            flake.y += flake.speed * speedScale * tiltCos * delta
            flake.x += flake.speed * tiltSin * delta
            flake.x += sin(flake.phase * 2f * PI.toFloat()) * flake.sway * 22f * delta
            flake.rotation += flake.spin * delta

            if (flake.y - flake.size > h) {
                flake.y = -flake.size
                // 沿着当前倾斜方向在屏幕外重排，避免"瞬移"
                flake.x = Random.nextFloat() * w - tiltSin * flake.size * 4f
            }
            if (flake.x < -flake.size * 2) flake.x = w + flake.size
            if (flake.x > w + flake.size * 2) flake.x = -flake.size

            val scale = flake.size * 2f / image.width
            matrix.reset()
            matrix.postTranslate(-image.width / 2f, -image.height / 2f)
            matrix.postRotate(flake.rotation + tiltDegrees)
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

    private companion object {

        /** 精灵图边长：96px 已经足够细腻，一张约 36KB。 */
        const val SPRITE_SIZE = 96

        /** 加速度计低通系数（和四季氛围保持一致）。 */
        const val TILT_SMOOTHING = 0.14f

        /** 重力开关状态每秒最多读一次，省掉每帧读 SharedPreferences。 */
        const val SETTINGS_CHECK_INTERVAL_MILLIS = 1000L
    }
}
