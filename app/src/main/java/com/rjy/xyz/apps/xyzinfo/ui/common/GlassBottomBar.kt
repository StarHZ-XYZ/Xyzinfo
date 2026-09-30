package com.rjy.xyz.apps.xyzinfo.ui.common

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R

/**
 * 底栏（极客风）。
 *
 * 不是胶囊、不悬浮：整条**下沉**贴住屏幕底边，背后是内容的实时高斯模糊，
 * 上面叠一套偏工程/仪表盘的视觉语言：
 *
 * 1. 顶部一条 2dp 的强调色**指示线**，会跟着选中项滑动（带轻微回弹），下面带一层渐隐辉光；
 * 2. 细网格纹理（48×48 预渲染 tile 平铺），营造仪表盘/示波器的底噪感；
 * 3. 选中项：图标与文字换成强调色，图标后面有一团径向辉光，并做轻微弹入；
 * 4. 文案用等宽字体 + 字距拉开，未选中项压到 60% 透明度；
 * 5. 顶/底各一条 1px 细线，把底栏和内容分开，又不抢视线。
 *
 * 布局仍然用 LinearLayout 的 weight 等分，指示线在每次 onLayout 后按真实宽度重算。
 */
class GlassBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Tab(val iconRes: Int, val title: String)

    var onTabSelected: ((index: Int, reselected: Boolean) -> Unit)? = null

    /** 点非当前标签时是否先在本页动一次指示线（真实切页时设 false）。 */
    var autoAnimateOnSelect: Boolean = true

    /** 下沉式：直角贴底、无阴影；设置页的活体预览用 false（圆角悬浮）。 */
    var docked: Boolean = false

    private val tabs = mutableListOf<Tab>()
    private val items = mutableListOf<LinearLayout>()
    private val icons = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()

    private val backdrop = GlassBackdrop(this)
    private val barPath = Path()
    private val barRect = RectF()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }

    private val colorIdle = ContextCompat.getColor(context, R.color.text_tertiary)
    private val colorSelected = ContextCompat.getColor(context, R.color.accent)
    private val colorTintTop = ContextCompat.getColor(context, R.color.nav_tint_top)
    private val colorTintBottom = ContextCompat.getColor(context, R.color.nav_tint_bottom)
    private val colorGrid = ContextCompat.getColor(context, R.color.nav_grid)
    private val colorLine = ContextCompat.getColor(context, R.color.accent)
    private val colorBorder = ContextCompat.getColor(context, R.color.nav_border)

    private var selectedIndex = 0
    private var indicatorCenterX = 0f
    private var appliedStyles = -1
    private var lastLaidOutWidth = 0
    private var touchAlpha = 0f

    private val colorEvaluator = ArgbEvaluator()
    private val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private var moveAnimator: ValueAnimator? = null
    private var tintShader: LinearGradient? = null
    private var glowShader: RadialGradient? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (docked) 0f else resources.getDimension(R.dimen.glass_bar_height) / 2f
            setColor(ContextCompat.getColor(context, R.color.glass_fill))
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setWillNotDraw(false)
        isClickable = true
        gridPaint.shader = BitmapShader(gridTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    // ---------- 对外接口 ----------

    fun bind(tabs: List<Tab>, selected: Int, listener: (index: Int, reselected: Boolean) -> Unit) {
        this.tabs.clear()
        this.tabs.addAll(tabs)
        onTabSelected = listener
        // -1 表示子页面：不属于任何标签，指示线隐藏
        selectedIndex = if (selected in tabs.indices) selected else NO_TAB
        buildItems()
    }

    fun attachBackdrop(source: View) = backdrop.attachSource(source)

    fun requestBackdropRefresh(immediate: Boolean = false) = backdrop.requestRefresh(immediate)

    fun setSelectedTab(index: Int, animated: Boolean) {
        if (index != NO_TAB && index !in tabs.indices) return
        val previous = selectedIndex
        selectedIndex = index
        applyItemStyles(previous)
        if (index == NO_TAB) invalidate() else moveTo(index, animated)
    }

    // ---------- 标签 ----------

    private fun buildItems() {
        removeAllViews()
        items.clear()
        icons.clear()
        labels.clear()
        tabs.forEachIndexed { index, tab ->
            val icon = ImageView(context).apply {
                setImageResource(tab.iconRes)
                setColorFilter(colorIdle)
                layoutParams = LayoutParams(dp(21f).toInt(), dp(21f).toInt())
            }
            val label = TextView(context).apply {
                text = tab.title
                textSize = 10f
                typeface = Typeface.MONOSPACE
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(colorIdle)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4f).toInt()
                }
            }
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                setPadding(0, dp(9f).toInt(), 0, 0)
                addView(icon)
                addView(label)
                setOnClickListener { handleClick(index) }
                setOnTouchListener { _, event -> handleTouch(event) }
            }
            addView(item, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            items += item
            icons += icon
            labels += label
        }
        appliedStyles = -1
        applyItemStyles(selectedIndex)
    }

    private fun applyItemStyles(previous: Int) {
        val firstPass = appliedStyles < 0
        val animated = Anim.enabled(context) && !firstPass
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val target = if (selected) colorSelected else colorIdle
            val changing = index == selectedIndex || index == previous
            val startColor = if (selected) colorIdle else colorSelected

            if (animated && changing && startColor != target) {
                ValueAnimator.ofObject(colorEvaluator, startColor, target).apply {
                    duration = Anim.DURATION_MEDIUM
                    addUpdateListener { value ->
                        val color = value.animatedValue as Int
                        icons.getOrNull(index)?.setColorFilter(color)
                        labels.getOrNull(index)?.setTextColor(color)
                    }
                    start()
                }
            } else {
                icons.getOrNull(index)?.setColorFilter(target)
                labels.getOrNull(index)?.setTextColor(target)
            }

            val alpha = if (selected) 1f else 0.6f
            val scale = if (selected) 1.04f else 1f
            item.animate().cancel()
            if (animated) {
                item.animate()
                    .alpha(alpha).scaleX(scale).scaleY(scale)
                    .setDuration(Anim.DURATION_MEDIUM)
                    .setInterpolator(decelerate)
                    .start()
            } else {
                item.alpha = alpha
                item.scaleX = scale
                item.scaleY = scale
            }
        }
        appliedStyles = selectedIndex
    }

    private fun handleClick(index: Int) {
        val reselected = index == selectedIndex
        if (reselected) {
            pulse()
        } else if (autoAnimateOnSelect) {
            val previous = selectedIndex
            selectedIndex = index
            applyItemStyles(previous)
            moveTo(index, Anim.enabled(context))
        }
        onTabSelected?.invoke(index, reselected)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        touchAlpha = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> 1f
            MotionEvent.ACTION_MOVE -> 0.6f
            else -> 0f
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) backdrop.requestRefresh()
        invalidate()
        return false
    }

    private fun pulse() {
        if (!Anim.enabled(context) || selectedIndex == NO_TAB) return
        moveTo(selectedIndex, animated = true)
    }

    private fun moveTo(index: Int, animated: Boolean) {
        val target = centerOf(index)
        if (target <= 0f) {
            post { moveTo(index, animated) }
            return
        }
        moveAnimator?.cancel()
        val startX = if (indicatorCenterX <= 0f) target else indicatorCenterX
        if (!animated || !Anim.enabled(context)) {
            indicatorCenterX = target
            invalidate()
            return
        }
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            addUpdateListener {
                indicatorCenterX = startX + (target - startX) * easeOutBack(it.animatedValue as Float)
                invalidate()
            }
            start()
        }
        backdrop.requestRefresh()
    }

    private fun centerOf(index: Int): Float {
        val item = items.getOrNull(index) ?: return 0f
        if (item.width <= 0) return 0f
        return item.left + item.width / 2f
    }

    // ---------- 布局 ----------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val radius = if (docked) 0f else h / 2f
        (background as? GradientDrawable)?.cornerRadius = radius
        elevation = if (docked) 0f else dp(18f)
        tintShader = LinearGradient(0f, 0f, 0f, h.toFloat(), colorTintTop, colorTintBottom, Shader.TileMode.CLAMP)
        glowShader = RadialGradient(0f, 0f, h * 2.2f, withAlpha(colorLine, 0x66), 0x00000000, Shader.TileMode.CLAMP)
        backdrop.requestRefresh(immediate = true)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val widthNow = r - l
        val widthChanged = widthNow != lastLaidOutWidth
        lastLaidOutWidth = widthNow
        val animating = moveAnimator?.isRunning == true
        if (selectedIndex != NO_TAB && !animating && (changed || widthChanged || indicatorCenterX <= 0f)) {
            val target = centerOf(selectedIndex)
            if (target > 0f && target != indicatorCenterX) {
                indicatorCenterX = target
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        backdrop.requestRefresh(immediate = true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        backdrop.release()
    }

    // ---------- 绘制 ----------

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val radius = if (docked) 0f else h / 2f
        barRect.set(0f, 0f, w, h)
        barPath.reset()
        barPath.addRoundRect(barRect, radius, radius, Path.Direction.CW)

        val checkpoint = canvas.save()
        canvas.clipPath(barPath)

        // 1) 内容的实时高斯模糊
        backdrop.draw(canvas, barRect, radius)

        // 2) 染色：让文字在任何背景上都够清楚
        paint.shader = tintShader
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 3) 网格底噪
        gridPaint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, gridPaint)

        // 4) 选中项：指示线 + 辉光
        if (selectedIndex != NO_TAB) {
            val itemWidth = (items.getOrNull(selectedIndex)?.width ?: 0).toFloat()
            val lineWidth = (itemWidth * 0.42f).coerceIn(dp(28f), dp(84f))
            val lineTop = dp(1.5f)
            // 顶部指示线
            linePaint.color = colorLine
            canvas.drawRect(
                indicatorCenterX - lineWidth / 2f, lineTop,
                indicatorCenterX + lineWidth / 2f, lineTop + dp(2f),
                linePaint
            )
            // 指示线往下的渐隐辉光
            linePaint.shader = LinearGradient(
                0f, lineTop, 0f, lineTop + dp(22f),
                withAlpha(colorLine, 0x59), 0x00000000, Shader.TileMode.CLAMP
            )
            canvas.drawRect(
                indicatorCenterX - lineWidth * 0.8f, lineTop,
                indicatorCenterX + lineWidth * 0.8f, lineTop + dp(22f),
                linePaint
            )
            linePaint.shader = null

            // 图标背后的径向辉光
            glowShader?.let { shader ->
                paint.shader = shader
                paint.alpha = 90
                val cy = h * 0.42f
                canvas.save()
                canvas.translate(indicatorCenterX, cy)
                canvas.drawCircle(0f, 0f, h * 2.2f, paint)
                canvas.restore()
                paint.alpha = 255
                paint.shader = null
            }
        }

        // 5) 按压时整条稍微亮一点
        if (touchAlpha > 0f) {
            paint.color = withAlpha(0xFFFFFF, (touchAlpha * 18).toInt())
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.color = 0
        }

        // 6) 上下的细线
        linePaint.color = withAlpha(colorBorder, 0x66)
        canvas.drawRect(0f, 0f, w, dp(1f), linePaint)
        canvas.drawRect(0f, h - dp(1f), w, h, linePaint)

        canvas.restoreToCount(checkpoint)

        // 悬浮预览模式再补一圈描边
        if (!docked) {
            borderPaint.color = withAlpha(colorBorder, 0x80)
            canvas.drawRoundRect(barRect, radius, radius, borderPaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.15f
        val c3 = c1 + 1f
        val inv = t - 1f
        return 1f + c3 * inv * inv * inv + c1 * inv * inv
    }

    private companion object {
        /** 子页面：底栏不指向任何标签。 */
        const val NO_TAB = -1

        /** 网格底噪：48×48 的预渲染 tile，平铺即可，不用每帧画线。 */
        val gridTile: Bitmap by lazy {
            val size = 48
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                color = 0x0DFFFFFF
                strokeWidth = 1f
            }
            canvas.drawLine(0f, 0f, size.toFloat(), 0f, paint)
            canvas.drawLine(0f, 0f, 0f, size.toFloat(), paint)
            bitmap
        }
    }
}
