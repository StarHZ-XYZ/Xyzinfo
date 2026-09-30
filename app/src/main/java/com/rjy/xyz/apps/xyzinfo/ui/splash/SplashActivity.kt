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
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        binding.tvSplashSubtitle.text =
            "作者：星幻终（RJYZ）　v${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_NUMBER}"

        val animated = com.rjy.xyz.apps.xyzinfo.data.SettingsRepository.animationsEnabled(this)
        val duration = if (animated) 1800L else 700L
        playEntrance(animated)
        binding.root.postDelayed({ goHome(animated) }, duration)
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
