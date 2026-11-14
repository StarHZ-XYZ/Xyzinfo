package com.rjy.xyz.apps.xyzinfo.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.BingWallpaperRepository
import com.rjy.xyz.apps.xyzinfo.BuildConfig
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.ui.common.Season
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySettingsBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.TabPage
import com.rjy.xyz.apps.xyzinfo.ui.common.UpdateControls
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

/**
 * 设置页：外观开关（液态玻璃底栏 / 丝滑动画）、跑分模式、机型库更新与关于信息。
 */
class SettingsTabFragment : Fragment(), TabPage {

    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val hostContext get() = requireContext()
    private val isFinishing get() = !isAdded || _binding == null

    private fun recreate() = requireActivity().recreate()

    private fun runOnUiThread(action: () -> Unit) {
        activity?.runOnUiThread(action)
    }
    private var aboutTapCount = 0
    private var lastAboutTapAt = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ActivitySettingsBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAppearance()
        setupWallpaper()
        setupBenchmark()
        setupData()
        setupAbout()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun scrollToTop() {
        (_binding?.root as? android.widget.ScrollView)?.smoothScrollTo(0, 0)
    }

    private fun setupAppearance() {
        binding.switchAnimations.isChecked = SettingsRepository.animationsEnabled(hostContext)
        binding.switchAnimations.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setAnimationsEnabled(hostContext, checked)
            // 粒子层是运行时挂上去的，改完重建页面让开关立刻生效
            recreate()
        }

