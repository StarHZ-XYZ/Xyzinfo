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
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 液态玻璃底栏。
 *
 * 视觉上不是一个「半透明色块」，而是照着系统级玻璃材质分七层叠出来的：
 *
 * 1. **真实背景模糊**：见 [GlassBackdrop]，把底栏背后的内容降采样后模糊再贴回来，
 *    所以滑动列表时能看到文字和卡片在玻璃下面糊着移动；
 * 2. **玻璃染色**：偏白的竖向渐变压在模糊层上，越靠上越亮（模拟厚玻璃）；
 * 3. **磨砂颗粒**：72×72 固定种子噪声，消掉纯色的塑料感；
 * 4. **边缘折射**：模糊层整体放大 8%，靠边的内容被「折」出去一点，形成透镜感；
 * 5. **镜面棱线**：外圈 1.4dp 渐变描边（上亮下暗）+ 顶部一条高光弧 + 底部内阴影；
 * 6. **液态气泡**：选中态是一颗会挤压缩放、后面拖着一小团残影的玻璃泡，
 *    停下来会回弹（easeOutBack + 途中拉伸），而不是生硬地平移一个矩形；
 * 7. **按压镜面高光**：手指按上去的位置有一团跟随的柔光。
 */
class GlassBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class Tab(val iconRes: Int, val title: String)

    /** 选中回调：index 为目标标签，reselected 表示点的就是当前页。 */
    var onTabSelected: ((index: Int, reselected: Boolean) -> Unit)? = null

    private val tabs = mutableListOf<Tab>()
    private val items = mutableListOf<LinearLayout>()
    private val icons = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()

    private val backdrop = GlassBackdrop(this)
    private val barPath = Path()
    private val barRect = RectF()
    private val bubbleRect = RectF()

    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val colorIdle = ContextCompat.getColor(context, R.color.text_tertiary)
    private val colorSelected = ContextCompat.getColor(context, R.color.accent)
    private val colorTintTop = ContextCompat.getColor(context, R.color.glass_tint_top)
    private val colorTintBottom = ContextCompat.getColor(context, R.color.glass_tint_bottom)
    private val colorRimTop = ContextCompat.getColor(context, R.color.glass_rim_top)
    private val colorRimBottom = ContextCompat.getColor(context, R.color.glass_rim_bottom)
    private val colorNoise = ContextCompat.getColor(context, R.color.glass_noise)
    private val colorSpecular = ContextCompat.getColor(context, R.color.glass_specular)
    private val colorBubble = ContextCompat.getColor(context, R.color.glass_bubble)
    private val colorBubbleRim = ContextCompat.getColor(context, R.color.glass_bubble_rim)

    private var selectedIndex = 0
    private var pillCenterX = 0f
    private var trailCenterX = 0f
    /** 0~1：搬运途中被「挤」出去的程度，停下就归零。 */
    private var squash = 0f
    private var specularX = 0.5f
    private var specularAlpha = 0f
    private var laid = false
    private var appliedStyles = -1

    private val colorEvaluator = ArgbEvaluator()
    private val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private var moveAnimator: ValueAnimator? = null

    init {
        // 背景只负责轮廓（elevation 阴影靠它），真正的玻璃层次都在 onDraw 里
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

    // ---------- 对外 ----------

    fun bind(tabs: List<Tab>, selected: Int, listener: (index: Int, reselected: Boolean) -> Unit) {
        this.tabs.clear()
        this.tabs.addAll(tabs)
        onTabSelected = listener
        selectedIndex = selected.coerceIn(0, tabs.size - 1)
        buildItems()
    }

    /** 由页面骨架注入「背后要取样的内容视图」，玻璃才开始真的模糊。 */
    fun attachBackdrop(source: View) {
        backdrop.attachSource(source)
    }

    fun requestBackdropRefresh(immediate: Boolean = false) {
        backdrop.requestRefresh(immediate)
    }

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
                setPadding(0, dp(10f).toInt(), 0, 0)
                addView(icon)
                addView(label)
                setOnClickListener { handleClick(index) }
                setOnTouchListener { _, event -> handleTouch(event) }
            }
            addView(item, LayoutParams(0, LayoutParams.MATCH_PARENT))
            items += item
            icons += icon
            labels += label
        }
        applyItemLayout()
        appliedStyles = -1
        applyItemStyles(selectedIndex)
    }

    /** FrameLayout 没有 weight：按「总宽 / 标签数」显式定宽，再用 leftMargin 排开。 */
    private fun applyItemLayout() {
        val count = items.size
        if (count == 0) return
        val total = width
        if (total <= 0) {
            post { applyItemLayout() }
            return
        }
        val itemWidth = total / count
        items.forEachIndexed { index, item ->
            val params = (item.layoutParams as? LayoutParams) ?: LayoutParams(0, LayoutParams.MATCH_PARENT)
            params.width = itemWidth
            params.height = LayoutParams.MATCH_PARENT
            params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            params.leftMargin = index * itemWidth
            item.layoutParams = params
        }
        val target = centerOf(selectedIndex)
        if (target > 0f) {
            pillCenterX = target
            trailCenterX = target
        }
        requestLayout()
        invalidate()
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

            item.animate().cancel()
            val scale = if (selected) 1.05f else 0.94f
            val alpha = if (selected) 1f else 0.85f
            val lift = if (selected) -dp(1f) else 0f
            if (animated) {
                item.animate()
                    .scaleX(scale).scaleY(scale).alpha(alpha).translationY(lift)
                    .setDuration(Anim.DURATION_MEDIUM)
                    .setInterpolator(decelerate)
                    .start()
            } else {
                item.scaleX = scale
                item.scaleY = scale
                item.alpha = alpha
                item.translationY = lift
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (width > 0) specularX = (event.x / width).coerceIn(0f, 1f)
                specularAlpha = if (event.actionMasked == MotionEvent.ACTION_DOWN) 0.85f else 0.5f
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                specularAlpha = 0f
                invalidate()
            }
        }
        return false
    }

    /** 再次点当前标签：气泡弹一下。 */
    private fun pulse() {
        if (!Anim.enabled(context)) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 460L
            addUpdateListener {
                val t = it.animatedValue as Float
                squash = sin(t * Math.PI).toFloat() * 0.7f
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    squash = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun moveTo(index: Int, animated: Boolean) {
        val target = centerOf(index)
        if (target <= 0f) {
            post { moveTo(index, animated) }
            return
        }
        moveAnimator?.cancel()
        val startX = if (pillCenterX == 0f) target else pillCenterX
        val startTrail = if (trailCenterX == 0f) target else trailCenterX

        if (!animated || !laid) {
            pillCenterX = target
            trailCenterX = target
            squash = 0f
            invalidate()
            return
        }

        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            addUpdateListener {
                val t = it.animatedValue as Float
                pillCenterX = startX + (target - startX) * easeOutBack(t)
                trailCenterX = startTrail + (target - startTrail) * easeOutCubic(t)
                // 途中被挤出去，落地时收回来——这就是「液态」的来源
                squash = sin(t * Math.PI).toFloat()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    squash = 0f
                    invalidate()
                }
            })
            start()
        }
        backdrop.requestRefresh()
    }

    private fun centerOf(index: Int): Float {
        val item = items.getOrNull(index) ?: return 0f
        if (item.right == 0 && item.left == 0) return 0f
        return (item.left + item.right) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        laid = true
        (background as? GradientDrawable)?.cornerRadius = h / 2f
        applyItemLayout()
        backdrop.requestRefresh(immediate = true)
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

        val capsuleRadius = h / 2f
        barRect.set(0f, 0f, w, h)
        barPath.reset()
        barPath.addRoundRect(barRect, capsuleRadius, capsuleRadius, Path.Direction.CW)

        // 1) 背后内容的实时模糊（玻璃的灵魂，没有这层就只是半透明色块）
        backdrop.draw(canvas, barRect, capsuleRadius, zoom = 1.08f)

        // 2) 玻璃染色：上厚下薄
        tintPaint.shader = LinearGradient(
            0f, 0f, 0f, h, colorTintTop, colorTintBottom, Shader.TileMode.CLAMP
        )
        canvas.drawPath(barPath, tintPaint)
        tintPaint.shader = null

        // 3) 磨砂颗粒
        noisePaint.shader = BitmapShader(noiseTexture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        canvas.drawPath(barPath, noisePaint)
        noisePaint.shader = null

        // 4) 液态气泡：残影 → 阴影 → 气泡本体 → 气泡的棱线
        drawBubble(canvas, h, capsuleRadius)

        // 5) 按压位置的镜面高光
        if (specularAlpha > 0.01f) {
            specularPaint.shader = RadialGradient(
                specularX * w, h * 0.5f, w * 0.5f,
                withAlpha(colorSpecular, (specularAlpha * 150).toInt()),
                0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(barPath, specularPaint)
            specularPaint.shader = null
        }

        // 6) 棱线：上亮下暗的外圈 + 顶部高光弧
        rimPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            colorRimTop,
            colorRimBottom,
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipPath(barPath)
        canvas.drawRoundRect(
            dp(0.7f), dp(0.7f), w - dp(0.7f), h - dp(0.7f),
            capsuleRadius, capsuleRadius, rimPaint
        )
        canvas.restore()
        rimPaint.shader = null

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        highlightPaint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(0x00FFFFFF, withAlpha(colorRimTop, 0xE6), withAlpha(colorRimTop, 0xE6), 0x00FFFFFF),
            floatArrayOf(0.02f, 0.25f, 0.75f, 0.98f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            dp(2f), dp(1.2f), w - dp(2f), dp(2.6f),
            dp(0.7f), dp(0.7f), highlightPaint
        )
    }

    private fun drawBubble(canvas: Canvas, height: Float, capsuleRadius: Float) {
        val baseHalfWidth = bubbleHalfWidth()
        val bubbleHeight = min(height - dp(14f), dp(42f))
        val top = (height - bubbleHeight) / 2f
        val bottom = top + bubbleHeight
        val bubbleRadius = bubbleHeight / 2f

        // 拉伸：横向撑开、纵向压扁，形成流体挤压感
        val stretchHalf = baseHalfWidth * (1f + 0.16f * squash)
        val squeeze = 1f - 0.10f * squash
        val stretchTop = top + (bubbleHeight - bubbleHeight * squeeze) / 2f
        val stretchBottom = stretchTop + bubbleHeight * squeeze

        // 残影：慢半拍跟上来的一小团，制造液体拖尾
        if (abs(pillCenterX - trailCenterX) > dp(1.5f)) {
            bubbleShadowPaint.color = withAlpha(colorBubble, 0x33)
            canvas.drawRoundRect(
                trailCenterX - baseHalfWidth * 0.88f,
                top + dp(3f),
                trailCenterX + baseHalfWidth * 0.88f,
                bottom - dp(3f),
                bubbleRadius, bubbleRadius, bubbleShadowPaint
            )
        }

        // 气泡下方的柔和投影，让它「浮」在玻璃上
        bubbleShadowPaint.shader = RadialGradient(
            pillCenterX, bottom + dp(1f), baseHalfWidth * 1.25f,
            0x33000000, 0x00000000, Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            pillCenterX - baseHalfWidth * 1.25f,
            bottom - dp(1f),
            pillCenterX + baseHalfWidth * 1.25f,
            bottom + dp(7f),
            bubbleShadowPaint
        )
        bubbleShadowPaint.shader = null

        bubbleRect.set(
            pillCenterX - stretchHalf,
            stretchTop,
            pillCenterX + stretchHalf,
            stretchBottom
        )
        bubblePaint.color = colorBubble
        canvas.drawRoundRect(
            bubbleRect,
            bubbleRadius,
            bubbleRadius,
            bubblePaint
        )

        // 气泡自己的棱线：上亮下透，像一颗凸起的玻璃珠
        bubbleRimPaint.shader = LinearGradient(
            0f, stretchTop, 0f, stretchBottom,
            colorBubbleRim,
            0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bubbleRect, bubbleRadius, bubbleRadius, bubbleRimPaint)
        bubbleRimPaint.shader = null

        // 气泡顶部的窄高光
        bubbleRimPaint.shader = null
        bubblePaint.color = withAlpha(colorBubbleRim, 0x59)
        canvas.drawRoundRect(
            pillCenterX - stretchHalf * 0.62f,
            stretchTop + dp(2.2f),
            pillCenterX + stretchHalf * 0.62f,
            stretchTop + dp(3.8f),
            dp(0.8f), dp(0.8f), bubblePaint
        )
    }

    private fun bubbleHalfWidth(): Float {
        val itemWidth = items.getOrNull(selectedIndex)?.let { it.right - it.left } ?: 0
        val wanted = if (itemWidth > 0) itemWidth * 0.66f else dp(150f)
        return min(wanted, dp(88f)) / 2f
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t
        return 1f - inv * inv * inv
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.32f
        val c3 = c1 + 1f
        val inv = t - 1f
        return 1f + c3 * inv * inv * inv + c1 * inv * inv
    }

    private companion object {
        /** 磨砂颗粒：固定种子，保证每次启动纹理一致。 */
        val noiseTexture: Bitmap by lazy {
            val size = 72
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val random = java.util.Random(20260930L)
            val pixels = IntArray(size * size)
            for (index in pixels.indices) {
                pixels[index] = (random.nextInt(20) shl 24) or 0x00FFFFFF
            }
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            bitmap
        }
    }
}
