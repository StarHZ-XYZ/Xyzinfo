package com.rjy.xyz.apps.xyzinfo.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.rjy.xyz.apps.xyzinfo.data.Holiday
import com.rjy.xyz.apps.xyzinfo.data.HolidayTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 节日造型的精灵图工厂（1.0.4）。
 *
 * 飘落的节日粒子和**点击特效**共用这里的画法：都是先在离屏 Canvas 上把造型画一次，
 * 之后每帧只做一次 `drawBitmap`（配合 Matrix 旋转 / 缩放）。
 * 一帧里画十几颗也只是十几次位图绘制，硬件加速下几乎不占时间。
 */
object HolidaySprites {

    /** 画一张 [size]×[size] 的节日精灵图。 */
    fun build(theme: HolidayTheme, accent: Int, accentDeep: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (theme) {
            HolidayTheme.LANTERN -> lantern(canvas, paint, size, accent, accentDeep)
            HolidayTheme.HEART -> heart(canvas, paint, size, accent, accentDeep)
            HolidayTheme.STAR -> star(canvas, paint, size, accent, accentDeep)
            HolidayTheme.FIREWORK -> firework(canvas, paint, size, accent, accentDeep)
            HolidayTheme.MOONCAKE -> mooncake(canvas, paint, size, accent, accentDeep)
            HolidayTheme.GIFT -> gift(canvas, paint, size, accent, accentDeep)
            HolidayTheme.DUMPLING -> dumpling(canvas, paint, size, accent, accentDeep)
            HolidayTheme.BALLOON -> balloon(canvas, paint, size, accent, accentDeep)
            HolidayTheme.SNOWBALL -> snowball(canvas, paint, size, accent, accentDeep)
        }
        return bitmap
    }

    fun build(holiday: Holiday, size: Int): Bitmap =
        build(holiday.theme, holiday.accent, holiday.accentDeep, size)

    private fun lantern(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        val body = RectF(size * 0.24f, size * 0.20f, size * 0.76f, size * 0.70f)
        paint.shader = LinearGradient(
            body.left, body.top, body.right, body.bottom,
            accent, accentDeep, Shader.TileMode.CLAMP
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
        // 灯身上的竖纹
        paint.color = 0x66FFFFFF
        canvas.drawRect(size * 0.47f, size * 0.30f, size * 0.53f, size * 0.62f, paint)
    }

    private fun heart(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        val path = Path()
        path.moveTo(size * 0.5f, size * 0.82f)
        path.cubicTo(size * 0.06f, size * 0.55f, size * 0.16f, size * 0.16f, size * 0.5f, size * 0.36f)
        path.cubicTo(size * 0.84f, size * 0.16f, size * 0.94f, size * 0.55f, size * 0.5f, size * 0.82f)
        path.close()
        paint.shader = LinearGradient(
            size * 0.2f, size * 0.2f, size * 0.8f, size * 0.85f,
            accent, accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = 0x40FFFFFF
        canvas.drawCircle(size * 0.36f, size * 0.37f, size * 0.06f, paint)
    }

    private fun star(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        val path = starPath(size / 2f, size / 2f, size * 0.40f, size * 0.16f)
        paint.shader = LinearGradient(
            size * 0.2f, 0f, size * 0.8f, size.toFloat(),
            accent, accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun firework(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        canvas.drawCircle(
            size / 2f, size / 2f, size * 0.46f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    size / 2f, size / 2f, size * 0.46f,
                    intArrayOf(withAlpha(accent, 170), withAlpha(accentDeep, 0)),
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

    private fun mooncake(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        val body = RectF(size * 0.14f, size * 0.20f, size * 0.86f, size * 0.82f)
        paint.shader = LinearGradient(
            body.left, body.top, body.right, body.bottom,
            accent, accentDeep, Shader.TileMode.CLAMP
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

    private fun gift(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        paint.shader = LinearGradient(
            size * 0.2f, size * 0.3f, size * 0.8f, size * 0.85f,
            accent, accentDeep, Shader.TileMode.CLAMP
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

    private fun dumpling(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        val path = Path()
        path.moveTo(size * 0.5f, size * 0.14f)
        path.lineTo(size * 0.86f, size * 0.80f)
        path.lineTo(size * 0.14f, size * 0.80f)
        path.close()
        paint.shader = LinearGradient(
            size * 0.3f, 0f, size * 0.7f, size.toFloat(),
            accent, accentDeep, Shader.TileMode.CLAMP
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

    private fun balloon(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        paint.shader = LinearGradient(
            size * 0.3f, size * 0.1f, size * 0.7f, size * 0.7f,
            accent, accentDeep, Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(size * 0.24f, size * 0.10f, size * 0.76f, size * 0.68f), paint)
        paint.shader = null
        paint.color = withAlpha(accentDeep, 200)
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

    /** 雪球：白球 + 一圈冷光 + 高光点，圣诞节的点击特效。 */
    private fun snowball(canvas: Canvas, paint: Paint, size: Int, accent: Int, accentDeep: Int) {
        val center = size / 2f
        // 冷色外晕
        paint.shader = RadialGradient(
            center, center, size * 0.48f,
            intArrayOf(withAlpha(accent, 150), withAlpha(accentDeep, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(center, center, size * 0.48f, paint)
        paint.shader = null
        // 球体
        paint.shader = RadialGradient(
            size * 0.38f, size * 0.36f, size * 0.55f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFDCE9F5.toInt(), 0xFFA8C4DE.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(center, center, size * 0.36f, paint)
        paint.shader = null
        // 高光
        paint.color = 0xE6FFFFFF.toInt()
        canvas.drawCircle(size * 0.38f, size * 0.36f, size * 0.09f, paint)
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
