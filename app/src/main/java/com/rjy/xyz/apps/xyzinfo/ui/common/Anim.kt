package com.rjy.xyz.apps.xyzinfo.ui.common

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import kotlin.math.roundToInt
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

/**
 * 全局动效工具。
 *
 * 统一在这里定义时长 / 曲线，页面只调用语义化方法；设置里的「丝滑动画」关闭后，
 * 所有方法都会直接把控件置为终态，不做任何插值。
 */
object Anim {

    /** 标准曲线：快速起步、柔和收尾（与 res/interpolator 中的定义一致）。 */
    private val DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)

    const val DURATION_FAST = 150L
    const val DURATION_SHORT = 220L
    const val DURATION_MEDIUM = 320L
    const val DURATION_LONG = 460L

    fun enabled(context: Context): Boolean = SettingsRepository.animationsEnabled(context)

    private fun dp(view: View, value: Float): Float = value * view.resources.displayMetrics.density

    /**
     * 一组控件依次入场：淡入 + 从下方轻微上浮。
     *
     * @param step 相邻控件的间隔，越小越「齐刷」，越大越有明显顺序。
     */
    fun staggerIn(
        views: List<View>,
        startDelay: Long = 0L,
        step: Long = 55L,
        travelDp: Float = 16f,
        duration: Long = DURATION_LONG
    ) {
        if (views.isEmpty()) return
        if (!enabled(views.first().context)) {
            views.forEach {
                it.alpha = 1f
                it.translationY = 0f
            }
            return
        }
        views.forEachIndexed { index, view ->
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = dp(view, travelDp)
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(startDelay + index * step)
                .setDuration(duration)
                .setInterpolator(DECELERATE)
                .start()
        }
    }

    /** 列表 / 分组容器里的所有子控件依次入场。 */
    fun revealChildren(container: ViewGroup, step: Long = 45L, startDelay: Long = 0L) {
        val children = (0 until container.childCount).map { container.getChildAt(it) }
        staggerIn(children, startDelay = startDelay, step = step)
    }

    /**
     * 卡片按压反馈：Material 卡片挂水波纹，普通控件挂前景水波纹，同时轻微缩放。
     *
     * 只做视觉效果，返回 false 不拦截点击，原有 OnClickListener 照常工作。
     */
    fun pressFeedback(views: Collection<View>) {
        views.forEach { view ->
            val context = view.context
            val rippleColor = ThemeColors.accent(context)
            if (view is com.google.android.material.card.MaterialCardView) {
                view.rippleColor = ColorStateList.valueOf(
                    (rippleColor and 0x00FFFFFF) or 0x1F000000
                )
            } else {
                view.foreground = RippleDrawable(
                    ColorStateList.valueOf((rippleColor and 0x00FFFFFF) or 0x1F000000),
                    null,
                    null
                )
            }
            view.isClickable = true
            view.setOnTouchListener { v, event -> applyPressScale(v, event) }
        }
    }

    fun pressFeedback(vararg views: View) = pressFeedback(views.toList())

    private fun applyPressScale(view: View, event: MotionEvent): Boolean {
        if (!enabled(view.context)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.animate()
                .scaleX(0.972f)
                .scaleY(0.972f)
                .setDuration(DURATION_FAST)
                .setInterpolator(STANDARD)
                .start()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(DURATION_SHORT)
                    .setInterpolator(DECELERATE)
                    .start()
        }
        return false
    }

}

/**
 * 下面这些是「控件扩展」，做成顶层函数而不是 Anim 的成员，
 * 这样同一个包里的页面不用写额外 import 就能直接用。
 */

private val ANIM_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

/** 数字滚动（简化入口：前缀 + 数值 + 后缀）。 */
fun TextView.countUpTo(
    target: Int,
    prefix: String = "",
    suffix: String = "",
    duration: Long = 780L
) {
    if (!Anim.enabled(context) || target == 0) {
        setInfoRow("$prefix$target$suffix")
        return
    }
    ValueAnimator.ofFloat(0f, 1f).apply {
        this.duration = duration
        interpolator = ANIM_DECELERATE
        addUpdateListener {
            val value = (target * it.animatedValue as Float).roundToInt()
            setInfoRow("$prefix$value$suffix")
        }
        start()
    }
}

/** 数字滚动：回调里拿到当前值，方便拼接多段富文本。 */
fun TextView.countUpWith(
    target: Int,
    duration: Long = 780L,
    render: (Int) -> String
) {
    if (!Anim.enabled(context)) {
        setInfoRow(render(target))
        return
    }
    ValueAnimator.ofFloat(0f, 1f).apply {
        this.duration = duration
        interpolator = ANIM_DECELERATE
        addUpdateListener {
            val value = (target * it.animatedValue as Float).roundToInt()
            setInfoRow(render(value))
        }
        start()
    }
}

/** 进度条平滑推进到 [percent]（0~100）。 */
fun ProgressBar.animateTo(percent: Int, duration: Long = Anim.DURATION_MEDIUM) {
    val target = percent.coerceIn(0, 100)
    if (!Anim.enabled(context)) {
        progress = target
        return
    }
    ValueAnimator.ofInt(this.progress, target).apply {
        this.duration = duration
        interpolator = ANIM_DECELERATE
        addUpdateListener { progress = it.animatedValue as Int }
        start()
    }
}

/** 条形图生长：控件本身是「满值」宽度，这里只做 scaleX。 */
fun View.growBar(fraction: Float, duration: Long = 520L, delay: Long = 0L) {
    val target = fraction.coerceIn(0f, 1f)
    pivotX = 0f
    if (!Anim.enabled(context)) {
        scaleX = target
        return
    }
    scaleX = 0f
    animate()
        .scaleX(target)
        .setStartDelay(delay)
        .setDuration(duration)
        .setInterpolator(ANIM_DECELERATE)
        .start()
}

/** 颜色平滑过渡（用于文字 / 图标选中态）。 */
fun TextView.animateTextColor(from: Int, to: Int, duration: Long = Anim.DURATION_MEDIUM) {
    if (!Anim.enabled(context)) {
        setTextColor(to)
        return
    }
    ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
        this.duration = duration
        addUpdateListener { setTextColor(it.animatedValue as Int) }
        start()
    }
}

/** 把一个可滚动页面平滑回到顶部（点击当前底栏标签时使用）。 */
fun View.smoothScrollToTop() {
    if (this is ScrollView) smoothScrollTo(0, 0)
}
