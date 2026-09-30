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
import android.graphics.SweepGradient
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
import kotlin.math.min
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

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

    /**
     * AI 标签（大肥鱼）在底栏里的下标。
     *
     * 设了之后这一格会：① 图标不上单色 tint（保留鲸鱼本身的颜色）；
     * ② 背后画一圈像 Gemini 那样的彩色光晕，让人一眼看出这是 AI 入口。
     */
    var aiTabIndex: Int = -1

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

    private val colorIdle = ContextCompat.getColor(context, R.color.text_tertiary)
    private val colorSelected = ThemeColors.accent(context)
    private val colorTintTop = ContextCompat.getColor(context, R.color.nav_tint_top)
    private val colorTintBottom = ContextCompat.getColor(context, R.color.nav_tint_bottom)
    private val colorGrid = ContextCompat.getColor(context, R.color.nav_grid)
    private val colorLine = ThemeColors.accent(context)
    private val colorBorder = ContextCompat.getColor(context, R.color.nav_border)

    private var selectedIndex = 0
    private var indicatorCenterX = 0f
    private var appliedStyles = -1
    private var lastLaidOutWidth = 0
    /** 手指按下时所在的标签（松手时用来判断是"点"还是"滑"）。 */
    private var tabIndexAtDown = 0
    private var downX = 0f
    private var dragging = false

    private val colorEvaluator = ArgbEvaluator()
    private val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private var moveAnimator: ValueAnimator? = null
    private var glowAnimator: ValueAnimator? = null
    private var glowPulse = 0f
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
                // 不做成 clickable：否则它会自己消费 DOWN，父级就收不到 MOVE/UP，跟手滑动就没了。
                // 点击与滑动统一由底栏自己的 onTouchEvent 处理。
                isClickable = false
                setPadding(0, dp(9f).toInt(), 0, 0)
                addView(icon)
                addView(label)
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

            if (index == aiTabIndex) {
                // AI 图标保留原色（彩色鲸鱼），只靠透明度区分选中态
                icons.getOrNull(index)?.let { icon ->
                    icon.colorFilter = null
                    icon.alpha = if (selected) 1f else 0.78f
                }
            } else if (animated && changing && startColor != target) {
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

    /**
     * 底栏统一手势：
     * - 按下 / 拖动时指示线**跟手移动**，手指经过的标签实时高亮；
     * - 松手才真正切换页面（拖动到别的标签算切换，原地松手算点击）。
     *
     * 之前两个毛病也在这一版去掉：
     * 1. 手指放上去整条会变灰 —— 那是旧的"整条提亮"叠加层，视觉上就是发灰，已删除；
     * 2. 点一下像闪一下 —— 同样来自那层叠加的瞬间出现/消失，现在没有任何整条高亮。
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                dragging = false
                downX = event.x
                tabIndexAtDown = indexAt(event.x)
                backdrop.requestRefresh()
            }

            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - downX) > dp(6f)) dragging = true
                if (dragging) {
                    // 指示线跟手（限制在第一个到最后一个标签中心之间）
                    val first = centerOf(0).takeIf { it > 0f }
                    val last = centerOf(tabs.size - 1).takeIf { it > 0f }
                    if (first != null && last != null) {
                        moveAnimator?.cancel()
                        indicatorCenterX = event.x.coerceIn(first, last)
                        val hover = indexAt(event.x)
                        if (hover != selectedIndex) {
                            val previous = selectedIndex
                            selectedIndex = hover
                            applyItemStyles(previous)
                        }
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                isPressed = false
                val commit = indexAt(event.x)
                if (dragging) {
                    // 跟手结束后落到最近的标签
                    moveTo(commit, Anim.enabled(context))
                    onTabSelected?.invoke(commit, commit == tabIndexAtDown)
                } else {
                    // 原地点击：保留原来的"重复点当前标签弹一下"行为
                    val reselected = commit == tabIndexAtDown && commit == selectedIndex
                    if (reselected) pulse() else moveTo(commit, Anim.enabled(context))
                    onTabSelected?.invoke(commit, reselected)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                dragging = false
                moveTo(selectedIndex.coerceAtLeast(0), Anim.enabled(context))
            }
        }
        return true
    }

    /** 某个 x 坐标落在第几个标签上。 */
    private fun indexAt(x: Float): Int {
        if (items.isEmpty()) return 0
        val width = items.first().width
        if (width <= 0) return 0
        return (x / width).toInt().coerceIn(0, items.size - 1)
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
        // 选中项的柔光晕：中心亮、往外平滑衰减（多段色标，避免出现生硬的圆边）
        glowShader = RadialGradient(
            0f, 0f, h * 3.2f,
            intArrayOf(withAlpha(colorLine, 0x59), withAlpha(colorLine, 0x24), 0x00000000),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
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
        startGlowPulse()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator?.cancel()
        backdrop.release()
    }

    /** AI 光晕的呼吸动画（很轻，2.6 秒一个来回）。 */
    private fun startGlowPulse() {
        if (!Anim.enabled(context) || glowAnimator != null) return
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                glowPulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * AI 图标的**整体描边**（不是外面套一个圆圈）。
     *
     * 做法：把同一个鲸鱼图案**放大一圈**、染成 AI 配色画在图标下面，
     * 露出来的那一圈就是"沿轮廓的发光描边"；叠两层（外层蓝、内层紫）做出渐变感，
     * 再让整体做很轻的呼吸缩放。因为只是把位图放大重绘，硬件加速下也能正常工作
     * （BlurMaskFilter 在硬件层会被忽略，所以不用它）。
     */
    private fun drawAiOutline(canvas: Canvas, h: Float) {
        if (aiTabIndex !in items.indices) return
        val item = items[aiTabIndex]
        val icon = icons.getOrNull(aiTabIndex) ?: return
        if (item.width <= 0 || icon.width <= 0) return
        val source = aiOutlineDrawable ?: ContextCompat
            .getDrawable(context, R.drawable.ic_deepseek_fish)
            ?.also { aiOutlineDrawable = it } ?: return

        // 图标在底栏里的实际中心（item 有 padding，所以要加上 icon 自己的偏移）
        val cx = item.left + icon.left + icon.width / 2f
        val cy = item.top + icon.top + icon.height / 2f
        val baseHalf = icon.width / 2f
        val breath = 1f + 0.05f * glowPulse

        // 外层偏蓝、内层偏紫，两层露出的部分就是渐变描边
        listOf(
            Triple(AI_BLUE, 1.28f, 78),
            Triple(AI_PURPLE, 1.15f, 105)
        ).forEach { (color, scale, alpha) ->
            source.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            val half = baseHalf * scale * breath
            source.setBounds(
                (cx - half).toInt(), (cy - half).toInt(),
                (cx + half).toInt(), (cy + half).toInt()
            )
            source.alpha = alpha
            source.draw(canvas)
            source.alpha = 255
        }
        source.setColorFilter(null)
    }

    private var aiOutlineDrawable: android.graphics.drawable.Drawable? = null

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

        // 3.5) AI 图标的整体发光描边（放大一圈画在图标下面）
        drawAiOutline(canvas, h)

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
            // 图标背后的一团柔光晕（不再是带边的光块）
            glowShader?.let { shader ->
                paint.shader = shader
                paint.alpha = 130
                val cy = h * 0.46f
                canvas.save()
                canvas.translate(indicatorCenterX, cy)
                canvas.drawCircle(0f, 0f, h * 3.2f, paint)
                canvas.restore()
                paint.alpha = 255
                paint.shader = null
            }
        }

        canvas.restoreToCount(checkpoint)
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

        /** Gemini 近似配色：蓝 / 紫 / 粉。 */
        const val AI_BLUE = 0x4285F4
        const val AI_PURPLE = 0x9B72CB
        const val AI_PINK = 0xD96570

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
