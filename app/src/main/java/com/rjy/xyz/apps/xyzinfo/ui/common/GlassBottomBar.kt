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
    private var fishGlowShader: RadialGradient? = null
    private var touchRadius = 0f
    /** 手指当前所在的 x（底栏坐标系）与按下辉光强度 0~1。 */
    private var touchX = 0f
    private var touchGlow = 0f
    private var touchGlowAnimator: ValueAnimator? = null

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

    /** 跑分等重负载场景：暂停底栏的实时模糊采样，别让全页重绘占满主线程。 */
    fun setBackdropEnabled(enabled: Boolean) = backdrop.setEnabled(enabled)

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
            val iconHeight = dp(21f).toInt()
            val icon = ImageView(context).apply {
                setImageResource(tab.iconRes)
                if (index == aiTabIndex) {
                    /*
                     * 大肥鱼用的是"宽比高长"的原图比例（474:349）。
                     * 如果和别的标签一样塞进 21dp 正方形，它会被压成很小一条，
                     * 高度只有 15dp 左右，看着就是没对齐。
                     * 这里给它 28dp 宽的框 + FIT_CENTER：宽度撑开、**高度和其它图标一致**，
                     * 文字行位置也不变（图标高度仍是 21dp）。
                     */
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LayoutParams(dp(28f).toInt(), iconHeight)
                } else {
                    setColorFilter(colorIdle)
                    layoutParams = LayoutParams(iconHeight, iconHeight)
                }
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
                // AI 图标保留原色（矢量蓝鲸鱼），并且**始终保持满不透明**——
                // 它本身就是彩色图标，压到 60% 会发灰发脏，反而看不清。
                icons.getOrNull(index)?.let { icon ->
                    icon.colorFilter = null
                    icon.alpha = 1f
                }
                // 「大肥鱼」文字始终保留，选中时染成 AI 蓝，和描边呼应
                labels.getOrNull(index)?.let { label ->
                    label.visibility = View.VISIBLE
                    label.setTextColor(if (selected) AI_BLUE else colorIdle)
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

            val alpha = if (index == aiTabIndex || selected) 1f else 0.6f
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
                touchX = event.x
                tabIndexAtDown = indexAt(event.x)
                animateTouchGlow(1f)
                backdrop.requestRefresh()
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.x != touchX) {
                    touchX = event.x
                    invalidate()
                }
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
                animateTouchGlow(0f)
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
                animateTouchGlow(0f)
                moveTo(selectedIndex.coerceAtLeast(0), Anim.enabled(context))
            }
        }
        return true
    }

    /**
     * 手指辉光的淡入淡出：按下 140ms 淡入，松手 260ms 淡出，
     * 避免点一下出现"闪一下"的突兀感。
     */
    private fun animateTouchGlow(target: Float) {
        touchGlowAnimator?.cancel()
        if (!Anim.enabled(context)) {
            touchGlow = target
            invalidate()
            return
        }
        touchGlowAnimator = ValueAnimator.ofFloat(touchGlow, target).apply {
            duration = if (target > touchGlow) 140L else 260L
            interpolator = decelerate
            addUpdateListener {
                touchGlow = it.animatedValue as Float
                invalidate()
            }
            start()
        }
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
        // 大肥鱼被选中：柔光圈换成 AI 蓝紫，一眼区分 AI 入口
        fishGlowShader = RadialGradient(
            0f, 0f, h * 3.2f,
            intArrayOf(withAlpha(AI_BLUE, 0x59), withAlpha(AI_PURPLE, 0x20), 0x00000000),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        // 手指辉光的半径（颜色在绘制时按手指位置混合，见 touchShader()）
        touchRadius = (h * 1.35f).coerceAtLeast(w / 5f * 0.8f)
        touchShaderCache = null
        touchShaderKey = 0
        backdrop.requestRefresh(immediate = true)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // 描边位图要等图标量出尺寸才能生成；没生成就再排一次重绘，
        // 免得关掉动画时（没有呼吸动画驱动 invalidate）它一直不出现。
        val iconWidth = icons.getOrNull(aiTabIndex)?.width ?: 0
        val iconHeight = icons.getOrNull(aiTabIndex)?.height ?: 0
        if (aiStroke == null ||
            (iconWidth > 0 && (aiStrokeIconWidth != iconWidth || aiStrokeIconHeight != iconHeight))
        ) {
            invalidate()
        }
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
     * 参照 Gemini 的做法：**贴着图案轮廓的细描边 + 平滑的颜色渐变**，
     * 不是把图案放大几层叠色块（那种会一圈一圈的色带，看着很突兀）。
     *
     * 具体做法：
     * 1. 先把鲸鱼图案渲染成纯白剪影（只取 alpha 形状）；
     * 2. 把剪影沿圆周平移一整圈 → 数学上的"膨胀"，得到**真正贴轮廓**的一条细边
     *    （半径只有 1.15dp，所以是细描边，不是色块）；
     * 3. 颜色用 **SweepGradient（扫描渐变）**绕图标中心一圈：
     *    蓝 → 紫 → 粉 → 紫 → 蓝，颜色沿周长连续过渡，没有分段色带；
     * 4. 外面再补一层半径 2.1dp、alpha 只有 38 的极淡同色辉光，让描边"发光"但不糊。
     *
     * 整张描边**预渲染成一张位图**（只在图标尺寸变化时重建），每帧只 drawBitmap 一次，
     * 硬件加速下稳定可见、开销极低。绘制时机还是父容器 onDraw（在子 View 图标下面）。
     */
    private fun drawAiOutline(canvas: Canvas, h: Float) {
        if (aiTabIndex !in items.indices) return
        val item = items[aiTabIndex]
        val icon = icons.getOrNull(aiTabIndex) ?: return
        if (item.width <= 0 || icon.width <= 0) return
        val stroke = ensureAiStroke(icon.width, icon.height) ?: return
        val cx = item.left + icon.left + icon.width / 2f
        val cy = item.top + icon.top + icon.height / 2f
        // 很轻的呼吸（±3%），只让描边"活着"，不改变形状
        val breath = 1f + 0.03f * glowPulse
        val checkpoint = canvas.save()
        canvas.scale(breath, breath, cx, cy)
        paint.shader = null
        paint.alpha = 255
        canvas.drawBitmap(stroke, cx - stroke.width / 2f, cy - stroke.height / 2f, paint)
        canvas.restoreToCount(checkpoint)
    }

    /**
     * 预渲染 AI 细描边位图：白剪影 → 圆周膨胀 → 扫描渐变上色。
     * 只有图标尺寸变化时才重建。
     */
    private fun ensureAiStroke(iconWidth: Int, iconHeight: Int): Bitmap? {
        val cached = aiStroke
        if (cached != null && !cached.isRecycled &&
            aiStrokeIconWidth == iconWidth && aiStrokeIconHeight == iconHeight
        ) {
            return cached
        }
        cached?.recycle()

        val pad = dp(3.6f).toInt().coerceAtLeast(3)
        // 底栏里大肥鱼图标是「宽比高长」的（原图比例），所以描边位图要按图标真实方位算，
        // 但位图本身用正方形，扫描渐变才是圆的。
        val boxW = iconWidth + pad * 2
        val boxH = iconHeight + pad * 2
        val box = maxOf(boxW, boxH)
        val offX = (box - boxW) / 2f
        val offY = (box - boxH) / 2f
        val source = ContextCompat.getDrawable(context, R.drawable.ic_deepseek_fish) ?: return null

        // 图标在 ImageView 里的实际绘制区域（FIT_CENTER），描边要贴合它
        val iw = source.intrinsicWidth.takeIf { it > 0 } ?: iconWidth
        val ih = source.intrinsicHeight.takeIf { it > 0 } ?: iconHeight
        val fit = minOf(iconWidth.toFloat() / iw, iconHeight.toFloat() / ih)
        val drawW = iw * fit
        val drawH = ih * fit
        val drawLeft = offX + pad + (iconWidth - drawW) / 2f
        val drawTop = offY + pad + (iconHeight - drawH) / 2f
        val artRect = android.graphics.RectF(
            drawLeft, drawTop, drawLeft + drawW, drawTop + drawH
        )

        // 1) 纯白剪影（只取形状，颜色稍后由扫描渐变统一给）
        val mask = Bitmap.createBitmap(box, box, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        source.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        source.setBounds(
            artRect.left.toInt(), artRect.top.toInt(),
            artRect.right.toInt(), artRect.bottom.toInt()
        )
        source.draw(maskCanvas)
        source.setColorFilter(null)

        // 2) 膨胀成环：先画纯白的膨胀层，再把剪影挖掉 → 只剩贴着轮廓的一圈
        val ring = Bitmap.createBitmap(box, box, Bitmap.Config.ARGB_8888)
        val ringCanvas = Canvas(ring)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        whitePaint.color = android.graphics.Color.WHITE
        val src = android.graphics.Rect(0, 0, mask.width, mask.height)
        // 先铺一层极淡的外辉光（大圈、低透明），再压上细描边
        whitePaint.alpha = 38
        scatterRing(ringCanvas, mask, src, whitePaint, dp(2.3f))
        whitePaint.alpha = 255
        scatterRing(ringCanvas, mask, src, whitePaint, dp(1.15f))
        /*
         * 关键一步：把剪影本身**挖空**。
         *
         * 膨胀之后得到的是"整条鱼"，如果不挖空，这层彩色就会垫在鱼身下面；
         * 而底栏未选中项只有 60% 不透明度，颜色会从鱼身里透出来，把图标糊成一团
         * （上一版就是这个问题）。挖空之后只剩贴着轮廓的那一圈细边，
         * 鱼身保持自己的颜色，描边才真的像 Gemini 那样"描"在外面。
         */
        val punch = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        punch.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        ringCanvas.drawBitmap(mask, null, artRect, punch)
        punch.xfermode = null

        /*
         * 3) 上色。
         *
         * 注意：`canvas.drawBitmap(位图, …, paint)` **不会**用 paint 上的渐变着色器
         * 给位图重新上色（真机导出的诊断图证明它老老实实画了白色），
         * 所以这里是先把扫描渐变铺满整张位图，再用描边环当作"蒙版"抠出来（DST_IN）——
         * 这样描边的颜色才是蓝→紫→粉连续的渐变。
         */
        val out = Bitmap.createBitmap(box, box, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(out)
        val half = box / 2f
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        gradientPaint.shader = SweepGradient(
            half, half,
            intArrayOf(AI_BLUE, AI_PURPLE, AI_PINK, AI_PURPLE, AI_BLUE),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        )
        outCanvas.drawRect(0f, 0f, box.toFloat(), box.toFloat(), gradientPaint)
        val keepPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        keepPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        outCanvas.drawBitmap(ring, 0f, 0f, keepPaint)
        keepPaint.xfermode = null
        ring.recycle()
        mask.recycle()

        aiStroke = out
        aiStrokeIconWidth = iconWidth
        aiStrokeIconHeight = iconHeight
        return out
    }

    /** 把剪影沿圆周平移一整圈 = 形态学膨胀，得到一条贴着轮廓的边。 */
    private fun scatterRing(canvas: Canvas, mask: Bitmap, src: android.graphics.Rect, paint: Paint, radius: Float) {
        if (radius <= 0.4f) return
        val steps = 40
        for (i in 0 until steps) {
            val angle = Math.PI * 2 * i / steps
            val dx = (radius * kotlin.math.cos(angle)).toInt()
            val dy = (radius * kotlin.math.sin(angle)).toInt()
            canvas.drawBitmap(
                mask, src,
                android.graphics.Rect(dx, dy, dx + mask.width, dy + mask.height),
                paint
            )
        }
    }

    private var aiStroke: Bitmap? = null
    private var aiStrokeIconWidth = 0
    private var aiStrokeIconHeight = 0

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

        /*
         * 3.5) 手指辉光：颜色**渐变**着跟着手指走。
         *
         * 早先按"落在哪一格"直接切色，手指划过格子边界时颜色会"啪"地一下变蓝，很突兀。
         * 现在改成按手指到大肥鱼格中心的距离算混合量：
         *   正中心 = 100% AI 蓝紫；离得越远越低；一格宽以外 = 0%。
         * 两套 shader 按这个比例互相淡入淡出，于是从首页滑到大肥鱼是一路渐变过去的。
         */
        if (touchGlow > 0.01f) {
            val mix = aiMixAt(touchX)
            val base = (255 * touchGlow).toInt().coerceIn(0, 255)
            drawTouchGlow(canvas, h, touchShader(mix), base)
        }

        // 4) 选中项：指示线 + 辉光
        if (selectedIndex != NO_TAB) {
            val itemWidth = (items.getOrNull(selectedIndex)?.width ?: 0).toFloat()
            val lineWidth = (itemWidth * 0.42f).coerceIn(dp(28f), dp(84f))
            val lineTop = dp(1.5f)
            // 顶部指示线
            linePaint.color = if (selectedIndex == aiTabIndex) AI_BLUE else colorLine
            canvas.drawRect(
                indicatorCenterX - lineWidth / 2f, lineTop,
                indicatorCenterX + lineWidth / 2f, lineTop + dp(2f),
                linePaint
            )
            // 图标背后的一团柔光晕（不再是带边的光块）
            val selectionGlow = if (selectedIndex == aiTabIndex) fishGlowShader else glowShader
            selectionGlow?.let { shader ->
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

    /**
     * 手指位置对应的 AI 配色混合量。
     *
     * 大肥鱼格正中心 = 1，离得越远越低，**一格半**宽以外才归零，
     * 并且用 smoothstep 把两头"抹平"，所以从首页慢慢滑过去是一路渐变过去的，
     * 不会在格子边界上"啪"地跳成蓝色。
     */
    private fun aiMixAt(x: Float): Float {
        if (aiTabIndex !in items.indices) return 0f
        val cell = items.firstOrNull()?.width?.toFloat() ?: return 0f
        if (cell <= 0f) return 0f
        val center = centerOf(aiTabIndex)
        if (center <= 0f) return 0f
        val t = (1f - kotlin.math.abs(x - center) / (cell * 1.5f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun drawTouchGlow(canvas: Canvas, h: Float, shader: RadialGradient?, alpha: Int) {
        if (shader == null || alpha <= 0) return
        paint.shader = shader
        paint.alpha = alpha.coerceIn(0, 255)
        canvas.save()
        canvas.translate(touchX, h * 0.5f)
        canvas.drawCircle(0f, 0f, touchRadius, paint)
        canvas.restore()
        paint.alpha = 255
        paint.shader = null
    }

    /**
     * 手指辉光的着色器：颜色 = 主题强调色 与 大肥鱼蓝紫 **按 [mix] 混合**。
     *
     * 以前是两个着色器各画一遍互相淡出，中间那段会叠亮、看起来还是"跳"；
     * 现在直接把颜色混出来只画一次，颜色随手指连续变化。
     * 混合量量化到 1/64，避免每帧都重建着色器。
     */
    private fun touchShader(mix: Float): RadialGradient {
        val quantized = (mix * 64f).toInt().coerceIn(0, 64) / 64f
        val primary = colorEvaluator.evaluate(quantized, colorLine, AI_BLUE) as Int
        val secondary = colorEvaluator.evaluate(quantized, colorLine, AI_PURPLE) as Int
        val key = primary * 31 + secondary
        val cached = touchShaderCache
        if (cached != null && touchShaderKey == key) return cached
        val shader = RadialGradient(
            0f, 0f, touchRadius,
            intArrayOf(withAlpha(primary, 0x74), withAlpha(secondary, 0x30), 0x00000000),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        touchShaderCache = shader
        touchShaderKey = key
        return shader
    }

    private var touchShaderCache: RadialGradient? = null
    private var touchShaderKey = 0

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

        /**
         * Gemini 近似配色：蓝 / 紫 / 粉。
         *
         * 注意必须带 `0xFF` 不透明通道：早先写成 `0x4285F4` 这种形式，
         * 那在 Int 里 alpha = 0x00，等于**全透明**——所以从「光晕」到「描边」
         * 每一版都画了却完全看不见，也让大肥鱼选中时的指示线是隐形的。
         */
        const val AI_BLUE = 0xFF4285F4.toInt()
        const val AI_PURPLE = 0xFF9B72CB.toInt()
        const val AI_PINK = 0xFFD96570.toInt()

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
