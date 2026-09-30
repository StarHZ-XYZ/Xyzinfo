package com.rjy.xyz.apps.xyzinfo.ui.common

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rjy.xyz.apps.xyzinfo.MainActivity
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.BenchmarkActivity
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.RankingActivity
import com.rjy.xyz.apps.xyzinfo.ui.settings.SettingsActivity

/**
 * 页面骨架：把 Activity 原本的内容包一层 FrameLayout，底部浮一个液态玻璃底栏。
 *
 * 这样做的好处是所有页面都不用改布局 XML：Activity 照旧 inflate 自己的 binding，
 * 骨架在运行时把它塞进容器，再补上底栏与内容底部预留空间。
 */
object GlassScaffold {

    const val TAB_HOME = 0
    const val TAB_BENCHMARK = 1
    const val TAB_RANKING = 2
    const val TAB_SETTINGS = 3

    fun tabs(): List<GlassBottomBar.Tab> = listOf(
        GlassBottomBar.Tab(R.drawable.ic_nav_home, "首页"),
        GlassBottomBar.Tab(R.drawable.ic_nav_benchmark, "跑分"),
        GlassBottomBar.Tab(R.drawable.ic_nav_ranking, "排行"),
        GlassBottomBar.Tab(R.drawable.ic_nav_settings, "设置")
    )

    private fun activityFor(tab: Int): Class<out Activity> = when (tab) {
        TAB_BENCHMARK -> BenchmarkActivity::class.java
        TAB_RANKING -> RankingActivity::class.java
        TAB_SETTINGS -> SettingsActivity::class.java
        else -> MainActivity::class.java
    }

    /**
     * 在 [content]（Activity 原本的根布局）外面套上底栏骨架。
     *
     * @param currentTab 当前页面在底栏中的位置，用于高亮。
     * @return 底栏实例；设置里关掉底栏时返回 null，此时不改动布局。
     */
    fun attach(activity: AppCompatActivity, content: View, currentTab: Int): GlassBottomBar? {
        if (!SettingsRepository.glassBottomBarEnabled(activity)) return null

        val barHeight = activity.resources.getDimension(R.dimen.glass_bar_height).toInt()
        val sideMargin = activity.resources.getDimension(R.dimen.glass_bar_side_margin).toInt()
        val baseBottomMargin = activity.resources.getDimension(R.dimen.glass_bar_bottom_margin).toInt()
        val reserved = activity.resources.getDimension(R.dimen.glass_bar_reserved_space).toInt()

        val container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundResource(R.drawable.bg_main_gradient)
        }
        // Activity 已经 setContentView 过一次，root 现在挂在 decor 的 content 上，
        // 必须先摘下来才能塞进新容器，否则会抛「child already has a parent」。
        (content.parent as? ViewGroup)?.removeView(content)
        container.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val bar = GlassBottomBar(activity)
        val barParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            barHeight
        ).apply {
            gravity = Gravity.BOTTOM
            marginStart = sideMargin
            marginEnd = sideMargin
            bottomMargin = baseBottomMargin
        }
        container.addView(bar, barParams)

        // 底栏要浮在系统手势条上方：把导航栏高度补进底边距
        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val params = view.layoutParams as FrameLayout.LayoutParams
            val target = baseBottomMargin + navBottom
            if (params.bottomMargin != target) {
                params.bottomMargin = target
                view.layoutParams = params
            }
            insets
        }

        // 给内容留出底栏高度，避免最后一段内容被玻璃挡住
        reserveBottomSpace(content, reserved)

        // 玻璃要真的糊东西：把内容视图交给底栏当取样源
        bar.attachBackdrop(content)
        (content as? android.widget.ScrollView)?.setOnScrollChangeListener { _, _, _, _, _ ->
            bar.requestBackdropRefresh()
        }
        content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            bar.requestBackdropRefresh(immediate = true)
        }
        // 页面数据是异步加载的，起来之后再补两次快照，避免玻璃里一直是空背景
        bar.postDelayed({ bar.requestBackdropRefresh(immediate = true) }, 320L)
        bar.postDelayed({ bar.requestBackdropRefresh(immediate = true) }, 1000L)

        bar.bind(tabs(), currentTab) { index, reselected ->
            if (index == currentTab) {
                if (reselected) content.smoothScrollToTop()
            } else {
                activity.startActivity(
                    Intent(activity, activityFor(index))
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            }
        }
        // 底栏创建后每次都要重算一遍（布局完成前拿不到 item 位置）
        bar.post { bar.setSelectedTab(currentTab, animated = false) }

        activity.setContentView(container)
        ViewCompat.requestApplyInsets(container)
        return bar
    }

    /**
     * 给滚动容器内部的第一个子布局加底部内边距。
     *
     * 不能直接改根布局的 padding：Activity 已经用它做过系统栏内边距，
     * 两者的 insets 回调会互相覆盖；加在内容子布局上互不干扰。
     */
    private fun reserveBottomSpace(content: View, reservedPx: Int) {
        if (content is ViewGroup) {
            // 让内容可以滚到玻璃底栏下面，透出底栏的磨砂感
            content.clipToPadding = false
        }
        val target = (content as? ViewGroup)?.takeIf { it.childCount > 0 }?.getChildAt(0) as? View
        if (target is ViewGroup) {
            target.setPadding(
                target.paddingLeft,
                target.paddingTop,
                target.paddingRight,
                target.paddingBottom + reservedPx
            )
        } else {
            content.setPadding(
                content.paddingLeft,
                content.paddingTop,
                content.paddingRight,
                content.paddingBottom + reservedPx
            )
        }
    }
}
