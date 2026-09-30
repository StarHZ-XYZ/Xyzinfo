package com.rjy.xyz.apps.xyzinfo.ui.common

import android.animation.ArgbEvaluator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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

/**
 * 高斯模糊底栏：首页 / 跑分 / 排行 / 设置。
 *
 * 结构很简单，不再搞花活：
 * 1. [GlassBackdrop] 提供底下那层的实时高斯模糊；
 * 2. 叠一层上厚下薄的白色染色 + 一圈细边，让文字在任何壁纸上都看得清；
 * 3. 选中项是一颗白色半透明胶囊指示块，切换时用 easeOutBack 弹一下滑过去；
 * 4. 图标与文字随选中态做颜色过渡和轻微缩放。
 *
 * 布局用 [LinearLayout] 的 weight 等分，避免手算边距导致的错位；
 * 指示块的位置在**每次布局完成后都会重新按实际宽度计算**，
 * 所以旋屏、切页、改开关之后都不会出现「要点两次才对位」。
 */
class GlassBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Tab(val iconRes: Int, val title: String)

    var onTabSelected: ((index: Int, reselected: Boolean) -> Unit)? = null

    private val tabs = mutableListOf<Tab>()
    private val items = mutableListOf<LinearLayout>()
    private val icons = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()

    private val backdrop = GlassBackdrop(this)
    private val barPath = Path()
    private val barRect = RectF()
    private val pillRect = RectF()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val colorIdle = ContextCompat.getColor(context, R.color.text_tertiary)
    private val colorSelected = ContextCompat.getColor(context, R.color.accent)
    private val colorTintTop = ContextCompat.getColor(context, R.color.glass_tint_top)
    private val colorTintBottom = ContextCompat.getColor(context, R.color.glass_tint_bottom)
    private val colorRimTop = ContextCompat.getColor(context, R.color.glass_rim_top)
    private val colorRimBottom = ContextCompat.getColor(context, R.color.glass_rim_bottom)
    private val colorPill = ContextCompat.getColor(context, R.color.glass_bubble)
    private val colorPillRim = ContextCompat.getColor(context, R.color.glass_bubble_rim)

    private var selectedIndex = 0
    private var pillCenterX = 0f
    private var appliedStyles = -1
    private var lastLaidOutWidth = 0

    private val colorEvaluator = ArgbEvaluator()
    private val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private var moveAnimator: ValueAnimator? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.getDimension(R.dimen.glass_bar_height) / 2f
            setColor(ContextCompat.getColor(context, R.color.glass_fill))
        }
        elevation = dp(18f)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setWillNotDraw(false)
        isClickable = true
    }

    // ---------- 对外接口 ----------

    fun bind(tabs: List<Tab>, selected: Int, listener: (index: Int, reselected: Boolean) -> Unit) {
        this.tabs.clear()
        this.tabs.addAll(tabs)
        onTabSelected = listener
        selectedIndex = selected.coerceIn(0, tabs.size - 1)
        buildItems()
    }

    fun attachBackdrop(source: View) = backdrop.attachSource(source)

    fun requestBackdropRefresh(immediate: Boolean = false) = backdrop.requestRefresh(immediate)

    fun setSelectedTab(index: Int, animated: Boolean) {
        if (index !in tabs.indices) return
        val previous = selectedIndex
        selectedIndex = index
        applyItemStyles(previous)
        moveTo(index, animated)
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
                layoutParams = LayoutParams(dp(22f).toInt(), dp(22f).toInt())
            }
            val label = TextView(context).apply {
                text = tab.title
                textSize = 10.5f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(colorIdle)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(3f).toInt()
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

            val scale = if (selected) 1.05f else 0.95f
            val alpha = if (selected) 1f else 0.85f
            item.animate().cancel()
            if (animated) {
                item.animate()
                    .scaleX(scale).scaleY(scale).alpha(alpha)
                    .setDuration(Anim.DURATION_MEDIUM)
                    .setInterpolator(decelerate)
                    .start()
            } else {
                item.scaleX = scale
                item.scaleY = scale
                item.alpha = alpha
            }
        }
        appliedStyles = selectedIndex
    }

    private fun handleClick(index: Int) {
        val reselected = index == selectedIndex
        if (reselected) {
            pulse()
        } else {
            val previous = selectedIndex
            selectedIndex = index
            applyItemStyles(previous)
            moveTo(index, Anim.enabled(context))
        }
        onTabSelected?.invoke(index, reselected)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            backdrop.requestRefresh()
        }
        return false
    }

    /** 再次点当前标签：指示块弹一下。 */
    private fun pulse() {
        if (!Anim.enabled(context)) return
        moveTo(selectedIndex, animated = true)
    }

    private fun moveTo(index: Int, animated: Boolean) {
        val target = centerOf(index)
        if (target <= 0f) {
            // 还没完成布局，等布局好了 onLayout 会自己纠正
            post { moveTo(index, animated) }
            return
        }
        moveAnimator?.cancel()
        val startX = if (pillCenterX <= 0f) target else pillCenterX
        if (!animated || !Anim.enabled(context)) {
            pillCenterX = target
            invalidate()
            return
        }
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 440L
            addUpdateListener {
                pillCenterX = startX + (target - startX) * easeOutBack(it.animatedValue as Float)
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
        (background as? GradientDrawable)?.cornerRadius = h / 2f
        backdrop.requestRefresh(immediate = true)
    }

    /**
     * 每次布局完成后都按真实宽度重算指示块位置。
     *
     * 之前只在首次布局算一次，之后宽度变化（换页面、旋屏、改设置后重建）就不再纠正，
     * 于是出现「要点两次按钮才归位」。
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val widthNow = r - l
        val widthChanged = widthNow != lastLaidOutWidth
        lastLaidOutWidth = widthNow
        val animating = moveAnimator?.isRunning == true
        if (!animating && (changed || widthChanged || pillCenterX <= 0f)) {
            val target = centerOf(selectedIndex)
            if (target > 0f && target != pillCenterX) {
                pillCenterX = target
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
        val capsule = h / 2f
        barRect.set(0f, 0f, w, h)
        barPath.reset()
        barPath.addRoundRect(barRect, capsule, capsule, Path.Direction.CW)

        // 1) 底下那层的高斯模糊
        backdrop.draw(canvas, barRect, capsule)

        // 2) 白色染色（上厚下薄）
        paint.shader = LinearGradient(
            0f, 0f, 0f, h, colorTintTop, colorTintBottom, Shader.TileMode.CLAMP
        )
        canvas.drawPath(barPath, paint)
        paint.shader = null

        // 3) 指示块
        val itemWidth = items.getOrNull(selectedIndex)?.width ?: 0
        val pillHalf = (min(itemWidth * 0.62f, dp(86f)) / 2f).coerceAtLeast(dp(24f))
        val pillHeight = min(h - dp(16f), dp(42f))
        val top = (h - pillHeight) / 2f
        pillRect.set(pillCenterX - pillHalf, top, pillCenterX + pillHalf, top + pillHeight)

        pillPaint.shader = LinearGradient(
            0f, top, 0f, top + pillHeight,
            withAlpha(colorPillRim, 0x4D),
            withAlpha(colorPill, 0x33),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)
        pillPaint.shader = null
        pillPaint.color = colorPill
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)

        pillRimPaint.shader = LinearGradient(
            0f, top, 0f, top + pillHeight,
            colorPillRim, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillRimPaint)
        pillRimPaint.shader = null

        // 4) 外圈细边
        borderPaint.shader = LinearGradient(
            0f, 0f, 0f, h, colorRimTop, colorRimBottom, Shader.TileMode.CLAMP
        )
        val checkpoint = canvas.save()
        canvas.clipPath(barPath)
        canvas.drawRoundRect(
            dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f), capsule, capsule, borderPaint
        )
        canvas.restoreToCount(checkpoint)
        borderPaint.shader = null
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.20f
        val c3 = c1 + 1f
        val inv = t - 1f
        return 1f + c3 * inv * inv * inv + c1 * inv * inv
    }
}
