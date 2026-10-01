package com.rjy.xyz.apps.xyzinfo.ui.splash

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.PathInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.BuildConfig
import com.rjy.xyz.apps.xyzinfo.MainActivity
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySplashBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding

/**
 * 启动动画页。
 *
 * 1.8 秒：品牌标从 0.86 倍带一点回弹放大到 1 倍、标题与副标题依次上浮淡入、
 * 进度条 0 → 100 平滑推进；结束后淡出切到首页（不留白屏）。
 * 关掉「丝滑动画」时直接跳到终态，只等必要的 0.9 秒。
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * 热启动（进程还在、只是从后台回来）：**不播动画、不预热、也不显示**，
         * 立刻把 MainActivity 提到前面然后结束自己。
         *
         * 为什么会出现"没被杀却重载"：开屏页自己 finish 掉了，所以它不在任务栈根上；
         * 从桌面再次点击图标时，系统会在已有任务栈**再启一个开屏页**，
         * 于是动画从头播一遍、MainActivity 也被重新创建 —— 看起来就像重载。
         *
         * 这里把窗口背景设成透明并立即交棒：用户看到的是"原来那一页直接回来了"，
         * 进程里原有的 Activity 栈（包括滚动位置、页面状态）完全保留。
         */
        if (com.rjy.xyz.apps.xyzinfo.XyzInfoApp.warmStarted) {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            /*
             * 任务栈里还有我们的页面 → 直接结束这一页，让下面那一页露出来（原样恢复，不重载）；
             * 进程虽然活着但任务栈已经没了（比如用户按返回退出了、进程被温度浮窗服务续着）→
             * 这时才需要正常开首页。
             */
            if (isTaskRoot) goHome(animated = false) else finish()
            return
        }
        com.rjy.xyz.apps.xyzinfo.XyzInfoApp.markStarted()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        binding.tvSplashSubtitle.text =
            "作者：星幻终（RJYZ）　v${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_NUMBER}"

        val animated = com.rjy.xyz.apps.xyzinfo.data.SettingsRepository.animationsEnabled(this)
        val duration = if (animated) 1800L else 700L
        playEntrance(animated)
        /*
         * 开屏期间把首页要用的数据预加载完，动画放完后首页直接是最终状态，
         * 不会再先闪一下「未知设备」。规则是「至少放满 duration，且数据必须加载完」：
         * 数据加载快 → 满 duration 后进首页；加载慢 → 等它加载完再进（并显示在加载什么）。
         */
        val startedAt = android.os.SystemClock.elapsedRealtime()
        Thread({
            com.rjy.xyz.apps.xyzinfo.data.AppPreloader.warmUp(this) { step ->
                runOnUiThread {
                    if (!isFinishing) binding.tvSplashFooter.text = step
                }
            }
            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            val remaining = (duration - elapsed).coerceAtLeast(0L)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                binding.root.postDelayed({ goHome(animated) }, remaining)
            }
        }, "xyzinfo-preload").start()
    }

    private fun playEntrance(animated: Boolean) {
        val logo = binding.ivLogo
        val title = binding.tvSplashTitle
        val subtitle = binding.tvSplashSubtitle
        val progress = binding.progressSplash

        if (!animated) {
            progress.progress = 100
            binding.tvSplashFooter.text = "准备就绪"
            return
        }

        val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        logo.scaleX = 0.86f
        logo.scaleY = 0.86f
        logo.alpha = 0f
        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(620L)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
            .start()

        listOf(title to 120L, subtitle to 260L).forEach { (view, delay) ->
            view.alpha = 0f
            view.translationY = 28f * resources.displayMetrics.density
            view.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(delay)
                .setDuration(420L)
                .setInterpolator(decelerate)
                .start()
        }

        ValueAnimator.ofInt(0, 100).apply {
            duration = 1600L
            interpolator = decelerate
            addUpdateListener { progress.progress = it.animatedValue as Int }
            start()
        }
        binding.tvSplashFooter.alpha = 0f
        binding.tvSplashFooter.animate().alpha(1f).setStartDelay(500L).setDuration(400L).start()
        binding.tvSplashFooter.text = "正在初始化…"
    }

    private fun goHome(animated: Boolean) {
        startActivity(Intent(this, MainActivity::class.java))
        if (animated) {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        finish()
    }
}