        binding.switchParticles.isChecked = SettingsRepository.particleEffectEnabled(hostContext)
        binding.switchParticles.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setParticleEffectEnabled(hostContext, checked)
            recreate()
        }

        binding.switchFollowSystemColor.isChecked = SettingsRepository.followSystemColor(hostContext)
        binding.switchFollowSystemColor.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setFollowSystemColor(hostContext, checked)
            Toast.makeText(
                hostContext,
                if (checked) "已开启莫奈取色，重进应用后完全生效" else "已恢复应用默认配色",
                Toast.LENGTH_SHORT
            ).show()
            recreate()
        }

        binding.switchDeepSeekTheme.isChecked = SettingsRepository.deepSeekTheme(hostContext)
        binding.switchDeepSeekTheme.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setDeepSeekTheme(hostContext, checked)
            Toast.makeText(
                hostContext,
                if (checked) "已开启大肥鱼主题：每页角落会有条大肥鱼" else "已关闭大肥鱼主题",
                Toast.LENGTH_SHORT
            ).show()
            // 装饰是建页面时挂上去的，重建一次立即生效
            recreate()
        }

        binding.switchSeason.isChecked = SettingsRepository.seasonEffectEnabled(hostContext)
        buildDarkModeChips()
        updateSeasonHint()
        buildSeasonChips()
        binding.switchSeasonGravity.isChecked = SettingsRepository.seasonGravity(hostContext)
        binding.tvSeasonGravityHint.alpha = if (binding.switchSeasonGravity.isChecked) 1f else 0.5f
        binding.switchSeasonGravity.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setSeasonGravity(hostContext, checked)
            binding.tvSeasonGravityHint.alpha = if (checked) 1f else 0.5f
        }
        binding.switchSeason.setOnCheckedChangeListener { _, checked ->
            // 总开关：一次关掉四季氛围与节日特效
            SettingsRepository.setSeasonEffectEnabled(hostContext, checked)
            binding.switchHoliday.isEnabled = checked
            binding.tvHolidayHint.alpha = if (checked && binding.switchHoliday.isChecked) 1f else 0.5f
            recreate()
        }

        binding.switchHoliday.isChecked = SettingsRepository.holidayEffectEnabled(hostContext)
        val fallingOn = SettingsRepository.seasonEffectEnabled(hostContext)
        binding.switchHoliday.isEnabled = fallingOn
        binding.tvHolidayHint.alpha = if (fallingOn && binding.switchHoliday.isChecked) 1f else 0.5f
        binding.switchHoliday.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setHolidayEffectEnabled(hostContext, checked)
            binding.tvHolidayHint.alpha =
                if (checked && SettingsRepository.seasonEffectEnabled(hostContext)) 1f else 0.5f
        }

        binding.switchHomeGrid.isChecked = SettingsRepository.homeGridStyle(hostContext)
        binding.switchHomeGrid.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setHomeGridStyle(hostContext, checked)
            recreate()
        }

        // 活体预览：就是首页用的那个底栏组件，点着能直接感受液体指示块
        /*
         * 预览里的选中态必须**跟着点击走**。
         *
         * 之前这里的回调是空的：点别的标签只让指示线滑过去（光标动了），
         * 但 selectedIndex 一直停在「设置」—— 于是图标 / 文字的选中色、
         * 选中项背后那团光晕全都不动，看着就像"光晕没跑过去"。
         */
        binding.glassBarPreview.bind(GlassScaffold.tabs(), GlassScaffold.TAB_SETTINGS) { index, _ ->
            binding.glassBarPreview.setSelectedTab(index, animated = true)
        }
        // 预览也要真的磨砂：拿设置页自己的内容当取样源
        binding.glassBarPreview.attachBackdrop(binding.root)
    }

    private fun setupWallpaper() {
        binding.switchWallpaper.isChecked = SettingsRepository.bingWallpaperEnabled(hostContext)
        binding.switchWallpaper.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setBingWallpaperEnabled(hostContext, checked)
            if (checked && !BingWallpaperRepository.isUpToDate(hostContext)) {
                binding.tvWallpaperInfo.text = "正在下载必应今日壁纸…"
                Thread({
                    val result = BingWallpaperRepository.download(hostContext.applicationContext)
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

        val scrim = SettingsRepository.wallpaperScrim(hostContext)
        binding.seekScrim.progress = scrim - 45
        binding.tvScrimValue.text = scrimLabel(scrim)
        binding.seekScrim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) binding.tvScrimValue.text = scrimLabel(value + 45)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                SettingsRepository.setWallpaperScrim(hostContext, (bar?.progress ?: 27) + 45)
                recreate()
            }
        })

        binding.btnWallpaperNext.setOnClickListener {
            Anim.pressFeedback(it)
            binding.tvWallpaperInfo.text = "正在换一张…"
            Thread({
                val result = BingWallpaperRepository.download(hostContext.applicationContext, (1..7).random())
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    binding.tvWallpaperInfo.text = result.message
                    SettingsRepository.setBingWallpaperEnabled(hostContext, true)
                    recreate()
                }
            }, "bing-wallpaper-next").start()
        }
    }

    private fun scrimLabel(value: Int): String =
        "蒙版浓度：$value%（越高文字越清楚，越低越能看到壁纸）"

    /** 深色模式：跟随系统 / 浅色 / 深色 三个胶囊，点一下立刻全局生效。 */
    private fun buildDarkModeChips() {
        val modes = listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色")
        val current = SettingsRepository.darkMode(hostContext)
        binding.layoutDarkModeChips.removeAllViews()
        modes.forEach { (mode, label) ->
            val chip = TextView(hostContext).apply {
                text = label
                textSize = 12.5f
                gravity = android.view.Gravity.CENTER
                setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                setTextColor(
                    if (mode == current) ThemeColors.accent(hostContext) else ContextCompat.getColor(hostContext, R.color.text_secondary)
                )
                background = ContextCompat.getDrawable(hostContext, R.drawable.bg_chip_filter)
                isSelected = mode == current
                isClickable = true
                setOnClickListener {
                    SettingsRepository.setDarkMode(hostContext, mode)
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        when (mode) {
                            1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                            2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    )
                }
            }
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8f) }
            binding.layoutDarkModeChips.addView(chip)
        }
    }

    private fun updateSeasonHint() {
        val manual = SettingsRepository.seasonMode(hostContext) != "auto"
        binding.tvSeasonHint.text = (if (manual) "已手动锁定：" else "自动模式（按当前月份）：") +
            Season.resolve(hostContext).label
    }

    /** 自动 / 春 / 夏 / 秋 / 冬 五个胶囊按钮，点一下就锁定（点「自动」恢复按月份）。 */
    private fun buildSeasonChips() {
        val modes = listOf(
            "auto" to "自动",
            "spring" to "春",
            "summer" to "夏",
            "autumn" to "秋",
            "winter" to "冬"
        )
        val current = SettingsRepository.seasonMode(hostContext)
        binding.layoutSeasonChips.removeAllViews()
        modes.forEach { (mode, label) ->
            val chip = TextView(hostContext).apply {
                text = label
                textSize = 12.5f
                gravity = android.view.Gravity.CENTER
                setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                setTextColor(
                    if (mode == current) ThemeColors.accent(hostContext) else ContextCompat.getColor(hostContext, R.color.text_secondary)
                )
                background = ContextCompat.getDrawable(hostContext, R.drawable.bg_chip_filter)
                isSelected = mode == current
                isClickable = true
                setOnClickListener {
                    SettingsRepository.setSeasonMode(hostContext, mode)
                    SettingsRepository.setSeasonEffectEnabled(hostContext, true)
                    recreate()
                }
            }
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8f) }
            binding.layoutSeasonChips.addView(chip)
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateWallpaperInfo() {
        val current = BingWallpaperRepository.current(hostContext)
        binding.tvWallpaperInfo.text = if (current == null) {
            "还没有下载过壁纸：打开上面的开关会立刻抓取必应当天的图"
        } else {
            "当前：${current.date}\n${current.copyright}"
        }
    }

    private fun setupBenchmark() {
        binding.switchDeepBenchmark.isChecked = SettingsRepository.deepBenchmarkEnabled(hostContext)
        binding.switchDeepBenchmark.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setDeepBenchmarkEnabled(hostContext, checked)
        }

        binding.btnClearBenchmark.setOnClickListener {
            Anim.pressFeedback(it)
            SettingsRepository.clearBenchmark(hostContext)
            Toast.makeText(hostContext, "已清除本机跑分记录，排行榜里不再显示实测行", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupData() {
        val host = requireActivity() as AppCompatActivity
        UpdateControls.refreshStatus(host, binding.tvDataStatus)
        UpdateControls.attachUpdate(host, binding.btnUpdateData, binding.tvDataStatus)
        UpdateControls.attachReset(host, binding.btnResetData, binding.tvDataStatus)
        Anim.pressFeedback(binding.btnUpdateData, binding.btnResetData, binding.btnClearBenchmark)
    }

    private fun setupAbout() {
        val version = "${BuildConfig.VERSION_NAME}（构建号 ${BuildConfig.BUILD_NUMBER}）"
        binding.tvAbout.setInfoRow(
            "应用：XYZ-Devinfo 设备检测\n" +
                "版本：$version\n" +
                "作者：星幻终（RJYZ）\n" +
                "排行榜数据：极客湾（Geekerwan）公开榜单\n" +
                "机型库：Google Play 认证设备清单 + 社区补充\n" +
                "开源协议：MIT"
        )
        binding.btnOpenDiagnostics.visibility =
            if (SettingsRepository.diagnosticsUnlocked(hostContext)) TextView.VISIBLE else TextView.GONE
        binding.tvAbout.setOnClickListener { revealDiagnosticsOnSecretTap() }
        binding.btnOpenDiagnostics.setOnClickListener {
            Anim.pressFeedback(it)
                hostContext.startActivity(
                android.content.Intent(
                        hostContext,
                    com.rjy.xyz.apps.xyzinfo.ui.about.DiagnosticsActivity::class.java
                )
            )
        }
        binding.btnOpenGithub.setOnClickListener {
            Anim.pressFeedback(it)
            runCatching {
                hostContext.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/StarHZ-XYZ/Xyzinfo")
                    )
                )
            }.onFailure { Toast.makeText(hostContext, "没有可用的浏览器", Toast.LENGTH_SHORT).show() }
        }
        binding.btnOpenChangelog.setOnClickListener {
            Anim.pressFeedback(it)
            hostContext.startActivity(
                android.content.Intent(hostContext, com.rjy.xyz.apps.xyzinfo.ui.about.ChangelogActivity::class.java)
            )
        }
        setupFishAssistant()
    }

    /** 隐藏特性：2.5 秒内连续点「关于」信息 7 次，解锁诊断与恢复工具。 */
    private fun revealDiagnosticsOnSecretTap() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAboutTapAt > 2_500L) aboutTapCount = 0
        lastAboutTapAt = now
        if (SettingsRepository.diagnosticsUnlocked(hostContext)) {
            binding.btnOpenDiagnostics.visibility = TextView.VISIBLE
            Toast.makeText(hostContext, "诊断中心已解锁，入口在更新日志下方", Toast.LENGTH_SHORT).show()
            return
        }
        aboutTapCount++
        when (aboutTapCount) {
            in 4..6 -> Toast.makeText(
                hostContext,
                "还差 ${7 - aboutTapCount} 次…",
                Toast.LENGTH_SHORT
            ).show()
            7 -> {
                SettingsRepository.unlockDiagnostics(hostContext)
                binding.btnOpenDiagnostics.visibility = TextView.VISIBLE
                Toast.makeText(hostContext, "已解锁隐藏诊断中心", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 大肥鱼助手：填 API Key（直连官方接口）或切到官方免费版网页。 */
    private fun setupFishAssistant() {
        binding.etDeepSeekKey.setText(SettingsRepository.deepSeekApiKey(hostContext))
        binding.btnSaveDeepSeekKey.setOnClickListener {
            Anim.pressFeedback(it)
            SettingsRepository.setDeepSeekApiKey(hostContext, binding.etDeepSeekKey.text?.toString().orEmpty())
            Toast.makeText(hostContext, "已保存 DeepSeek API Key", Toast.LENGTH_SHORT).show()
            updateAiModeUi()
        }
        binding.chipAiModeApi.setOnClickListener {
            SettingsRepository.setAiMode(hostContext, "api")
            updateAiModeUi()
        }
        binding.chipAiModeWeb.setOnClickListener {
            SettingsRepository.setAiMode(hostContext, "web")
            updateAiModeUi()
        }
        updateAiModeUi()
    }

    private fun updateAiModeUi() {
        val api = SettingsRepository.aiMode(hostContext) == "api"
        val keySet = SettingsRepository.deepSeekApiKey(hostContext).isNotBlank()
        listOf(binding.chipAiModeApi to api, binding.chipAiModeWeb to !api).forEach { (chip, selected) ->
            chip.isSelected = selected
            chip.setTextColor(
                ContextCompat.getColor(
                    hostContext,
                    if (selected) R.color.accent else R.color.text_secondary
                )
            )
        }
        binding.tvAiModeHint.text = when {
            api && keySet -> "当前：API Key 直连（已填写 Key，点「大肥鱼」标签即可让 DeepSeek 解读验机报告）"
            api && !keySet -> "当前：API Key 直连，但还没填 Key —— 请在上面的输入框里填入并保存"
            else -> "当前：官方免费版（点「大肥鱼」标签里的按钮会打开 chat.deepseek.com，需要登录）"
        }
    }
}

/** 兼容旧任务栈 / 外部入口：不再创建独立设置窗口，直接切到单 Activity 壳层的设置页。 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            android.content.Intent(this, com.rjy.xyz.apps.xyzinfo.MainActivity::class.java)
                .putExtra(
                    com.rjy.xyz.apps.xyzinfo.MainActivity.EXTRA_INITIAL_TAB,
                    GlassScaffold.TAB_SETTINGS
                )
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
