package com.rjy.xyz.apps.xyzinfo.ui.hardware

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 屏幕硬件测试视图。
 *
 * 两个模式：
 * - **坏点测试**：整屏纯色循环（白 / 红 / 绿 / 蓝 / 黑 / 灰），点一下换一个颜色，
 *   盯着看有没有发黑、发亮、异色的点；
 * - **触摸测试**：手指划过的地方留下轨迹，同时显示**当前同时按下的触点数**，
 *   可以验证断触、边缘触控和多点触控。
 */
class ScreenTestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val MODE_DEAD_PIXEL = 0
        const val MODE_TOUCH = 1

        private val COLORS = intArrayOf(
            Color.WHITE,
            Color.rgb(255, 0, 0),
            Color.rgb(0, 255, 0),
            Color.rgb(0, 0, 255),
            Color.BLACK,
            Color.rgb(128, 128, 128)
        )
        private val COLOR_NAMES = arrayOf("白", "红", "绿", "蓝", "黑", "灰")
    }

    var mode: Int = MODE_DEAD_PIXEL
        set(value) {
            field = value
            trails.reset()
            pointers.clear()
            invalidate()
        }

    /** 当前模式下给界面显示的提示。 */
    var onHint: ((String) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(0, 255, 200)
    }
    private val trails = Path()
    private val pointers = SparseArray<android.graphics.PointF>()
    private var colorIndex = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mode == MODE_DEAD_PIXEL) {
                    colorIndex = (colorIndex + 1) % COLORS.size
                } else {
                    trails.reset()
                    trails.moveTo(event.x, event.y)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == MODE_TOUCH) {
                    for (index in 0 until event.pointerCount) {
                        if (index == 0) trails.lineTo(event.getX(index), event.getY(index))
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> Unit
        }

        // 记录每个触点的位置，用来画圆点和统计点数
        pointers.clear()
        for (index in 0 until event.pointerCount) {
            pointers.put(event.getPointerId(index), android.graphics.PointF(event.getX(index), event.getY(index)))
        }

        val count = event.pointerCount
        onHint?.invoke(
            if (mode == MODE_DEAD_PIXEL) {
                "坏点测试：${COLOR_NAMES[colorIndex]}（点一下换色）"
            } else {
                "触摸测试：当前 $count 点触控（同时按下几个手指看数字变化）"
            }
        )
        invalidate()
        performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        if (mode == MODE_DEAD_PIXEL) {
            canvas.drawColor(COLORS[colorIndex])
            // 黑/蓝底上用浅色字，浅底上用深色字
            val light = colorIndex == 3 || colorIndex == 4
            paint.color = if (light) Color.WHITE else Color.BLACK
            paint.textSize = 14f * density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "${COLOR_NAMES[colorIndex]}　点一下换下一个颜色（共 ${COLORS.size} 个）",
                width / 2f,
                height - 24f * density,
                paint
            )
            return
        }

        canvas.drawColor(Color.rgb(12, 14, 18))
        // 网格参照，方便看断触位置
        paint.color = 0x22FFFFFF
        paint.strokeWidth = 1f
        val step = min(width, height) / 12f
        var x = step
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += step
        }
        var y = step
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += step
        }

        trailPaint.strokeWidth = 8f * density
        canvas.drawPath(trails, trailPaint)

        for (index in 0 until pointers.size()) {
            val point = pointers.valueAt(index)
            paint.color = 0x66FFFFFF
            canvas.drawCircle(point.x, point.y, 26f * density, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(point.x, point.y, 8f * density, paint)
            paint.color = Color.rgb(0, 255, 200)
            paint.textSize = 12f * density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${index + 1}", point.x, point.y - 30f * density, paint)
        }
    }
}
