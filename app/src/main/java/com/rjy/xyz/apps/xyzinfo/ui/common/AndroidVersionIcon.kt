package com.rjy.xyz.apps.xyzinfo.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * 安卓版本图标：2.2 ~ 17+ 每个版本一个「甜点 / 吉祥物」小图标。
 *
 * 全部是**程序化绘制**（Canvas 基本图形），不塞 17 张图片资源：
 * 好处是体积为零、任何分辨率都清晰，也不涉及第三方商标。
 *
 * 版本对应关系（按 AOSP 世代代号）：
 * 2.2 冻酸奶杯 / 2.3 姜饼人 / 3.x 蜂巢 / 4.0 冰淇淋三明治 / 4.1-4.3 果冻豆 /
 * 4.4 巧克力棒 / 5.x 棒棒糖 / 6.0 棉花糖 / 7.x 牛轧糖 / 8.x 奥利奥 /
 * 9.0 派（一角） / 10 墨鱼 / 11 红丝绒蛋糕 / 12 雪糕筒 / 13 提拉米苏 /
 * 14 倒扣蛋糕 / 15 香草冰淇淋 / 16 果仁蜜饼 / 17+ 通用甜点标记
 */
object AndroidVersionIcon {

    /** 每个版本的主色（取「甜点」本身最有辨识度的颜色）。 */
    private fun palette(api: Int): Pair<Int, Int> = when {
        api <= 8 -> 0xFF7ED0F0.toInt() to 0xFF2C7BA8.toInt()      // 2.2
        api <= 10 -> 0xFFB5793A.toInt() to 0xFF6E4520.toInt()     // 2.3
        api <= 13 -> 0xFFF2B33D.toInt() to 0xFFB07A12.toInt()     // 3.x
        api <= 15 -> 0xFFEADFCB.toInt() to 0xFFB49C77.toInt()     // 4.0
        api <= 18 -> 0xFFE8607A.toInt() to 0xFFA82E4A.toInt()     // 4.1-4.3
        api <= 19 -> 0xFFD0362F.toInt() to 0xFF7E1B16.toInt()     // 4.4
        api <= 22 -> 0xFFFF8FB1.toInt() to 0xFFC84E76.toInt()     // 5.x
        api == 23 -> 0xFFFFFFFF.toInt() to 0xFFBFC6CF.toInt()     // 6.0
        api <= 25 -> 0xFFC98A2B.toInt() to 0xFF8A5A14.toInt()     // 7.x
        api <= 27 -> 0xFF3C4A5B.toInt() to 0xFF1B2430.toInt()     // 8.x
        api == 28 -> 0xFFE9B85A.toInt() to 0xFFAE7C1F.toInt()     // 9 派
        api == 29 -> 0xFF9C6FE0.toInt() to 0xFF5D3CA6.toInt()     // 10 墨鱼
        api == 30 -> 0xFFC0392B.toInt() to 0xFF7B1E14.toInt()     // 11 红丝绒
        api == 31 -> 0xFF4FC3F7.toInt() to 0xFF1E88A8.toInt()     // 12 雪糕筒
        api == 32 -> 0xFFD9A066.toInt() to 0xFF9A6A33.toInt()     // 13 提拉米苏
        api == 33 -> 0xFFF0A45C.toInt() to 0xFFB06C24.toInt()     // 14 倒扣蛋糕
        api == 34 -> 0xFFFFF1C2.toInt() to 0xFFD3B15A.toInt()     // 15 香草冰淇淋
        api == 35 -> 0xFFE7B563.toInt() to 0xFFA87322.toInt()     // 16 果仁蜜饼
        else -> 0xFF8FD6A6.toInt() to 0xFF3F9163.toInt()          // 17+ 通用
    }

    /** 生成图标（带圆角底色，容易在文字行里看清楚）。 */
    fun drawable(context: Context, apiLevel: Int, sizePx: Int): Drawable {
        val size = sizePx.coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val (light, dark) = palette(apiLevel)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        // 圆角底 + 品牌色渐变
        paint.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            light, dark, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, size * 0.3f, size * 0.3f, paint)
        paint.shader = null

