package com.rjy.xyz.apps.xyzinfo.ui.common

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.rjy.xyz.apps.xyzinfo.MainActivity
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.HolidayCatalog
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
    /** 大肥鱼（AI）：放在底栏正中间，一眼就能认出这是 AI 入口。 */
    const val TAB_FISH = 2
    const val TAB_RANKING = 3
    const val TAB_SETTINGS = 4

    /** 子页面（芯片 / 内存 / 屏幕 / 电池 / 传感器 / 系统 / 通信 / GPS）：不属于任何标签。 */
    const val TAB_NONE = -1

    fun tabs(): List<GlassBottomBar.Tab> = listOf(
        GlassBottomBar.Tab(R.drawable.ic_nav_home, "首页"),
        GlassBottomBar.Tab(R.drawable.ic_nav_benchmark, "跑分"),
        // 大肥鱼图标 = 用户桌面那张图自动矢量化后的 VectorDrawable（ic_deepseek_fish.xml）
        GlassBottomBar.Tab(R.drawable.ic_deepseek_fish, "大肥鱼"),
        GlassBottomBar.Tab(R.drawable.ic_nav_ranking, "排行"),
        GlassBottomBar.Tab(R.drawable.ic_nav_settings, "设置")
    )

    private fun activityFor(tab: Int): Class<out Activity> = when (tab) {
        TAB_BENCHMARK -> BenchmarkActivity::class.java
        TAB_RANKING -> RankingActivity::class.java
        TAB_SETTINGS -> SettingsActivity::class.java
        TAB_FISH -> com.rjy.xyz.apps.xyzinfo.ui.fish.FishAiActivity::class.java
        else -> MainActivity::class.java
    }

    /**
     * 在 [content]（Activity 原本的根布局）外面套上底栏骨架。
     *
     * @param currentTab 当前页面在底栏中的位置，用于高亮。
     * @return 底栏实例；设置里关掉底栏时返回 null，此时不改动布局。
     */
    fun attach(activity: AppCompatActivity, content: View, currentTab: Int): GlassBottomBar? {
        // 底栏固定开启：设置里的开关已经去掉。
        // 注意不能再读旧版本存下来的开关值——有些用户之前把它关过，
        // 继续读就会导致「没有开关、底栏也永远不出现」。

        val barHeight = activity.resources.getDimension(R.dimen.glass_bar_height).toInt()
        // 下沉式底栏：左右不留边距，直接贴着屏幕底部（不再是悬浮胶囊）
        val sideMargin = 0
        val baseBottomMargin = 0
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
        // 必应壁纸必须画在内容**下面**：先铺壁纸 + 蒙版，再加内容
        applyWallpaper(activity, container, content)
        container.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val bar = GlassBottomBar(activity)
        bar.docked = true
        // 让「大肥鱼」那一格带上 AI 彩色光晕、并且不吃单色 tint
        bar.aiTabIndex = TAB_FISH
        /*
         * 子页面（芯片 / 内存 / 屏幕 / 电池 / 传感器 / 系统 / 通信 / GPS / 硬件测试 / 杂项）
         * **不显示底栏**：它们是从首页点进去的详情页，底部再飘一条标签栏会让层级认知混乱；
         * 取而代之是左上角一个圆形返回按钮。
         */
        if (currentTab == TAB_NONE) {
            bar.visibility = View.GONE
            val density = activity.resources.displayMetrics.density
            val size = (42 * density).toInt()
            val backButton = android.widget.ImageButton(activity).apply {
                setImageResource(R.drawable.ic_arrow_back)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(activity, R.color.surface))
                    setStroke((1 * density).toInt(), ContextCompat.getColor(activity, R.color.stroke))
                }
                imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.text_primary)
                )
                contentDescription = "返回"
                elevation = 3f * density
                setOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
            }
            Anim.pressFeedback(backButton)
            container.addView(
                backButton,
                FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.TOP or Gravity.START
                    marginStart = (14 * density).toInt()
                    topMargin = (10 * density).toInt()
                }
            )
            ViewCompat.setOnApplyWindowInsetsListener(backButton) { view, insets ->
                val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                (view.layoutParams as FrameLayout.LayoutParams).topMargin =
                    (10 * density).toInt() + top
                insets
            }
            // 给标题让出返回按钮的高度
            val host = (content as? ViewGroup)
                ?.takeIf { it.childCount > 0 }
                ?.getChildAt(0) as? ViewGroup
            host?.setPadding(
                host.paddingLeft,
                host.paddingTop + (46 * density).toInt(),
                host.paddingRight,
                host.paddingBottom
            )
        }
        /*
         * 氛围层：铺在内容之上、底栏之下；粒子数很少，掉帧风险低，可随时关掉。
         *
         * 1.0.4 起，**节日当天换成节日彩蛋**（灯笼 / 爱心 / 月饼 / 礼物 …），
         * 否则还是原来的四季氛围 —— 两套一起飘会显得很乱，所以是二选一。
         */
        val animationsOn = SettingsRepository.animationsEnabled(activity)
        /*
         * 「飘落特效」是**总开关**：四季氛围和节日彩蛋都由它一键管理。
         * 节日彩蛋还有个自己的开关（可以只要四季氛围、不要节日特效，反之亦然）。
         */
        val fallingOn = animationsOn && SettingsRepository.seasonEffectEnabled(activity)
        val holiday = if (animationsOn && SettingsRepository.holidayEffectEnabled(activity)) {
            HolidayCatalog.today()
        } else {
            null
        }
        val festiveFalling = if (fallingOn) holiday else null
        if (festiveFalling != null) {
            val festive = HolidayOverlay(activity).apply { this.holiday = festiveFalling }
            container.addView(
                festive,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.addObserver(
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> festive.start()
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> festive.stop()
                        else -> Unit
                    }
                }
            )
        } else if (fallingOn) {
            val season = SeasonOverlay(activity).apply { this.season = Season.resolve(activity) }
            container.addView(
                season,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.addObserver(
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> season.start()
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> season.stop()
                        else -> Unit
                    }
                }
            )
        }
        val barParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            barHeight
        ).apply {
            gravity = Gravity.BOTTOM
            marginStart = sideMargin
            marginEnd = sideMargin
            bottomMargin = baseBottomMargin
        }

        /*
         * 大肥鱼主题：在每个页面的角落摆一条大肥鱼。
         *
         * 用户要求得很明确：**不改任何配色**，只是放一条可爱的大肥鱼。
         * 所以这里只加一个 ImageView：放在底栏上方或右下角、半透明、不挡内容。
         *
         * 1.0.7 起这条鱼**可以点了**：点一下它会甩一下尾巴（绕着鱼身快速来回摆几次再停下），
         * 顺手给个轻微的回弹缩放。日常那种很轻的浮动 + 摆尾还在，点击只是插播一段动作。
         */
        if (SettingsRepository.deepSeekTheme(activity)) {
            val fishDensity = activity.resources.displayMetrics.density
            val fishWidth = (56 * fishDensity).toInt()
            val fishHeight = (fishWidth * 349f / 474f).toInt() // 和矢量图标同比例
            val fish = android.widget.ImageView(activity).apply {
                setImageResource(R.drawable.ic_deepseek_fish)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                alpha = 0.92f
                isClickable = true
                isFocusable = true
                contentDescription = "大肥鱼：点一下它会摆摆尾巴"
                layoutParams = FrameLayout.LayoutParams(fishWidth, fishHeight).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = (12 * fishDensity).toInt()
                    // 有底栏的页面抬到玻璃上方；子页面（没有底栏）就贴着右下角
                    bottomMargin = if (currentTab == TAB_NONE) {
                        (18 * fishDensity).toInt()
                    } else {
                        reserved + (6 * fishDensity).toInt()
                    }
                }
            }
            container.addView(fish)

            if (SettingsRepository.animationsEnabled(activity)) {
                val bob = android.animation.ObjectAnimator.ofFloat(
                    fish, "translationY", 0f, -5f * fishDensity
                ).apply {
                    duration = 1700L
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                val sway = android.animation.ObjectAnimator.ofFloat(
                    fish, "rotation", -3.5f, 3.5f
                ).apply {
                    duration = 2400L
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    // 以"尾巴"那一侧为轴轻轻摆，看起来像在游
                    startDelay = 300L
                }
                (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.addObserver(
                    androidx.lifecycle.LifecycleEventObserver { _, event ->
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                bob.start()
                                sway.start()
                            }

                            androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                bob.cancel()
                                sway.cancel()
                                fish.translationY = 0f
                                fish.rotation = 0f
                            }

                            else -> Unit
                        }
                    }
                )

                /*
                 * 点一下：先停掉日常的小浮动，甩一段尾巴（绕鱼身快速来回摆几次），
                 * 顺手来一个轻微的回弹缩放；结束后再让日常动作接着跑。
                 */
                fish.setOnClickListener { view ->
                    Anim.pressFeedback(view)
                    bob.cancel()
                    sway.cancel()
                    val wag = android.animation.ObjectAnimator.ofFloat(
                        fish, "rotation", 0f, 16f, -13f, 9f, -5f, 2f, 0f
                    ).apply {
                        duration = 760L
                        interpolator = android.view.animation.DecelerateInterpolator()
                    }
                    val hop = android.animation.ObjectAnimator.ofFloat(
                        fish, "translationY", 0f, -7f * fishDensity, 0f
                    ).apply {
                        duration = 460L
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    }
                    val popX = android.animation.ObjectAnimator.ofFloat(fish, "scaleX", 1f, 1.12f, 1f)
                    val popY = android.animation.ObjectAnimator.ofFloat(fish, "scaleY", 1f, 1.12f, 1f)
                    android.animation.AnimatorSet().apply {
                        playTogether(wag, hop, popX, popY)
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                fish.rotation = 0f
                                fish.translationY = 0f
                                // 页面还在前台就让日常动作继续；不在就保持静止
                                val resumed = (activity as? androidx.lifecycle.LifecycleOwner)
                                    ?.lifecycle?.currentState
                                    ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
                                if (resumed) {
                                    bob.start()
                                    sway.start()
                                }
                            }
                        })
                        start()
                    }
                }
            } else {
                // 动画总开关关掉时只给按压反馈，不做动作
                fish.setOnClickListener { Anim.pressFeedback(it) }
            }
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
        bar.autoAnimateOnSelect = false
        // 滚动时按 110ms 节流刷新模糊层（≈9fps），所以底下的内容在玻璃里是"活的"；
        // 另外停止滚动 260ms 后再补一次全质量刷新，避免停下来时还留着低帧的画面。
        val blurRefresh = Runnable { bar.requestBackdropRefresh(immediate = true) }
        val scrollView = content as? android.widget.ScrollView
        scrollView?.setOnScrollChangeListener { _, _, _, _, _ ->
            bar.requestBackdropRefresh()
            scrollView.removeCallbacks(blurRefresh)
            scrollView.postDelayed(blurRefresh, 260L)
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
                /*
                 * 平级标签之间切换：先把当前页结束掉，再用 CLEAR_TOP + SINGLE_TOP 打开目标页，
                 * 让任务栈始终保持成「首页 + 一个标签页」两层。
                 *
                 * 之前用 REORDER_TO_FRONT 会把已经存在的页面提到前台，栈会变成
                 * 「首页 → 设置 → 排行」这种乱序，于是出现「点了首页却弹出别的页面」。
                 */
                activity.startActivity(
                    Intent(activity, activityFor(index))
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                activity.finish()
            }
        }
        // 底栏创建后每次都要重算一遍（布局完成前拿不到 item 位置）
        bar.post { bar.setSelectedTab(currentTab, animated = false) }

        // 点击粒子：盖在最上层，但不消费触摸事件
        // 动画总开关关掉时，粒子也不出现（用户按的就是这个开关）
        if (SettingsRepository.particleEffectEnabled(activity) &&
            SettingsRepository.animationsEnabled(activity)
        ) {
            val particles = ParticleOverlay(activity)
            // 节日当天，点击特效也换成节日造型（过年放烟花、圣诞扔雪球…）
            particles.holiday = holiday
            container.addView(
                particles,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            // 页面被切走时马上停掉粒子，别和窗口转场动画抢帧
            (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.addObserver(
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) particles.stop()
                }
            )
        }

        activity.setContentView(container)
        ViewCompat.requestApplyInsets(container)
        animateEntrance(content)
        return bar
    }

    /**
     * 必应每日壁纸作为软件背景。
     *
     * 可读性的三层保证：
     * 1. 壁纸之上先压一层半透明蒙版（浅色模式压白、深色模式压黑），浓度由设置里的滑杆控制；
     * 2. 页面根布局自带的渐变背景要清掉，否则会把壁纸整个盖住；
     * 3. 所有信息仍然装在**不透明的卡片**里，文字不会直接压在照片上。
     */
    private fun applyWallpaper(
        activity: AppCompatActivity,
        container: FrameLayout,
        content: View
    ) {
        if (!com.rjy.xyz.apps.xyzinfo.data.SettingsRepository.bingWallpaperEnabled(activity)) return
        val bitmap = com.rjy.xyz.apps.xyzinfo.data.BingWallpaperRepository.loadBitmap(activity)
            ?: return
        container.background = android.graphics.drawable.BitmapDrawable(activity.resources, bitmap)
            .apply { setGravity(android.view.Gravity.FILL) }
        content.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val night = (activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val alpha = (com.rjy.xyz.apps.xyzinfo.data.SettingsRepository.wallpaperScrim(activity) / 100f * 255f).toInt()
        val scrim = View(activity)
        scrim.setBackgroundColor(
            android.graphics.Color.argb(alpha, if (night) 0 else 255, if (night) 0 else 255, if (night) 0 else 255)
        )
        container.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /**
     * 页面入场动画：把页面里的一级区块**从左往右依次滑入**。
     *
     * 流畅优先的三条约束：
     * 1. 只做 alpha + translationX（不缩放、不加 alpha 到整窗，避免离屏层）；
     * 2. 只动前 14 个区块（首屏可见的那几个），不碰长列表；
     * 3. 关掉「丝滑动画」后整段跳过。
     */
    private fun animateEntrance(content: View) {
        if (!Anim.enabled(content.context)) return
        val host = (content as? ViewGroup)
            ?.takeIf { it.childCount > 0 }
            ?.getChildAt(0) as? ViewGroup ?: return
        if (host.childCount < 2) return
        val density = content.resources.displayMetrics.density
        val interpolator = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val count = minOf(host.childCount, 14)
        for (index in 0 until count) {
            val view = host.getChildAt(index)
            view.animate().cancel()
            // 幅度要看得出来：从屏幕左侧 42% 宽处滑进来 + 淡入
            view.alpha = 0f
            view.translationX = content.width.coerceAtLeast(600) * 0.42f
            view.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(index * 34L)
                .setDuration(330L)
                .setInterpolator(interpolator)
                .start()
        }
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
            // 按设备形态自适应排版（平板 / 展开态折叠屏会加宽左右留白、放大标题）
            AdaptiveLayout.apply(target.context, target)
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
