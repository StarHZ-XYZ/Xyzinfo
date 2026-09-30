package com.rjy.xyz.apps.xyzinfo.ui.settings

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.BingWallpaperRepository
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySettingsBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.UpdateControls
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow

/**
 * 设置页：外观开关（液态玻璃底栏 / 丝滑动画）、跑分模式、机型库更新与关于信息。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_SETTINGS)

        setupAppearance()
        setupWallpaper()
        setupBenchmark()
        setupData()
        setupAbout()
    }

    private fun setupAppearance() {
        binding.switchAnimations.isChecked = SettingsRepository.animationsEnabled(this)
        binding.switchAnimations.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setAnimationsEnabled(this, checked)
            // 粒子层是运行时挂上去的，改完重建页面让开关立刻生效
            recreate()
        }

        binding.switchParticles.isChecked = SettingsRepository.particleEffectEnabled(this)
        binding.switchParticles.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setParticleEffectEnabled(this, checked)
            recreate()
        }

        binding.switchFollowSystemColor.isChecked = SettingsRepository.followSystemColor(this)
        binding.switchFollowSystemColor.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setFollowSystemColor(this, checked)
            Toast.makeText(
                this,
                if (checked) "已开启莫奈取色，重进应用后完全生效" else "已恢复应用默认配色",
                Toast.LENGTH_SHORT
            ).show()
            recreate()
        }

        // 活体预览：就是首页用的那个底栏组件，点着能直接感受液体指示块
        binding.glassBarPreview.bind(GlassScaffold.tabs(), GlassScaffold.TAB_SETTINGS) { _, _ -> }
        // 预览也要真的磨砂：拿设置页自己的内容当取样源
        binding.glassBarPreview.attachBackdrop(binding.root)
    }

    private fun setupWallpaper() {
        binding.switchWallpaper.isChecked = SettingsRepository.bingWallpaperEnabled(this)
        binding.switchWallpaper.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setBingWallpaperEnabled(this, checked)
            if (checked && !BingWallpaperRepository.isUpToDate(this)) {
                binding.tvWallpaperInfo.text = "正在下载必应今日壁纸…"
                Thread({
                    val result = BingWallpaperRepository.download(this)
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        binding.tvWallpaperInfo.text = result.message
                        recreate()
                    }
                }, "bing-wallpaper").start()
            } else {
                recreate()
            }
        }
        updateWallpaperInfo()

        val scrim = SettingsRepository.wallpaperScrim(this)
        binding.seekScrim.progress = scrim - 45
        binding.tvScrimValue.text = scrimLabel(scrim)
        binding.seekScrim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) binding.tvScrimValue.text = scrimLabel(value + 45)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                SettingsRepository.setWallpaperScrim(this@SettingsActivity, (bar?.progress ?: 27) + 45)
                recreate()
            }
        })

        binding.btnWallpaperNext.setOnClickListener {
            Anim.pressFeedback(it)
            binding.tvWallpaperInfo.text = "正在换一张…"
            Thread({
                val result = BingWallpaperRepository.download(this, (1..7).random())
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    binding.tvWallpaperInfo.text = result.message
                    SettingsRepository.setBingWallpaperEnabled(this, true)
                    recreate()
                }
            }, "bing-wallpaper-next").start()
        }
    }

    private fun scrimLabel(value: Int): String =
        "蒙版浓度：$value%（越高文字越清楚，越低越能看到壁纸）"

    private fun updateWallpaperInfo() {
        val current = BingWallpaperRepository.current(this)
        binding.tvWallpaperInfo.text = if (current == null) {
            "还没有下载过壁纸：打开上面的开关会立刻抓取必应当天的图"
        } else {
            "当前：${current.date}\n${current.copyright}"
        }
    }

    private fun setupBenchmark() {
        binding.switchDeepBenchmark.isChecked = SettingsRepository.deepBenchmarkEnabled(this)
        binding.switchDeepBenchmark.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setDeepBenchmarkEnabled(this, checked)
        }

        binding.btnClearBenchmark.setOnClickListener {
            Anim.pressFeedback(it)
            SettingsRepository.clearBenchmark(this)
            Toast.makeText(this, "已清除本机跑分记录，排行榜里不再显示实测行", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupData() {
        UpdateControls.refreshStatus(this, binding.tvDataStatus)
        UpdateControls.attachUpdate(this, binding.btnUpdateData, binding.tvDataStatus)
        UpdateControls.attachReset(this, binding.btnResetData, binding.tvDataStatus)
        Anim.pressFeedback(binding.btnUpdateData, binding.btnResetData, binding.btnClearBenchmark)
    }

    private fun setupAbout() {
        binding.tvAbout.setInfoRow(
            "应用：XYZ-Devinfo 设备检测\n" +
                "版本：0.7\n" +
                "作者：星幻终（RJYZ）\n" +
                "排行榜数据：极客湾（Geekerwan）公开榜单\n" +
                "机型库：Google Play 认证设备清单 + 社区补充\n" +
                "开源协议：MIT"
        )
    }
}