        // 内部图形用等比坐标（0~1），方便任意尺寸
        val inset = size * 0.16f
        val inner = RectF(inset, inset, size - inset, size - inset)
        paint.color = Color.WHITE
        drawMascot(canvas, apiLevel, inner, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun drawMascot(canvas: Canvas, api: Int, box: RectF, paint: Paint) {
        val w = box.width()
        val h = box.height()
        val cx = box.centerX()
        val cy = box.centerY()
        paint.style = Paint.Style.FILL

        // 2.2 冻酸奶杯
        if (api <= 8) {
            val path = Path().apply {
                moveTo(box.left + w * 0.18f, box.top + h * 0.30f)
                lineTo(box.right - w * 0.18f, box.top + h * 0.30f)
                lineTo(box.right - w * 0.32f, box.bottom)
                lineTo(box.left + w * 0.32f, box.bottom)
                close()
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * 0.10f
            canvas.drawArc(
                RectF(box.left + w * 0.20f, box.top + h * 0.10f, box.right - w * 0.20f, box.top + h * 0.50f),
                200f, 230f, false, paint
            )
            paint.style = Paint.Style.FILL
            return
        }

        // 2.3 姜饼人
        if (api <= 10) {
            canvas.drawCircle(cx, box.top + h * 0.22f, w * 0.20f, paint)
            canvas.drawRoundRect(
                RectF(cx - w * 0.22f, box.top + h * 0.40f, cx + w * 0.22f, box.bottom - h * 0.06f),
                w * 0.16f, w * 0.16f, paint
            )
            paint.strokeWidth = w * 0.11f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(cx - w * 0.24f, box.top + h * 0.52f, cx - w * 0.46f, box.top + h * 0.34f, paint)
            canvas.drawLine(cx + w * 0.24f, box.top + h * 0.52f, cx + w * 0.46f, box.top + h * 0.34f, paint)
            paint.style = Paint.Style.FILL
            return
        }

        // 3.x 蜂巢（六边形）
        if (api <= 13) {
            val path = Path()
            for (index in 0 until 6) {
                val angle = Math.toRadians((60 * index - 30).toDouble())
                val x = cx + (w * 0.48f) * Math.cos(angle).toFloat()
                val y = cy + (h * 0.48f) * Math.sin(angle).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, paint)
            return
        }

        // 4.0 冰淇淋三明治：上下两片饼干 + 中间夹层
        if (api <= 15) {
            canvas.drawRoundRect(RectF(box.left, box.top, box.right, cy), w * 0.14f, w * 0.14f, paint)
            paint.alpha = 170
            canvas.drawRoundRect(RectF(box.left, cy - h * 0.06f, box.right, cy + h * 0.06f), 0f, 0f, paint)
            paint.alpha = 255
            canvas.drawRoundRect(RectF(box.left, cy, box.right, box.bottom), w * 0.14f, w * 0.14f, paint)
            return
        }

        // 4.1-4.3 果冻豆：椭圆
        if (api <= 18) {
            val path = Path().apply {
                addOval(RectF(cx - w * 0.34f, cy - h * 0.46f, cx + w * 0.34f, cy + h * 0.46f), Path.Direction.CW)
            }
            canvas.drawPath(path, paint)
            paint.alpha = 120
            canvas.drawOval(RectF(cx - w * 0.20f, cy - h * 0.30f, cx + w * 0.06f, cy + h * 0.10f), paint)
            paint.alpha = 255
            return
        }

        // 4.4 巧克力棒
        if (api <= 19) {
            canvas.drawRoundRect(RectF(box.left, box.top + h * 0.12f, box.right, box.bottom - h * 0.12f), w * 0.12f, w * 0.12f, paint)
            paint.color = Color.parseColor("#4E342E")
            canvas.drawRect(cx - w * 0.04f, box.top + h * 0.12f, cx + w * 0.04f, box.bottom - h * 0.12f, paint)
            return
        }

        // 5.x 棒棒糖
        if (api <= 22) {
            canvas.drawCircle(cx, box.top + h * 0.34f, w * 0.34f, paint)
            paint.strokeWidth = w * 0.12f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(cx, box.top + h * 0.62f, cx, box.bottom, paint)
            paint.style = Paint.Style.FILL
            return
        }

        // 6.0 棉花糖：蓬松圆柱
        if (api == 23) {
            canvas.drawRoundRect(RectF(cx - w * 0.34f, cy - h * 0.34f, cx + w * 0.34f, cy + h * 0.34f), w * 0.34f, w * 0.34f, paint)
            paint.color = Color.parseColor("#B0BEC5")
            canvas.drawCircle(cx - w * 0.16f, cy, w * 0.07f, paint)
            canvas.drawCircle(cx + w * 0.16f, cy, w * 0.07f, paint)
            return
        }

        // 7.x 牛轧糖
        if (api <= 25) {
            canvas.drawRoundRect(RectF(box.left, box.top + h * 0.18f, box.right, box.bottom - h * 0.18f), w * 0.10f, w * 0.10f, paint)
            paint.color = Color.parseColor("#6D4C41")
            canvas.drawCircle(cx, cy, w * 0.13f, paint)
            return
        }

        // 8.x 奥利奥：外圆 + 内圆 + 夹心
        if (api <= 27) {
            canvas.drawCircle(cx, cy, w * 0.48f, paint)
            paint.color = Color.parseColor("#EDE7F6")
            canvas.drawCircle(cx, cy, w * 0.34f, paint)
            paint.color = Color.parseColor("#3C4A5B")
            canvas.drawCircle(cx, cy, w * 0.26f, paint)
            return
        }

        // 9.0 派：一角派 + 花边
        if (api == 28) {
            val path = Path().apply {
                moveTo(cx, box.bottom)
                lineTo(box.left + w * 0.06f, box.top + h * 0.42f)
                lineTo(box.right - w * 0.06f, box.top + h * 0.42f)
                close()
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * 0.09f
            canvas.drawArc(
                RectF(box.left, box.top + h * 0.20f, box.right, box.top + h * 0.66f),
                180f, 180f, false, paint
            )
            paint.style = Paint.Style.FILL
            return
        }

        // 10 墨鱼：身体 + 两只眼 + 触手
        if (api == 29) {
            canvas.drawRoundRect(
                RectF(cx - w * 0.36f, box.top + h * 0.10f, cx + w * 0.36f, cy + h * 0.16f),
                w * 0.34f, w * 0.34f, paint
            )
            paint.color = Color.parseColor("#4A2C86")
            canvas.drawCircle(cx - w * 0.14f, cy - h * 0.08f, w * 0.07f, paint)
            canvas.drawCircle(cx + w * 0.14f, cy - h * 0.08f, w * 0.07f, paint)
            paint.strokeWidth = w * 0.09f
            paint.style = Paint.Style.STROKE
            for (index in -2..2) {
                val startX = cx + index * w * 0.15f
                canvas.drawLine(startX, cy + h * 0.14f, startX, box.bottom, paint)
            }
            paint.style = Paint.Style.FILL
            return
        }

        // 11 红丝绒蛋糕：三层
        if (api == 30) {
            val left = box.left + w * 0.06f
            val right = box.right - w * 0.06f
            canvas.drawRect(left, box.top + h * 0.20f, right, box.top + h * 0.46f, paint)
            paint.alpha = 190
            canvas.drawRect(left, box.top + h * 0.46f, right, box.top + h * 0.66f, paint)
            paint.alpha = 255
            canvas.drawRect(left, box.top + h * 0.66f, right, box.bottom - h * 0.06f, paint)
            return
        }

        // 12 雪糕筒：三角 + 顶球
        if (api == 31) {
            val path = Path().apply {
                moveTo(cx, box.bottom)
                lineTo(cx - w * 0.30f, cy)
                lineTo(cx + w * 0.30f, cy)
                close()
            }
            canvas.drawPath(path, paint)
            paint.color = Color.parseColor("#FF8A80")
            canvas.drawCircle(cx, cy - h * 0.16f, w * 0.30f, paint)
            return
        }

        // 13 提拉米苏：分层方块
        if (api == 32) {
            canvas.drawRect(box.left, box.top + h * 0.24f, box.right, box.bottom - h * 0.10f, paint)
            paint.color = Color.parseColor("#FFF3E0")
            canvas.drawRect(box.left, box.top + h * 0.42f, box.right, box.top + h * 0.56f, paint)
            canvas.drawRect(box.left, box.top + h * 0.70f, box.right, box.top + h * 0.82f, paint)
            return
        }

        // 14 倒扣蛋糕：蛋糕体 + 底部小三角
        if (api == 33) {
            canvas.drawRoundRect(
                RectF(box.left + w * 0.06f, box.top + h * 0.12f, box.right - w * 0.06f, cy + h * 0.16f),
                w * 0.10f, w * 0.10f, paint
            )
            val path = Path().apply {
                moveTo(cx, box.bottom)
                lineTo(cx - w * 0.22f, cy + h * 0.10f)
                lineTo(cx + w * 0.22f, cy + h * 0.10f)
                close()
            }
            paint.color = Color.parseColor("#FFF3E0")
            canvas.drawPath(path, paint)
            return
        }

        // 15 香草冰淇淋
        if (api == 34) {
            val path = Path().apply {
                moveTo(cx, box.bottom)
                lineTo(cx - w * 0.26f, cy + h * 0.06f)
                lineTo(cx + w * 0.26f, cy + h * 0.06f)
                close()
            }
            canvas.drawPath(path, paint)
            paint.color = Color.parseColor("#FFF8E1")
            canvas.drawCircle(cx, cy - h * 0.14f, w * 0.28f, paint)
            return
        }

        // 16 果仁蜜饼：菱形切块
        if (api == 35) {
            val path = Path().apply {
                moveTo(cx, box.top + h * 0.06f)
                lineTo(box.right, cy)
                lineTo(cx, box.bottom - h * 0.06f)
                lineTo(box.left, cy)
                close()
            }
            canvas.drawPath(path, paint)
            paint.color = Color.parseColor("#8D6E63")
            canvas.drawLine(box.left + w * 0.2f, cy, box.right - w * 0.2f, cy, paint)
            return
        }

        // 17+ 通用甜点标记
        canvas.drawCircle(cx, cy, w * 0.44f, paint)
        paint.color = Color.parseColor("#2E7D32")
        canvas.drawCircle(cx, cy, w * 0.22f, paint)
    }
}
